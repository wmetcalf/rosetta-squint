import Foundation
import Ctiff

internal enum TIFFDecoder {
    static func decode(bytes: [UInt8]) throws -> DecodedImage {
        // Write to a temp file; libtiff's simplest API works best with a path on Linux.
        let tempURL = FileManager.default.temporaryDirectory
            .appendingPathComponent("tiff-\(UUID().uuidString).tif")
        defer { try? FileManager.default.removeItem(at: tempURL) }
        try Data(bytes).write(to: tempURL)

        guard let handle = TIFFOpen(tempURL.path, "r") else {
            throw DecodeError.corruptInput(format: .tiff, detail: "TIFFOpen failed")
        }
        defer { TIFFClose(handle) }

        var width: UInt32 = 0
        var height: UInt32 = 0
        TIFFGetField_UInt32(handle, UInt32(TIFFTAG_IMAGEWIDTH), &width)
        TIFFGetField_UInt32(handle, UInt32(TIFFTAG_IMAGELENGTH), &height)

        guard width > 0, height > 0 else {
            throw DecodeError.corruptInput(format: .tiff, detail: "zero dimensions in TIFF")
        }

        let pixels = Int(width) * Int(height)
        // TIFFReadRGBAImageOriented with ORIENTATION_TOPLEFT delivers rows top-down,
        // so no manual flip is needed.
        var raster = [UInt32](repeating: 0, count: pixels)

        let ok = raster.withUnsafeMutableBufferPointer { buf -> Int32 in
            TIFFReadRGBAImageOriented(
                handle, width, height, buf.baseAddress,
                Int32(ORIENTATION_TOPLEFT), 0
            )
        }
        if ok == 0 {
            throw DecodeError.corruptInput(format: .tiff, detail: "TIFFReadRGBAImageOriented failed")
        }

        // Each UInt32 is packed as ABGR (little-endian byte order: R, G, B, A).
        // Extract R, G, B in order.
        let w = Int(width)
        let h = Int(height)
        var out = [UInt8](repeating: 0, count: pixels * 3)
        for i in 0..<pixels {
            let pixel = raster[i]
            out[i * 3]     = UInt8(pixel & 0xFF)          // R
            out[i * 3 + 1] = UInt8((pixel >> 8) & 0xFF)   // G
            out[i * 3 + 2] = UInt8((pixel >> 16) & 0xFF)  // B
        }

        return DecodedImage(
            width: w,
            height: h,
            data: out,
            channels: .rgb,
            format: .tiff
        )
    }
}
