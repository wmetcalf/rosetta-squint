import Foundation

// MARK: - Public entry point

internal enum GIFDecoder {
    static func decode(bytes: [UInt8]) throws -> DecodedImage {
        var parser = GIFParser(bytes: bytes)

        // Parse header (6 bytes) and logical screen descriptor (7 bytes)
        try parser.parseHeader()
        try parser.parseLogicalScreenDescriptor()

        // Read global color table if present
        if parser.hasGlobalColorTable {
            try parser.parseGlobalColorTable()
        }

        // Walk blocks until first Image Descriptor
        var transparentIndex: Int? = nil
        while true {
            guard let intro = parser.readByte() else {
                throw DecodeError.corruptInput(format: .gif, detail: "unexpected EOF before image descriptor")
            }
            switch intro {
            case 0x21: // Extension introducer
                try parser.skipOrParseExtension(transparentIndex: &transparentIndex)
            case 0x2C: // Image Descriptor
                let frame = try parser.parseImageDescriptor()

                // D-M2: per-frame dimension validation. The image descriptor
                // declares frame dims independently of the LSD (canvas) dims;
                // without this check a 16x16 LSD can still carry a 65535x65535
                // frame and drive ~34 GB of allocation in deinterlace below.
                // MAX_PIXELS check runs first so a decompression-bomb frame is
                // flagged as imageTooLarge rather than shadowed by the
                // canvas-extent check.
                // 1) Frame pixel count itself must respect MAX_PIXELS.
                try Limits.checkDimensions(width: frame.width, height: frame.height, format: .gif)
                // 2) Frame must lie within the canvas — otherwise input is corrupt.
                if frame.left + frame.width > parser.lsdWidth ||
                   frame.top + frame.height > parser.lsdHeight {
                    throw DecodeError.corruptInput(
                        format: .gif,
                        detail: "frame \(frame.width)x\(frame.height) at (\(frame.left),\(frame.top)) extends beyond canvas \(parser.lsdWidth)x\(parser.lsdHeight)"
                    )
                }

                let palette: [[UInt8]]
                if let lct = frame.localColorTable {
                    palette = lct
                } else if let gct = parser.globalColorTable {
                    palette = gct
                } else {
                    throw DecodeError.corruptInput(format: .gif, detail: "no color table available")
                }
                var indices = try parser.decodeLZWImage(frameWidth: frame.width, frameHeight: frame.height)
                if frame.interlaced {
                    indices = GIFDecoder.deinterlace(indices: indices, width: frame.width, height: frame.height)
                }
                return GIFDecoder.emitFrame(
                    canvasWidth: parser.lsdWidth,
                    canvasHeight: parser.lsdHeight,
                    frameLeft: frame.left,
                    frameTop: frame.top,
                    frameWidth: frame.width,
                    frameHeight: frame.height,
                    indices: indices,
                    palette: palette,
                    transparentIndex: transparentIndex,
                    bgIndex: parser.bgIndex
                )
            case 0x3B: // Trailer
                throw DecodeError.corruptInput(format: .gif, detail: "GIF trailer reached before any image")
            default:
                throw DecodeError.corruptInput(format: .gif, detail: "unknown block introducer: 0x\(String(intro, radix: 16))")
            }
        }
    }

    private static func emitFrame(
        canvasWidth: Int, canvasHeight: Int,
        frameLeft: Int, frameTop: Int,
        frameWidth: Int, frameHeight: Int,
        indices: [Int],
        palette: [[UInt8]],
        transparentIndex: Int?,
        bgIndex: Int
    ) -> DecodedImage {
        let hasAlpha = transparentIndex != nil
        let bpp = hasAlpha ? 4 : 3

        // Canvas filled with background color (palette[bgIndex]) or transparent
        var data = [UInt8](repeating: 0, count: canvasWidth * canvasHeight * bpp)

        // Fill canvas background
        let bgRGB: (UInt8, UInt8, UInt8)
        if bgIndex < palette.count {
            let entry = palette[bgIndex]
            bgRGB = (entry[0], entry[1], entry[2])
        } else {
            bgRGB = (0, 0, 0)
        }
        if !hasAlpha {
            for i in 0..<(canvasWidth * canvasHeight) {
                data[i * 3]     = bgRGB.0
                data[i * 3 + 1] = bgRGB.1
                data[i * 3 + 2] = bgRGB.2
            }
        } else {
            // For RGBA canvas, background pixels are fully opaque bg color
            for i in 0..<(canvasWidth * canvasHeight) {
                data[i * 4]     = bgRGB.0
                data[i * 4 + 1] = bgRGB.1
                data[i * 4 + 2] = bgRGB.2
                data[i * 4 + 3] = 255
            }
        }

        // Write frame pixels into canvas
        for fy in 0..<frameHeight {
            let cy = frameTop + fy
            guard cy >= 0 && cy < canvasHeight else { continue }
            for fx in 0..<frameWidth {
                let cx = frameLeft + fx
                guard cx >= 0 && cx < canvasWidth else { continue }
                let srcIdx = fy * frameWidth + fx
                guard srcIdx < indices.count else { continue }
                let palIdx = indices[srcIdx]
                let rgb: (UInt8, UInt8, UInt8)
                if palIdx < palette.count {
                    let entry = palette[palIdx]
                    rgb = (entry[0], entry[1], entry[2])
                } else {
                    rgb = (0, 0, 0)
                }
                let dstBase = (cy * canvasWidth + cx) * bpp
                data[dstBase]     = rgb.0
                data[dstBase + 1] = rgb.1
                data[dstBase + 2] = rgb.2
                if hasAlpha {
                    let alpha: UInt8 = (palIdx == transparentIndex!) ? 0 : 255
                    data[dstBase + 3] = alpha
                }
            }
        }

        return DecodedImage(
            width: canvasWidth,
            height: canvasHeight,
            data: data,
            channels: hasAlpha ? .rgba : .rgb,
            format: .gif
        )
    }

    // MARK: - Interlace de-interleaving

    /// GIF interlacing delivers rows in 4 passes:
    ///   Pass 1: rows 0, 8, 16, ...  (step 8, start 0)
    ///   Pass 2: rows 4, 12, 20, ... (step 8, start 4)
    ///   Pass 3: rows 2, 6, 10, ...  (step 4, start 2)
    ///   Pass 4: rows 1, 3, 5, ...   (step 2, start 1)
    ///
    /// `indices` contains pixels in that scan order. We reorder them into
    /// normal top-to-bottom, left-to-right order.
    private static func deinterlace(indices: [Int], width: Int, height: Int) -> [Int] {
        // Build list of actual row indices in interlaced order.
        var interlacedRows = [Int]()
        interlacedRows.reserveCapacity(height)

        let passes: [(start: Int, step: Int)] = [(0, 8), (4, 8), (2, 4), (1, 2)]
        for pass in passes {
            var row = pass.start
            while row < height {
                interlacedRows.append(row)
                row += pass.step
            }
        }

        // `interlacedRows[i]` is the destination row for the i-th row in the raw LZW output.
        var result = [Int](repeating: 0, count: width * height)
        for (srcRow, dstRow) in interlacedRows.enumerated() {
            let srcBase = srcRow * width
            let dstBase = dstRow * width
            for col in 0..<width {
                if srcBase + col < indices.count {
                    result[dstBase + col] = indices[srcBase + col]
                }
            }
        }
        return result
    }
}

// MARK: - GIFParser

private struct FrameInfo {
    let left: Int
    let top: Int
    let width: Int
    let height: Int
    let localColorTable: [[UInt8]]?
    let interlaced: Bool
}

private struct GIFParser {
    let bytes: [UInt8]
    var pos: Int = 0

    // LSD fields
    var lsdWidth: Int = 0
    var lsdHeight: Int = 0
    var hasGlobalColorTable: Bool = false
    var gctSize: Int = 0
    var bgIndex: Int = 0

    // Decoded GCT
    var globalColorTable: [[UInt8]]? = nil

    init(bytes: [UInt8]) {
        self.bytes = bytes
    }

    // MARK: - Basic reading

    mutating func readByte() -> UInt8? {
        guard pos < bytes.count else { return nil }
        defer { pos += 1 }
        return bytes[pos]
    }

    mutating func readBytes(_ n: Int) -> [UInt8]? {
        guard pos + n <= bytes.count else { return nil }
        defer { pos += n }
        return Array(bytes[pos..<(pos + n)])
    }

    mutating func readU16LE() -> Int? {
        guard pos + 2 <= bytes.count else { return nil }
        let lo = Int(bytes[pos])
        let hi = Int(bytes[pos + 1])
        pos += 2
        return lo | (hi << 8)
    }

    // MARK: - Header

    mutating func parseHeader() throws {
        guard let sig = readBytes(6) else {
            throw DecodeError.corruptInput(format: .gif, detail: "truncated header")
        }
        // sig[0..2] = "GIF", sig[3..5] = "87a" or "89a"
        guard sig[0] == 0x47 && sig[1] == 0x49 && sig[2] == 0x46 else {
            throw DecodeError.unsupportedFormat(magic: Array(sig.prefix(2)))
        }
        guard (sig[3] == 0x38) &&
              ((sig[4] == 0x37 || sig[4] == 0x39)) &&
              (sig[5] == 0x61) else {
            throw DecodeError.corruptInput(format: .gif, detail: "invalid GIF version")
        }
    }

    mutating func parseLogicalScreenDescriptor() throws {
        guard let w = readU16LE(), let h = readU16LE() else {
            throw DecodeError.corruptInput(format: .gif, detail: "truncated logical screen descriptor")
        }
        guard let packed = readByte(), let bg = readByte(), let _ = readByte() else {
            throw DecodeError.corruptInput(format: .gif, detail: "truncated logical screen descriptor")
        }
        try Limits.checkDimensions(width: w, height: h, format: .gif)
        lsdWidth = w
        lsdHeight = h
        hasGlobalColorTable = (packed >> 7) & 1 == 1
        gctSize = Int(packed & 0x07)
        bgIndex = Int(bg)
    }

    mutating func parseGlobalColorTable() throws {
        let count = 1 << (gctSize + 1)
        globalColorTable = try parseColorTable(count: count)
    }

    private mutating func parseColorTable(count: Int) throws -> [[UInt8]] {
        guard let data = readBytes(count * 3) else {
            throw DecodeError.corruptInput(format: .gif, detail: "truncated color table")
        }
        var table = [[UInt8]](repeating: [0, 0, 0], count: count)
        for i in 0..<count {
            table[i] = [data[i * 3], data[i * 3 + 1], data[i * 3 + 2]]
        }
        return table
    }

    // MARK: - Extensions

    mutating func skipOrParseExtension(transparentIndex: inout Int?) throws {
        guard let label = readByte() else {
            throw DecodeError.corruptInput(format: .gif, detail: "truncated extension label")
        }
        switch label {
        case 0xF9: // Graphic Control Extension
            try parseGCE(transparentIndex: &transparentIndex)
        default:
            // All other extensions: skip sub-blocks
            try skipSubBlocks()
        }
    }

    private mutating func parseGCE(transparentIndex: inout Int?) throws {
        // Block size = 4 (always for GCE)
        guard let blockSize = readByte() else {
            throw DecodeError.corruptInput(format: .gif, detail: "truncated GCE block size")
        }
        guard blockSize >= 4 else {
            throw DecodeError.corruptInput(format: .gif, detail: "GCE block size < 4")
        }
        guard let packed = readByte(),
              let _ = readByte(), // delay lo
              let _ = readByte(), // delay hi
              let tidx = readByte() else {
            throw DecodeError.corruptInput(format: .gif, detail: "truncated GCE data")
        }
        // Skip any extra bytes if blockSize > 4
        if blockSize > 4 {
            guard readBytes(Int(blockSize) - 4) != nil else {
                throw DecodeError.corruptInput(format: .gif, detail: "truncated GCE extra bytes")
            }
        }
        // Read block terminator
        guard let term = readByte() else {
            throw DecodeError.corruptInput(format: .gif, detail: "truncated GCE terminator")
        }
        if term != 0x00 {
            // Not a proper terminator; treat as sub-block chain (some encoders do this)
            // Back up one and skip sub-blocks
            pos -= 1
            try skipSubBlocks()
        }
        let transparentFlag = packed & 0x01
        if transparentFlag != 0 {
            transparentIndex = Int(tidx)
        } else {
            transparentIndex = nil
        }
    }

    private mutating func skipSubBlocks() throws {
        while true {
            guard let sz = readByte() else {
                throw DecodeError.corruptInput(format: .gif, detail: "truncated sub-block size")
            }
            if sz == 0 { return }
            guard readBytes(Int(sz)) != nil else {
                throw DecodeError.corruptInput(format: .gif, detail: "truncated sub-block data")
            }
        }
    }

    // MARK: - Image Descriptor

    mutating func parseImageDescriptor() throws -> FrameInfo {
        // 0x2C already consumed; read remaining 9 bytes
        guard let left   = readU16LE(),
              let top    = readU16LE(),
              let width  = readU16LE(),
              let height = readU16LE(),
              let packed = readByte() else {
            throw DecodeError.corruptInput(format: .gif, detail: "truncated image descriptor")
        }
        let hasLCT    = (packed >> 7) & 1 == 1
        let interlace = (packed >> 6) & 1 == 1
        let lctSize   = Int(packed & 0x07)

        var lct: [[UInt8]]? = nil
        if hasLCT {
            let count = 1 << (lctSize + 1)
            lct = try parseColorTable(count: count)
        }

        return FrameInfo(
            left: left, top: top,
            width: width, height: height,
            localColorTable: lct,
            interlaced: interlace
        )
    }

    // MARK: - LZW Image Data

    mutating func decodeLZWImage(frameWidth: Int, frameHeight: Int) throws -> [Int] {
        guard let minCodeSizeByte = readByte() else {
            throw DecodeError.corruptInput(format: .gif, detail: "missing LZW minimum code size")
        }
        let minCodeSize = Int(minCodeSizeByte)
        guard minCodeSize >= 2 && minCodeSize <= 12 else {
            throw DecodeError.corruptInput(format: .gif, detail: "invalid LZW min code size: \(minCodeSize)")
        }

        // Collect all sub-block data into one buffer
        var lzwData = [UInt8]()
        while true {
            guard let sz = readByte() else {
                throw DecodeError.corruptInput(format: .gif, detail: "truncated LZW sub-block size")
            }
            if sz == 0 { break }
            guard let chunk = readBytes(Int(sz)) else {
                throw DecodeError.corruptInput(format: .gif, detail: "truncated LZW sub-block data")
            }
            lzwData.append(contentsOf: chunk)
        }

        return try lzwDecode(data: lzwData, minCodeSize: minCodeSize,
                             pixelCount: frameWidth * frameHeight)
    }

    // MARK: - LZW Decompressor

    private func lzwDecode(data: [UInt8], minCodeSize: Int, pixelCount: Int) throws -> [Int] {
        let clearCode = 1 << minCodeSize
        let endCode   = clearCode + 1

        var bitReader  = BitReader(data: data)
        var output     = [Int]()
        output.reserveCapacity(pixelCount)

        // Code table: index → byte sequence.
        // Entries 0..<clearCode are single-byte sequences.
        // clearCode (index clearCode) and endCode (index clearCode+1) are sentinels.
        var codeTable  = [[UInt8]]()
        var codeSize   = minCodeSize + 1
        var nextCode   = endCode + 1

        func resetTable() {
            codeTable = [[UInt8]](repeating: [], count: clearCode + 2)
            for i in 0..<clearCode {
                codeTable[i] = [UInt8(i)]
            }
            codeSize = minCodeSize + 1
            nextCode = endCode + 1
        }

        resetTable()

        // prevSequence: the sequence emitted by the last code.
        // nil means we are at the start of a fresh run (just after a clear, or at the
        // very beginning). In this state the next code is treated as "first after clear":
        // we emit it, record it as prevSequence, and DON'T add a new code-table entry.
        var prevSequence: [UInt8]? = nil

        decode_loop: while output.count < pixelCount {
            guard let code = bitReader.readBits(codeSize) else {
                break decode_loop
            }

            if code == endCode {
                break decode_loop
            }

            if code == clearCode {
                resetTable()
                prevSequence = nil
                continue
            }

            if prevSequence == nil {
                // First real code after a clear (or absolute start).
                // Must be a literal palette code (0..<clearCode).
                guard code < clearCode else {
                    throw DecodeError.corruptInput(format: .gif, detail: "LZW: first code after clear is out of range: \(code)")
                }
                let seq = codeTable[code]
                output.append(contentsOf: seq.map { Int($0) })
                prevSequence = seq
                continue
            }

            let prev = prevSequence!

            // Look up current code in table (or handle the "code == nextCode" special case).
            let entry: [UInt8]
            if code < codeTable.count && !codeTable[code].isEmpty {
                entry = codeTable[code]
            } else if code == nextCode {
                // The new code being defined right now: its sequence is prev + prev[0].
                entry = prev + [prev[0]]
            } else {
                throw DecodeError.corruptInput(format: .gif, detail: "LZW: code \(code) out of range (table size \(nextCode))")
            }

            output.append(contentsOf: entry.map { Int($0) })

            // Define a new code table entry: prev_sequence + first_byte(entry).
            if nextCode <= 4095 {
                let newEntry = prev + [entry[0]]
                if nextCode < codeTable.count {
                    codeTable[nextCode] = newEntry
                } else {
                    codeTable.append(newEntry)
                }
                nextCode += 1

                // Expand code size when the table fills the current bit width.
                if nextCode == (1 << codeSize) && codeSize < 12 {
                    codeSize += 1
                }
            }

            prevSequence = entry
        }

        return output
    }
}

// MARK: - BitReader (LSB-first)

private struct BitReader {
    let data: [UInt8]
    var byteIdx: Int = 0
    var bitIdx: Int  = 0

    mutating func readBits(_ n: Int) -> Int? {
        var value    = 0
        var bitsRead = 0
        while bitsRead < n {
            guard byteIdx < data.count else { return nil }
            let bit = (Int(data[byteIdx]) >> bitIdx) & 1
            value |= bit << bitsRead
            bitsRead += 1
            bitIdx += 1
            if bitIdx == 8 {
                byteIdx += 1
                bitIdx = 0
            }
        }
        return value
    }
}
