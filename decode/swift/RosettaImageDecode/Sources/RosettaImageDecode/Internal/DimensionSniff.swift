import Foundation

// Pre-decode dimension sniffers for formats whose native library refuses
// dimensions that exceed its own internal limits before our MAX_PIXELS
// check has a chance to run.
//
// Concretely:
//   - libwebp's WebPGetFeatures returns VP8_STATUS_BITSTREAM_ERROR for
//     VP8X-declared canvas dimensions that fail libwebp's internal
//     validation, so we never see the dimensions.
//   - libheif returns dimensions from the underlying HEVC bitstream rather
//     than the container's ispe box, so even a patched ispe is ignored by
//     heif_image_handle_get_width/height.
//   - libtiff rejects the 38-byte too-large fixture as corruptInput while
//     reading the IFD; we have to parse the IFD ourselves to see the
//     declared ImageWidth/ImageLength before libtiff is invoked.
//
// By peeking at the file's declared dimensions ourselves before invoking
// the native library, we ensure that "header says it's too large" produces
// a clean imageTooLarge error instead of corruptInput. Spec §3.1 requires
// this ordering.

internal enum DimensionSniff {

    /// Sniff WebP canvas dimensions from the VP8X chunk header.
    ///
    /// VP8X canvas dims live as 24-bit ``width - 1`` and ``height - 1``
    /// little-endian fields at offsets 24 and 27 from the start of the file.
    /// Returns nil for non-VP8X WebPs (VP8/VP8L); those rarely exceed
    /// libwebp's 14-bit per-side limit so the existing post-WebPGetInfo
    /// check covers them.
    static func sniffWebpDimensions(_ bytes: [UInt8]) -> (Int, Int)? {
        guard bytes.count >= 30 else { return nil }
        // RIFF magic
        guard bytes[0] == 0x52, bytes[1] == 0x49, bytes[2] == 0x46, bytes[3] == 0x46 else {
            return nil
        }
        // WEBP magic
        guard bytes[8] == 0x57, bytes[9] == 0x45, bytes[10] == 0x42, bytes[11] == 0x50 else {
            return nil
        }
        // VP8X chunk marker
        guard bytes[12] == 0x56, bytes[13] == 0x50, bytes[14] == 0x38, bytes[15] == 0x58 else {
            return nil
        }
        let wMinus1 = Int(bytes[24]) | (Int(bytes[25]) << 8) | (Int(bytes[26]) << 16)
        let hMinus1 = Int(bytes[27]) | (Int(bytes[28]) << 8) | (Int(bytes[29]) << 16)
        return (wMinus1 + 1, hMinus1 + 1)
    }

    /// Sniff HEIC primary-image dimensions from the first ``ispe`` (Image
    /// Spatial Extents) box.
    ///
    /// HEIF/HEIC is an ISO Base Media File Format container; ``ispe`` payload
    /// is fixed: 4 bytes version+flags, then 4 bytes width and 4 bytes height
    /// as big-endian u32. The box lives inside ``meta`` → ``iprp`` → ``ipco``
    /// → ``ispe``. We scan the byte stream for the literal "ispe" fourcc and
    /// read the 8 bytes of dimensions that follow the version word. Capped at
    /// 1 MiB of prefix to keep the scan bounded for adversarial inputs.
    static func sniffHeicDimensions(_ bytes: [UInt8]) -> (Int, Int)? {
        guard bytes.count >= 30 else { return nil }
        let scanLimit = min(bytes.count - 16, 1024 * 1024)
        var i = 0
        while i < scanLimit {
            // 'i','s','p','e' = 0x69, 0x73, 0x70, 0x65
            if bytes[i] == 0x69 && bytes[i + 1] == 0x73 &&
               bytes[i + 2] == 0x70 && bytes[i + 3] == 0x65 {
                let wOff = i + 4 + 4 // skip "ispe" type + version+flags
                if wOff + 8 > bytes.count { return nil }
                let width = (Int(bytes[wOff]) << 24)
                          | (Int(bytes[wOff + 1]) << 16)
                          | (Int(bytes[wOff + 2]) << 8)
                          |  Int(bytes[wOff + 3])
                let height = (Int(bytes[wOff + 4]) << 24)
                           | (Int(bytes[wOff + 5]) << 16)
                           | (Int(bytes[wOff + 6]) << 8)
                           |  Int(bytes[wOff + 7])
                if width == 0 || height == 0 { return nil }
                return (width, height)
            }
            i += 1
        }
        return nil
    }

    /// Sniff TIFF dimensions from the first IFD.
    ///
    /// TIFF layout: 2-byte byte order ("II" = little-endian, "MM" =
    /// big-endian), 2-byte magic (42), 4-byte offset to first IFD. An IFD
    /// begins with a 2-byte entry count followed by 12-byte entries
    /// {tag(2), type(2), count(4), value-or-offset(4)}. For SHORT (type 3)
    /// or LONG (type 4) values with count=1, the value is stored directly in
    /// the 4-byte value field. We look up ImageWidth (tag 0x0100) and
    /// ImageLength (tag 0x0101). Entry count loop is bounded defensively.
    static func sniffTiffDimensions(_ bytes: [UInt8]) -> (Int, Int)? {
        guard bytes.count >= 8 else { return nil }
        let littleEndian: Bool
        if bytes[0] == 0x49 && bytes[1] == 0x49 {
            littleEndian = true
        } else if bytes[0] == 0x4D && bytes[1] == 0x4D {
            littleEndian = false
        } else {
            return nil
        }

        func readU16(_ off: Int) -> Int {
            if off + 2 > bytes.count { return -1 }
            if littleEndian {
                return Int(bytes[off]) | (Int(bytes[off + 1]) << 8)
            } else {
                return (Int(bytes[off]) << 8) | Int(bytes[off + 1])
            }
        }
        func readU32(_ off: Int) -> Int {
            if off + 4 > bytes.count { return -1 }
            if littleEndian {
                return Int(bytes[off])
                     | (Int(bytes[off + 1]) << 8)
                     | (Int(bytes[off + 2]) << 16)
                     | (Int(bytes[off + 3]) << 24)
            } else {
                return (Int(bytes[off]) << 24)
                     | (Int(bytes[off + 1]) << 16)
                     | (Int(bytes[off + 2]) << 8)
                     |  Int(bytes[off + 3])
            }
        }

        let magic = readU16(2)
        guard magic == 42 else { return nil }

        let ifdOffset = readU32(4)
        guard ifdOffset >= 8, ifdOffset + 2 <= bytes.count else { return nil }

        let entryCount = readU16(ifdOffset)
        guard entryCount > 0 else { return nil }
        // Defensive cap: real TIFFs rarely exceed 100 entries in IFD0, and any
        // file claiming thousands of entries before the dim fields is suspect.
        let cappedCount = min(entryCount, 4096)

        var width: Int? = nil
        var height: Int? = nil

        for entryIdx in 0..<cappedCount {
            let entryOff = ifdOffset + 2 + entryIdx * 12
            if entryOff + 12 > bytes.count { return nil }
            let tag = readU16(entryOff)
            let type = readU16(entryOff + 2)
            let count = readU32(entryOff + 4)

            // Only handle SHORT (3) or LONG (4) with count=1, where value
            // is in-line in the value-or-offset field.
            if count != 1 { continue }
            if type != 3 && type != 4 { continue }

            let valueOff = entryOff + 8
            let value: Int
            if type == 3 {
                // SHORT: 2-byte value in low half of the 4-byte field.
                value = readU16(valueOff)
            } else {
                // LONG: full 4-byte value.
                value = readU32(valueOff)
            }
            if value < 0 { return nil }

            if tag == 0x0100 {
                width = value
            } else if tag == 0x0101 {
                height = value
            }

            if width != nil && height != nil { break }
        }

        guard let w = width, let h = height, w > 0, h > 0 else { return nil }
        return (w, h)
    }
}
