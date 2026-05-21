import Foundation
import Cjpeg

internal enum JPEGDecoder {
    static func decode(bytes: [UInt8]) throws -> DecodedImage {
        // Init TurboJPEG decompressor
        guard let handle = tjInitDecompress() else {
            throw DecodeError.corruptInput(format: .jpeg, detail: "tjInitDecompress failed")
        }
        defer { tjDestroy(handle) }

        // Read header to get width, height, subsamp, colorspace
        var width: Int32 = 0
        var height: Int32 = 0
        var subsamp: Int32 = 0
        var colorspace: Int32 = 0

        let headerRet = bytes.withUnsafeBufferPointer { buf -> Int32 in
            tjDecompressHeader3(
                handle,
                buf.baseAddress,
                UInt(buf.count),
                &width,
                &height,
                &subsamp,
                &colorspace
            )
        }
        if headerRet != 0 {
            let msg = String(cString: tjGetErrorStr2(handle))
            throw DecodeError.corruptInput(format: .jpeg, detail: "tjDecompressHeader3: \(msg)")
        }

        // Reject CMYK / YCCK (colorspace is an Int32 from the C enum)
        if colorspace == Int32(TJCS_CMYK.rawValue) || colorspace == Int32(TJCS_YCCK.rawValue) {
            throw DecodeError.unsupportedFeature(format: .jpeg, feature: "CMYK color space")
        }

        // Decompress to RGB (3 bytes per pixel)
        let pixelFormat = Int32(TJPF_RGB.rawValue)
        let pixelSize = 3
        var out = [UInt8](repeating: 0, count: Int(width) * Int(height) * pixelSize)

        // TJFLAG_ACCURATEDCT = 4096 — uses JDCT_ISLOW (matches PIL)
        let TJFLAG_ACCURATEDCT: Int32 = 4096

        let decodeRet = bytes.withUnsafeBufferPointer { buf in
            out.withUnsafeMutableBufferPointer { outBuf in
                tjDecompress2(
                    handle,
                    buf.baseAddress,
                    UInt(buf.count),
                    outBuf.baseAddress,
                    width,
                    0,  // pitch = 0 → default (width * pixelSize)
                    height,
                    pixelFormat,
                    TJFLAG_ACCURATEDCT
                )
            }
        }
        if decodeRet != 0 {
            let msg = String(cString: tjGetErrorStr2(handle))
            throw DecodeError.corruptInput(format: .jpeg, detail: "tjDecompress2: \(msg)")
        }

        return DecodedImage(
            width: Int(width),
            height: Int(height),
            data: out,
            channels: .rgb,
            format: .jpeg
        )
    }
}
