import Foundation
import PNG

internal enum PNGDecoder {
    static func decode(bytes: [UInt8]) throws -> DecodedImage {
        var blob = PNGBlob(data: bytes)
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
