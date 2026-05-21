import Foundation
import Cwebp

internal enum WebPDecoder {
    static func decode(bytes: [UInt8]) throws -> DecodedImage {
        // Get image info: width, height
        var width: Int32 = 0
        var height: Int32 = 0
        let getInfoRet = bytes.withUnsafeBufferPointer { buf -> Int32 in
            WebPGetInfo(buf.baseAddress, buf.count, &width, &height)
        }
        if getInfoRet == 0 || width <= 0 || height <= 0 {
            throw DecodeError.corruptInput(format: .webp, detail: "WebPGetInfo failed")
        }

        // Detect alpha from container header
        let hasAlpha = detectWebpAlpha(bytes)

        if hasAlpha {
            // Decode to RGBA
            var outWidth: Int32 = 0
            var outHeight: Int32 = 0
            let rawPtr = bytes.withUnsafeBufferPointer { buf in
                WebPDecodeRGBA(buf.baseAddress, buf.count, &outWidth, &outHeight)
            }
            guard let ptr = rawPtr else {
                throw DecodeError.corruptInput(format: .webp, detail: "WebPDecodeRGBA returned null")
            }
            defer { WebPFree(UnsafeMutableRawPointer(ptr)) }

            let count = Int(outWidth) * Int(outHeight) * 4
            let data = Array(UnsafeBufferPointer(start: ptr, count: count))
            return DecodedImage(
                width: Int(outWidth),
                height: Int(outHeight),
                data: data,
                channels: .rgba,
                format: .webp
            )
        } else {
            var outWidth: Int32 = 0
            var outHeight: Int32 = 0
            let rawPtr = bytes.withUnsafeBufferPointer { buf in
                WebPDecodeRGB(buf.baseAddress, buf.count, &outWidth, &outHeight)
            }
            guard let ptr = rawPtr else {
                throw DecodeError.corruptInput(format: .webp, detail: "WebPDecodeRGB returned null")
            }
            defer { WebPFree(UnsafeMutableRawPointer(ptr)) }

            let count = Int(outWidth) * Int(outHeight) * 3
            let data = Array(UnsafeBufferPointer(start: ptr, count: count))
            return DecodedImage(
                width: Int(outWidth),
                height: Int(outHeight),
                data: data,
                channels: .rgb,
                format: .webp
            )
        }
    }

    private static func detectWebpAlpha(_ bytes: [UInt8]) -> Bool {
        guard bytes.count >= 20 else { return false }
        let chunkType = String(bytes: bytes[12..<16], encoding: .ascii) ?? ""
        switch chunkType {
        case "VP8X":
            guard bytes.count >= 21 else { return false }
            return (bytes[20] & 0x10) != 0
        case "VP8L":
            guard bytes.count >= 25 else { return false }
            return (bytes[24] & 0x10) != 0
        case "VP8 ":
            return false
        default:
            return false
        }
    }
}
