import Foundation
import PNG

internal enum PNGDecoder {
    // Sniff IHDR dimensions from raw bytes before invoking swift-png.
    // PNG layout: 8-byte signature + 4-byte chunk length + 4-byte "IHDR" +
    //             4-byte width (BE) + 4-byte height (BE) = offsets 16-23.
    private static func sniffIHDR(bytes: [UInt8]) throws {
        guard bytes.count >= 24 else { return }  // too short to sniff — let swift-png reject
        let w = Int(bytes[16]) << 24 | Int(bytes[17]) << 16 | Int(bytes[18]) << 8 | Int(bytes[19])
        let h = Int(bytes[20]) << 24 | Int(bytes[21]) << 16 | Int(bytes[22]) << 8 | Int(bytes[23])
        try Limits.checkDimensions(width: w, height: h, format: .png)
    }

    // Workaround for swift-png 4.3.0 (and 4.5.1) bug: deflate streams that cross
    // IDAT chunk boundaries at certain offsets trigger a false-positive
    // "extraneousImageData" error. Real-world PNG decoders concatenate all
    // IDAT bodies before inflating; swift-png does not. PIL emits multi-IDAT
    // PNGs by default (64 KB split), so we re-chunk the input bytes here:
    // merge all IDAT chunks into a single IDAT, leave everything else intact.
    private static func mergePngIdats(_ bytes: [UInt8]) -> [UInt8] {
        // PNG signature is 8 bytes; chunks follow.
        guard bytes.count >= 8 else { return bytes }
        // Quick check: count IDATs and validate basic walkability.
        var idatCount = 0
        var totalIdatLen = 0
        var i = 8
        while i + 8 <= bytes.count {
            let len = Int(UInt32(bytes[i]) << 24 | UInt32(bytes[i+1]) << 16
                        | UInt32(bytes[i+2]) << 8 | UInt32(bytes[i+3]))
            if len < 0 || i + 8 + len + 4 > bytes.count { break }
            let type = String(decoding: bytes[(i+4)..<(i+8)], as: UTF8.self)
            if type == "IDAT" {
                idatCount += 1
                totalIdatLen += len
            }
            if type == "IEND" { break }
            i += 8 + len + 4
        }
        guard idatCount > 1 else { return bytes }
        // PNG chunk length is u31 max; bail if merged body would exceed it.
        guard totalIdatLen <= 0x7FFFFFFF else { return bytes }

        // Re-walk and emit.
        var out: [UInt8] = Array(bytes.prefix(8))  // signature
        var idatBody: [UInt8] = []
        idatBody.reserveCapacity(totalIdatLen)
        var idatEmitted = false
        i = 8
        while i + 8 <= bytes.count {
            let len = Int(UInt32(bytes[i]) << 24 | UInt32(bytes[i+1]) << 16
                        | UInt32(bytes[i+2]) << 8 | UInt32(bytes[i+3]))
            if len < 0 || i + 8 + len + 4 > bytes.count {
                // Trailing garbage — preserve as-is and bail.
                out.append(contentsOf: bytes[i...])
                return out
            }
            let type = String(decoding: bytes[(i+4)..<(i+8)], as: UTF8.self)
            if type == "IDAT" {
                idatBody.append(contentsOf: bytes[(i+8)..<(i+8+len)])
                i += 8 + len + 4
                continue
            }
            if type == "IEND" {
                // Emit merged IDAT just before IEND if we haven't.
                if !idatEmitted && !idatBody.isEmpty {
                    emitIdat(into: &out, body: idatBody)
                    idatEmitted = true
                }
                out.append(contentsOf: bytes[i..<(i + 8 + len + 4)])
                return out
            }
            // Non-IDAT chunk: copy whole chunk (header + body + CRC) verbatim.
            out.append(contentsOf: bytes[i..<(i + 8 + len + 4)])
            i += 8 + len + 4
        }
        // Defensive fallthrough — no IEND found.
        if !idatEmitted && !idatBody.isEmpty {
            emitIdat(into: &out, body: idatBody)
        }
        return out
    }

    private static func emitIdat(into out: inout [UInt8], body: [UInt8]) {
        let len = UInt32(body.count)
        out.append(UInt8((len >> 24) & 0xFF))
        out.append(UInt8((len >> 16) & 0xFF))
        out.append(UInt8((len >> 8) & 0xFF))
        out.append(UInt8(len & 0xFF))
        out.append(contentsOf: [0x49, 0x44, 0x41, 0x54])  // "IDAT"
        out.append(contentsOf: body)
        // CRC32 over type + body.
        var crcInput: [UInt8] = [0x49, 0x44, 0x41, 0x54]
        crcInput.append(contentsOf: body)
        let crc = pngCrc32(crcInput)
        out.append(UInt8((crc >> 24) & 0xFF))
        out.append(UInt8((crc >> 16) & 0xFF))
        out.append(UInt8((crc >> 8) & 0xFF))
        out.append(UInt8(crc & 0xFF))
    }

    // PNG-standard CRC32 (reflected, polynomial 0xEDB88320).
    private static func pngCrc32(_ data: [UInt8]) -> UInt32 {
        var crc: UInt32 = 0xFFFFFFFF
        for b in data {
            crc ^= UInt32(b)
            for _ in 0..<8 {
                crc = (crc >> 1) ^ ((crc & 1) * 0xEDB88320)
            }
        }
        return crc ^ 0xFFFFFFFF
    }

    static func decode(bytes: [UInt8]) throws -> DecodedImage {
        try sniffIHDR(bytes: bytes)
        let mergedBytes = mergePngIdats(bytes)
        var blob = PNGBlob(data: mergedBytes)
        let image: PNG.Image
        do {
            image = try .decompress(stream: &blob)
        } catch {
            throw DecodeError.corruptInput(format: .png, detail: "PNG decompress failed: \(error)")
        }
        let width = image.size.x
        let height = image.size.y

        // Detect whether source has alpha from the layout format.
        // swift-png 4.x Format enum cases all have associated values.
        // Built-in color targets: PNG.RGBA<T> and PNG.VA<T> only (no PNG.RGB).
        let layout = image.layout.format
        let hasAlpha: Bool
        let isV16: Bool

        switch layout {
        case .v1, .v2, .v4, .v8:
            hasAlpha = false
            isV16 = false
        case .v16:
            hasAlpha = false
            isV16 = true
        case .rgb8, .rgb16, .bgr8:
            hasAlpha = false
            isV16 = false
        case .va8, .va16:
            hasAlpha = true
            isV16 = false
        case .rgba8, .rgba16, .bgra8:
            hasAlpha = true
            isV16 = false
        case .indexed1(let palette, _),
             .indexed2(let palette, _),
             .indexed4(let palette, _),
             .indexed8(let palette, _):
            // palette is [(r,g,b,a)]; hasAlpha iff any entry has a != 255
            hasAlpha = palette.contains { $0.a != 0xFF }
            isV16 = false
        @unknown default:
            hasAlpha = true
            isV16 = false
        }

        if hasAlpha {
            let rgba: [PNG.RGBA<UInt8>] = image.unpack(as: PNG.RGBA<UInt8>.self)
            var data = [UInt8](repeating: 0, count: rgba.count * 4)
            for i in 0..<rgba.count {
                data[i*4]   = rgba[i].r
                data[i*4+1] = rgba[i].g
                data[i*4+2] = rgba[i].b
                data[i*4+3] = rgba[i].a
            }
            return DecodedImage(width: width, height: height, data: data, channels: .rgba, format: .png)
        } else if isV16 {
            // Single-channel 16-bit grayscale: PIL uses min(v, 255) clip, not >>8.
            let v16: [UInt16] = image.unpack(as: UInt16.self)
            var data = [UInt8](repeating: 0, count: v16.count * 3)
            for i in 0..<v16.count {
                let clipped = UInt8(min(UInt(v16[i]), 255))
                data[i*3]   = clipped
                data[i*3+1] = clipped
                data[i*3+2] = clipped
            }
            return DecodedImage(width: width, height: height, data: data, channels: .rgb, format: .png)
        } else {
            // Non-alpha path. Unpack as RGBA<UInt8> (swift-png handles >>8 for 16-bit types).
            // swift-png sets alpha=255 for opaque formats; we discard it.
            let rgba: [PNG.RGBA<UInt8>] = image.unpack(as: PNG.RGBA<UInt8>.self)
            var data = [UInt8](repeating: 0, count: rgba.count * 3)
            for i in 0..<rgba.count {
                data[i*3]   = rgba[i].r
                data[i*3+1] = rgba[i].g
                data[i*3+2] = rgba[i].b
            }
            return DecodedImage(width: width, height: height, data: data, channels: .rgb, format: .png)
        }
    }
}

internal struct PNGBlob: PNG.BytestreamSource {
    let data: [UInt8]
    var position: Int

    init(data: [UInt8]) {
        self.data = data
        self.position = 0
    }

    mutating func read(count: Int) -> [UInt8]? {
        guard position + count <= data.count else { return nil }
        defer { position += count }
        return Array(data[position..<(position + count)])
    }
}
