import Foundation

public let MAX_PIXELS: Int = 256 * 1024 * 1024  // 268_435_456

internal enum Limits {
    static func checkDimensions(width: Int, height: Int, format: Format) throws {
        guard width > 0, height > 0 else {
            throw DecodeError.corruptInput(format: format, detail: "non-positive dimensions \(width)x\(height)")
        }
        let (pixels, overflow) = width.multipliedReportingOverflow(by: height)
        if overflow || pixels > MAX_PIXELS {
            throw DecodeError.imageTooLarge(format: format,
                detail: "declared dimensions \(width)x\(height) = \(overflow ? "overflow" : String(pixels)) pixels exceeds MAX_PIXELS = \(MAX_PIXELS)")
        }
    }
}
