public enum Decoder {
    public static func decode(_ bytes: [UInt8]) throws -> DecodedImage {
        guard let fmt = detectFormat(bytes) else {
            let magic = bytes.count >= 2 ? Array(bytes.prefix(2)) : Array(bytes)
            throw DecodeError.unsupportedFormat(magic: magic)
        }
        switch fmt {
        case .bmp:
            return try BMPDecoder.decode(bytes: bytes)
        default:
            throw DecodeError.unsupportedFormat(magic: bytes.count >= 2 ? Array(bytes.prefix(2)) : Array(bytes))
        }
    }

    public static func detectFormat(_ bytes: [UInt8]) -> Format? {
        guard bytes.count >= 2 else { return nil }
        if bytes[0] == 0x42 && bytes[1] == 0x4D { return .bmp }
        return nil
    }

    public static func supportedFormats() -> [Format] {
        [.bmp]
    }
}
