import Foundation

internal enum BMPDecoder {
    static func decode(bytes: [UInt8]) throws -> DecodedImage {
        throw DecodeError.unsupportedFeature(format: .bmp, feature: "BMP decoder not yet implemented")
    }
}
