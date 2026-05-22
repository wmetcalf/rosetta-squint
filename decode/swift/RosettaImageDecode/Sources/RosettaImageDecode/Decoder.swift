public enum Decoder {
    public static func decode(_ bytes: [UInt8]) throws -> DecodedImage {
        guard let fmt = detectFormat(bytes) else {
            let magic = bytes.count >= 2 ? Array(bytes.prefix(2)) : Array(bytes)
            throw DecodeError.unsupportedFormat(magic: magic)
        }
        switch fmt {
        case .bmp:
            return try BMPDecoder.decode(bytes: bytes)
        case .png:
            return try PNGDecoder.decode(bytes: bytes)
        case .gif:
            return try GIFDecoder.decode(bytes: bytes)
        case .jpeg:
            return try JPEGDecoder.decode(bytes: bytes)
        case .webp:
            return try WebPDecoder.decode(bytes: bytes)
        case .tiff:
            return try TIFFDecoder.decode(bytes: bytes)
        case .heic:
            return try HEICDecoder.decode(bytes: bytes)
        default:
            throw DecodeError.unsupportedFormat(magic: bytes.count >= 2 ? Array(bytes.prefix(2)) : Array(bytes))
        }
    }

    public static func detectFormat(_ bytes: [UInt8]) -> Format? {
        guard bytes.count >= 2 else { return nil }
        if bytes[0] == 0x42 && bytes[1] == 0x4D { return .bmp }
        if bytes.count >= 8
           && bytes[0] == 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4E && bytes[3] == 0x47
           && bytes[4] == 0x0D && bytes[5] == 0x0A && bytes[6] == 0x1A && bytes[7] == 0x0A {
            return .png
        }
        if bytes.count >= 6
           && bytes[0] == 0x47 && bytes[1] == 0x49 && bytes[2] == 0x46 && bytes[3] == 0x38
           && (bytes[4] == 0x37 || bytes[4] == 0x39) && bytes[5] == 0x61 {
            return .gif
        }
        if bytes.count >= 2 && bytes[0] == 0xFF && bytes[1] == 0xD8 {
            return .jpeg
        }
        if bytes.count >= 12
           && bytes[0] == 0x52 && bytes[1] == 0x49 && bytes[2] == 0x46 && bytes[3] == 0x46
           && bytes[8] == 0x57 && bytes[9] == 0x45 && bytes[10] == 0x42 && bytes[11] == 0x50 {
            return .webp
        }
        // TIFF little-endian: II (0x49 0x49) + magic 42 (0x2A 0x00)
        if bytes.count >= 4
           && bytes[0] == 0x49 && bytes[1] == 0x49
           && bytes[2] == 0x2A && bytes[3] == 0x00 {
            return .tiff
        }
        // TIFF big-endian: MM (0x4D 0x4D) + magic 42 (0x00 0x2A)
        if bytes.count >= 4
           && bytes[0] == 0x4D && bytes[1] == 0x4D
           && bytes[2] == 0x00 && bytes[3] == 0x2A {
            return .tiff
        }
        // HEIC: bytes[4..8) = "ftyp", bytes[8..12) = brand in HEIC-specific set
        if bytes.count >= 12
           && bytes[4] == 0x66 && bytes[5] == 0x74 && bytes[6] == 0x79 && bytes[7] == 0x70 {
            let brand = String(bytes: bytes[8..<12], encoding: .ascii) ?? ""
            let heicBrands: Set<String> = ["heic", "heix", "mif1", "msf1", "hevc", "hevx"]
            if heicBrands.contains(brand) {
                return .heic
            }
        }
        return nil
    }

    public static func supportedFormats() -> [Format] {
        [.bmp, .png, .gif, .jpeg, .webp, .tiff, .heic]
    }
}
