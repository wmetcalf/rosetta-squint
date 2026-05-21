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
        return nil
    }

    public static func supportedFormats() -> [Format] {
        [.bmp, .png, .gif]
    }
}
