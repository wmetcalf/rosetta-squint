import Foundation

internal enum BMPDecoder {
    static let BI_RGB: UInt32 = 0
    static let BI_RLE8: UInt32 = 1
    static let BI_RLE4: UInt32 = 2
    static let BI_BITFIELDS: UInt32 = 3
    static let BI_JPEG: UInt32 = 4
    static let BI_PNG: UInt32 = 5
    static let BI_ALPHABITFIELDS: UInt32 = 6

    struct Header {
        let width: Int
        let height: Int
        let topDown: Bool
        let bitCount: Int
        let compression: UInt32
        let clrUsed: Int
        let redMask: UInt32
        let greenMask: UInt32
        let blueMask: UInt32
        let alphaMask: UInt32
        let pixelDataOffset: Int
        let dibHeaderSize: Int
    }

    static func decode(bytes: [UInt8]) throws -> DecodedImage {
        let hdr = try parseHeader(bytes: bytes)
        switch hdr.compression {
        case BI_RGB:
            switch hdr.bitCount {
            case 24: return try decodeRgb24(bytes: bytes, hdr: hdr)
            case 32: return try decodeRgb32(bytes: bytes, hdr: hdr)
            case 8:  return try decodePal8(bytes: bytes, hdr: hdr)
            case 4:  return try decodePal4(bytes: bytes, hdr: hdr)
            case 1:  return try decodePal1(bytes: bytes, hdr: hdr)
            case 16:
                throw DecodeError.corruptInput(format: .bmp, detail: "BI_RGB 16-bit not supported")
            default:
                throw DecodeError.corruptInput(format: .bmp, detail: "biBitCount \(hdr.bitCount) for BI_RGB")
            }
        case BI_BITFIELDS, BI_ALPHABITFIELDS:
            switch hdr.bitCount {
            case 16: return try decodeBitfields(bytes: bytes, hdr: hdr, bitsPerPixel: 16)
            case 32: return try decodeBitfields(bytes: bytes, hdr: hdr, bitsPerPixel: 32)
            default:
                throw DecodeError.corruptInput(format: .bmp, detail: "BI_BITFIELDS with biBitCount \(hdr.bitCount)")
            }
        case BI_RLE8:
            return try decodeRle(bytes: bytes, hdr: hdr, bitsPerPixel: 8)
        case BI_RLE4:
            return try decodeRle(bytes: bytes, hdr: hdr, bitsPerPixel: 4)
        default:
            throw DecodeError.corruptInput(format: .bmp, detail: "biCompression \(hdr.compression) unreachable")
        }
    }

    // MARK: - Low-level readers

    private static func readU32LE(_ bytes: [UInt8], _ i: Int) throws -> UInt32 {
        guard i + 4 <= bytes.count else {
            throw DecodeError.truncated(format: .bmp, detail: "u32 read at offset \(i)")
        }
        return UInt32(bytes[i])
             | (UInt32(bytes[i+1]) << 8)
             | (UInt32(bytes[i+2]) << 16)
             | (UInt32(bytes[i+3]) << 24)
    }

    private static func readU16LE(_ bytes: [UInt8], _ i: Int) throws -> UInt16 {
        guard i + 2 <= bytes.count else {
            throw DecodeError.truncated(format: .bmp, detail: "u16 read at offset \(i)")
        }
        return UInt16(bytes[i]) | (UInt16(bytes[i+1]) << 8)
    }

    private static func readI32LE(_ bytes: [UInt8], _ i: Int) throws -> Int32 {
        return Int32(bitPattern: try readU32LE(bytes, i))
    }

    private static func readI16LE(_ bytes: [UInt8], _ i: Int) throws -> Int16 {
        return Int16(bitPattern: try readU16LE(bytes, i))
    }

    // MARK: - Header parsing

    static func parseHeader(bytes: [UInt8]) throws -> Header {
        guard bytes.count >= 14 else {
            throw DecodeError.truncated(format: .bmp, detail: "file header truncated")
        }
        guard bytes[0] == 0x42 && bytes[1] == 0x4D else {
            throw DecodeError.corruptInput(format: .bmp, detail: "Not a BMP file (no 'BM' signature)")
        }

        let bfOffBits = Int(try readU32LE(bytes, 10))

        guard bytes.count >= 18 else {
            throw DecodeError.truncated(format: .bmp, detail: "DIB header size not readable")
        }
        let biSize = Int(try readU32LE(bytes, 14))

        if biSize == 12 {
            throw DecodeError.unsupportedFeature(format: .bmp, feature: "OS/2 BMP header (size 12)")
        }
        // Accept BITMAPINFOHEADER(40), BITMAPV2(52), BITMAPV3(56), BITMAPV4(108), BITMAPV5(124)
        guard biSize == 40 || biSize == 52 || biSize == 56 || biSize == 108 || biSize == 124 else {
            throw DecodeError.corruptInput(format: .bmp, detail: "DIB header size \(biSize) not supported")
        }
        guard bytes.count >= 14 + biSize else {
            throw DecodeError.truncated(format: .bmp, detail: "DIB header truncated")
        }

        let biWidth  = Int(try readI32LE(bytes, 18))
        let biHeight = Int(try readI32LE(bytes, 22))
        let biPlanes   = try readI16LE(bytes, 26)
        let biBitCount = Int(try readU16LE(bytes, 28))
        let biCompression = try readU32LE(bytes, 30)
        let biClrUsed  = Int(try readU32LE(bytes, 46))

        guard biWidth > 0 else {
            throw DecodeError.corruptInput(format: .bmp, detail: "biWidth must be positive")
        }
        guard biHeight != 0 else {
            throw DecodeError.corruptInput(format: .bmp, detail: "biHeight must be non-zero")
        }
        guard biPlanes == 1 else {
            throw DecodeError.corruptInput(format: .bmp, detail: "biPlanes must be 1")
        }
        guard biBitCount == 1 || biBitCount == 4 || biBitCount == 8
           || biBitCount == 16 || biBitCount == 24 || biBitCount == 32 else {
            throw DecodeError.corruptInput(format: .bmp, detail: "biBitCount \(biBitCount) not supported")
        }
        guard biCompression <= 6 else {
            throw DecodeError.corruptInput(format: .bmp, detail: "biCompression \(biCompression) not supported")
        }
        if biCompression == BI_JPEG {
            throw DecodeError.unsupportedFeature(format: .bmp, feature: "embedded JPEG")
        }
        if biCompression == BI_PNG {
            throw DecodeError.unsupportedFeature(format: .bmp, feature: "embedded PNG")
        }

        // Masks if applicable
        var redMask: UInt32 = 0
        var greenMask: UInt32 = 0
        var blueMask: UInt32 = 0
        var alphaMask: UInt32 = 0

        let hasMasks = (biCompression == BI_BITFIELDS || biCompression == BI_ALPHABITFIELDS || biSize >= 52)
        if hasMasks {
            guard bytes.count >= 14 + 40 + 12 else {
                throw DecodeError.truncated(format: .bmp, detail: "BI_BITFIELDS masks truncated")
            }
            redMask   = try readU32LE(bytes, 54)
            greenMask = try readU32LE(bytes, 58)
            blueMask  = try readU32LE(bytes, 62)
            if biCompression == BI_ALPHABITFIELDS || biSize >= 56 {
                guard bytes.count >= 14 + 40 + 16 else {
                    throw DecodeError.truncated(format: .bmp, detail: "alpha mask truncated")
                }
                alphaMask = try readU32LE(bytes, 66)
            }
            if biCompression == BI_BITFIELDS {
                guard redMask != 0 && greenMask != 0 && blueMask != 0 else {
                    throw DecodeError.corruptInput(format: .bmp, detail: "BI_BITFIELDS mask is zero")
                }
            }
        }

        let topDown  = biHeight < 0
        let absHeight = abs(biHeight)

        try Limits.checkDimensions(width: biWidth, height: absHeight, format: .bmp)

        return Header(
            width: biWidth,
            height: absHeight,
            topDown: topDown,
            bitCount: biBitCount,
            compression: biCompression,
            clrUsed: biClrUsed,
            redMask: redMask,
            greenMask: greenMask,
            blueMask: blueMask,
            alphaMask: alphaMask,
            pixelDataOffset: bfOffBits,
            dibHeaderSize: biSize
        )
    }

    // MARK: - Decode RGB 24-bit

    private static func decodeRgb24(bytes: [UInt8], hdr: Header) throws -> DecodedImage {
        let stride = ((hdr.width * 3 + 3) / 4) * 4
        guard bytes.count - hdr.pixelDataOffset >= stride * hdr.height else {
            throw DecodeError.truncated(format: .bmp, detail: "pixel data truncated (24-bit RGB)")
        }
        var pixels = [UInt8](repeating: 0, count: hdr.width * hdr.height * 3)
        for srcRow in 0..<hdr.height {
            let dstRow = hdr.topDown ? srcRow : (hdr.height - 1 - srcRow)
            for x in 0..<hdr.width {
                let srcIdx = hdr.pixelDataOffset + srcRow * stride + x * 3
                let dstIdx = (dstRow * hdr.width + x) * 3
                pixels[dstIdx]     = bytes[srcIdx + 2] // R (from BGR+2)
                pixels[dstIdx + 1] = bytes[srcIdx + 1] // G (unchanged)
                pixels[dstIdx + 2] = bytes[srcIdx]     // B (from BGR+0)
            }
        }
        return DecodedImage(width: hdr.width, height: hdr.height, data: pixels, channels: .rgb, format: .bmp)
    }

    // MARK: - Decode RGB 32-bit

    private static func decodeRgb32(bytes: [UInt8], hdr: Header) throws -> DecodedImage {
        let stride = hdr.width * 4
        guard bytes.count - hdr.pixelDataOffset >= stride * hdr.height else {
            throw DecodeError.truncated(format: .bmp, detail: "pixel data truncated (32-bit RGB)")
        }
        // Always output RGB to match Pillow 11 behavior (goldens show channels=3 for all BI_RGB 32-bit).
        var pixels = [UInt8](repeating: 0, count: hdr.width * hdr.height * 3)
        for srcRow in 0..<hdr.height {
            let dstRow = hdr.topDown ? srcRow : (hdr.height - 1 - srcRow)
            for x in 0..<hdr.width {
                let srcIdx = hdr.pixelDataOffset + srcRow * stride + x * 4
                let dstIdx = (dstRow * hdr.width + x) * 3
                pixels[dstIdx]     = bytes[srcIdx + 2] // R (from BGRA+2)
                pixels[dstIdx + 1] = bytes[srcIdx + 1] // G (unchanged)
                pixels[dstIdx + 2] = bytes[srcIdx]     // B (from BGRA+0)
                // alpha byte at srcIdx+3 discarded
            }
        }
        return DecodedImage(width: hdr.width, height: hdr.height, data: pixels, channels: .rgb, format: .bmp)
    }

    // MARK: - Color table helper

    /// Clamp biClrUsed to the maximum entries the given bit-depth can index.
    /// If clrUsed <= 0, returns the bit-depth maximum (existing default).
    /// If clrUsed > bitDepthMax, clamps to bitDepthMax (PIL-lenient parsing).
    /// Defends against attacker-controlled values that would cause excessive
    /// palette allocation.
    internal static func clampEntryCount(_ clrUsed: Int, bitDepth: Int) -> Int {
        let bitDepthMax = 1 << bitDepth
        if clrUsed <= 0 { return bitDepthMax }
        return min(clrUsed, bitDepthMax)
    }

    private static func readColorTable(bytes: [UInt8], hdr: Header, entryCount: Int) throws -> [(UInt8, UInt8, UInt8)] {
        let colorTableOffset = 14 + hdr.dibHeaderSize
        let colorTableEnd = colorTableOffset + entryCount * 4
        guard bytes.count >= colorTableEnd else {
            throw DecodeError.truncated(format: .bmp, detail: "color table truncated")
        }
        var palette = [(UInt8, UInt8, UInt8)](repeating: (0, 0, 0), count: entryCount)
        for i in 0..<entryCount {
            let off = colorTableOffset + i * 4
            let r = bytes[off + 2]
            let g = bytes[off + 1]
            let b = bytes[off]
            palette[i] = (r, g, b)
        }
        return palette
    }

    // MARK: - Decode 8-bit paletted

    private static func decodePal8(bytes: [UInt8], hdr: Header) throws -> DecodedImage {
        let entryCount = clampEntryCount(hdr.clrUsed, bitDepth: 8)
        let colorTableOffset = 14 + hdr.dibHeaderSize
        let colorTableEnd = colorTableOffset + entryCount * 4
        guard bytes.count >= colorTableEnd else {
            throw DecodeError.truncated(format: .bmp, detail: "color table truncated (8-bit paletted)")
        }
        var palette = [(UInt8, UInt8, UInt8)](repeating: (0, 0, 0), count: entryCount)
        for i in 0..<entryCount {
            let off = colorTableOffset + i * 4
            palette[i] = (bytes[off + 2], bytes[off + 1], bytes[off])
        }

        let stride = ((hdr.width + 3) / 4) * 4
        guard bytes.count - hdr.pixelDataOffset >= stride * hdr.height else {
            throw DecodeError.truncated(format: .bmp, detail: "pixel data truncated (8-bit paletted)")
        }
        var pixels = [UInt8](repeating: 0, count: hdr.width * hdr.height * 3)
        for srcRow in 0..<hdr.height {
            let dstRow = hdr.topDown ? srcRow : (hdr.height - 1 - srcRow)
            for x in 0..<hdr.width {
                let srcIdx = hdr.pixelDataOffset + srcRow * stride + x
                var palIdx = Int(bytes[srcIdx])
                if palIdx >= entryCount { palIdx = entryCount - 1 }
                let dstIdx = (dstRow * hdr.width + x) * 3
                pixels[dstIdx]     = palette[palIdx].0
                pixels[dstIdx + 1] = palette[palIdx].1
                pixels[dstIdx + 2] = palette[palIdx].2
            }
        }
        return DecodedImage(width: hdr.width, height: hdr.height, data: pixels, channels: .rgb, format: .bmp)
    }

    // MARK: - Decode 4-bit paletted

    private static func decodePal4(bytes: [UInt8], hdr: Header) throws -> DecodedImage {
        let entryCount = clampEntryCount(hdr.clrUsed, bitDepth: 4)
        let palette = try readColorTable(bytes: bytes, hdr: hdr, entryCount: entryCount)
        let stride = ((hdr.width * 4 + 31) / 32) * 4
        guard bytes.count - hdr.pixelDataOffset >= stride * hdr.height else {
            throw DecodeError.truncated(format: .bmp, detail: "pixel data truncated (4-bit paletted)")
        }
        var pixels = [UInt8](repeating: 0, count: hdr.width * hdr.height * 3)
        for srcRow in 0..<hdr.height {
            let dstRow = hdr.topDown ? srcRow : (hdr.height - 1 - srcRow)
            for x in 0..<hdr.width {
                let byteOff = hdr.pixelDataOffset + srcRow * stride + (x / 2)
                let b = Int(bytes[byteOff])
                var idx = (x % 2 == 0) ? (b >> 4) : (b & 0xF)
                if idx >= entryCount { idx = entryCount - 1 }
                let dstIdx = (dstRow * hdr.width + x) * 3
                pixels[dstIdx]     = palette[idx].0
                pixels[dstIdx + 1] = palette[idx].1
                pixels[dstIdx + 2] = palette[idx].2
            }
        }
        return DecodedImage(width: hdr.width, height: hdr.height, data: pixels, channels: .rgb, format: .bmp)
    }

    // MARK: - Decode 1-bit paletted

    private static func decodePal1(bytes: [UInt8], hdr: Header) throws -> DecodedImage {
        let entryCount = clampEntryCount(hdr.clrUsed, bitDepth: 1)
        let palette = try readColorTable(bytes: bytes, hdr: hdr, entryCount: entryCount)
        let stride = ((hdr.width + 31) / 32) * 4
        guard bytes.count - hdr.pixelDataOffset >= stride * hdr.height else {
            throw DecodeError.truncated(format: .bmp, detail: "pixel data truncated (1-bit paletted)")
        }
        var pixels = [UInt8](repeating: 0, count: hdr.width * hdr.height * 3)
        for srcRow in 0..<hdr.height {
            let dstRow = hdr.topDown ? srcRow : (hdr.height - 1 - srcRow)
            for x in 0..<hdr.width {
                let byteOff = hdr.pixelDataOffset + srcRow * stride + (x / 8)
                let b = Int(bytes[byteOff])
                // MSB first: bit 7 is pixel 0
                var idx = (b >> (7 - (x % 8))) & 1
                if idx >= entryCount { idx = entryCount - 1 }
                let dstIdx = (dstRow * hdr.width + x) * 3
                pixels[dstIdx]     = palette[idx].0
                pixels[dstIdx + 1] = palette[idx].1
                pixels[dstIdx + 2] = palette[idx].2
            }
        }
        return DecodedImage(width: hdr.width, height: hdr.height, data: pixels, channels: .rgb, format: .bmp)
    }

    // MARK: - Decode BI_BITFIELDS

    private static func decodeBitfields(bytes: [UInt8], hdr: Header, bitsPerPixel: Int) throws -> DecodedImage {
        let hasAlpha = hdr.alphaMask != 0
        let channels = hasAlpha ? 4 : 3
        let ch: Channels = hasAlpha ? .rgba : .rgb

        // Pre-compute shifts and ranges for each channel
        let redShift   = hdr.redMask.trailingZeroBitCount
        let greenShift = hdr.greenMask.trailingZeroBitCount
        let blueShift  = hdr.blueMask.trailingZeroBitCount
        let redRange   = UInt64(hdr.redMask)   >> redShift
        let greenRange = UInt64(hdr.greenMask) >> greenShift
        let blueRange  = UInt64(hdr.blueMask)  >> blueShift
        let alphaShift = hasAlpha ? hdr.alphaMask.trailingZeroBitCount : 0
        let alphaRange = hasAlpha ? (UInt64(hdr.alphaMask) >> alphaShift) : UInt64(1)

        let stride: Int
        if bitsPerPixel == 16 {
            stride = ((hdr.width * 2 + 3) / 4) * 4
        } else {
            stride = hdr.width * 4
        }
        guard bytes.count - hdr.pixelDataOffset >= stride * hdr.height else {
            throw DecodeError.truncated(format: .bmp, detail: "pixel data truncated (BI_BITFIELDS \(bitsPerPixel)-bit)")
        }

        var pixels = [UInt8](repeating: 0, count: hdr.width * hdr.height * channels)
        for srcRow in 0..<hdr.height {
            let dstRow = hdr.topDown ? srcRow : (hdr.height - 1 - srcRow)
            for x in 0..<hdr.width {
                let srcIdx = hdr.pixelDataOffset + srcRow * stride
                let pixel: UInt64
                if bitsPerPixel == 16 {
                    let off = srcIdx + x * 2
                    let raw = UInt32(bytes[off]) | (UInt32(bytes[off+1]) << 8)
                    pixel = UInt64(raw)
                } else {
                    let off = srcIdx + x * 4
                    let raw = UInt32(bytes[off])
                             | (UInt32(bytes[off+1]) << 8)
                             | (UInt32(bytes[off+2]) << 16)
                             | (UInt32(bytes[off+3]) << 24)
                    pixel = UInt64(raw)
                }

                let r = UInt8((((pixel & UInt64(hdr.redMask))   >> redShift)   * 255) / redRange)
                let g = UInt8((((pixel & UInt64(hdr.greenMask)) >> greenShift) * 255) / greenRange)
                let b = UInt8((((pixel & UInt64(hdr.blueMask))  >> blueShift)  * 255) / blueRange)

                let dstIdx = (dstRow * hdr.width + x) * channels
                pixels[dstIdx]     = r
                pixels[dstIdx + 1] = g
                pixels[dstIdx + 2] = b
                if hasAlpha {
                    let a = UInt8((((pixel & UInt64(hdr.alphaMask)) >> alphaShift) * 255) / alphaRange)
                    pixels[dstIdx + 3] = a
                }
            }
        }
        return DecodedImage(width: hdr.width, height: hdr.height, data: pixels, channels: ch, format: .bmp)
    }

    // MARK: - Decode RLE (8-bit or 4-bit)

    private static func decodeRle(bytes: [UInt8], hdr: Header, bitsPerPixel: Int) throws -> DecodedImage {
        let entryCount = clampEntryCount(hdr.clrUsed, bitDepth: bitsPerPixel)
        let palette = try readColorTable(bytes: bytes, hdr: hdr, entryCount: entryCount)

        let xsize = hdr.width
        let ysize = hdr.height

        // Replicate Pillow's BmpRleDecoder exactly.
        // Pillow accumulates pixel indices into a flat buffer in file-scanline order,
        // then calls set_as_raw with direction=-1 (bottom-up) or +1 (top-down).
        var dataBuf = [Int]()
        dataBuf.reserveCapacity(xsize * ysize)

        var x = 0
        var pos = hdr.pixelDataOffset
        let end = bytes.count

        outer: while dataBuf.count < xsize * ysize {
            guard pos + 1 < end else { break }
            let numPixels = Int(bytes[pos]); pos += 1
            let dataByte  = Int(bytes[pos]); pos += 1

            if numPixels != 0 {
                // Encoded mode: clip at end of row (Pillow behavior)
                var count = numPixels
                if x + count > xsize {
                    count = max(0, xsize - x)
                }
                if bitsPerPixel == 8 {
                    for _ in 0..<count { dataBuf.append(dataByte) }
                } else {
                    // RLE4: alternating high/low nibble
                    for i in 0..<count {
                        dataBuf.append((i % 2 == 0) ? (dataByte >> 4) : (dataByte & 0xF))
                    }
                }
                x += count
            } else {
                if dataByte == 0 {
                    // EOL: pad with zeros to next row boundary (Pillow behavior)
                    while dataBuf.count % xsize != 0 { dataBuf.append(0) }
                    x = 0
                } else if dataByte == 1 {
                    // End of bitmap
                    break outer
                } else if dataByte == 2 {
                    // Delta: per BMP spec (and Pillow 12.x), the 2 bytes
                    // following the `00 02` escape are (dx, dy).
                    // (Pillow ≤ 11 had a bug that consumed 4 bytes here; we
                    // anchored to that buggy behavior in earlier goldens.
                    // Pillow 12.x fixed it; goldens regenerated against the
                    // spec-correct 2-byte read.)
                    guard pos + 1 < end else { break }
                    let right = Int(bytes[pos]); pos += 1
                    let up    = Int(bytes[pos]); pos += 1
                    let zeros = right + up * xsize
                    for _ in 0..<zeros { dataBuf.append(0) }
                    x = dataBuf.count % xsize
                } else {
                    // Absolute mode: dataByte >= 3 pixels follow
                    let numAbs = dataByte
                    let byteCount: Int
                    if bitsPerPixel == 8 {
                        byteCount = numAbs
                    } else {
                        // RLE4: Pillow uses floor division (byte[0] // 2), NOT ceil
                        byteCount = numAbs / 2
                    }
                    guard pos + byteCount <= end else { break }
                    if bitsPerPixel == 8 {
                        for i in 0..<byteCount {
                            dataBuf.append(Int(bytes[pos + i]))
                        }
                    } else {
                        // RLE4: emit both nibbles of each byte read
                        for i in 0..<byteCount {
                            let b = Int(bytes[pos + i])
                            dataBuf.append(b >> 4)
                            dataBuf.append(b & 0xF)
                        }
                    }
                    x += numAbs
                    pos += byteCount
                    // Word-align: check if (pos - hdr.pixelDataOffset) % 2 != 0
                    if (pos - hdr.pixelDataOffset) % 2 != 0 {
                        pos += 1
                    }
                }
            }
        }

        // Detect RLE overrun: if loop exited before buffer is full, the stream is corrupt.
        guard dataBuf.count >= xsize * ysize else {
            throw DecodeError.corruptInput(format: .bmp,
                detail: "RLE stream ended with \(dataBuf.count) pixels, expected \(xsize * ysize)")
        }

        // Build output pixels.
        // Pillow's set_as_raw with direction=-1 reverses rows:
        // image row i = buffer row (ysize - 1 - i) for bottom-up.
        // For top-down (direction=+1), image row i = buffer row i.
        var pixels = [UInt8](repeating: 0, count: xsize * ysize * 3)
        for bufRow in 0..<ysize {
            let imgRow = hdr.topDown ? bufRow : (ysize - 1 - bufRow)
            for col in 0..<xsize {
                var palIdx = dataBuf[bufRow * xsize + col]
                if palIdx >= entryCount { palIdx = entryCount - 1 }
                let rgb = palette[palIdx]
                let dstIdx = (imgRow * xsize + col) * 3
                pixels[dstIdx]     = rgb.0
                pixels[dstIdx + 1] = rgb.1
                pixels[dstIdx + 2] = rgb.2
            }
        }
        return DecodedImage(width: hdr.width, height: hdr.height, data: pixels, channels: .rgb, format: .bmp)
    }
}
