import XCTest
@testable import RosettaSquintDecode

/// D-M1: BMP biClrUsed must be clamped to bit-depth max so an attacker-controlled
/// value (e.g. 0x10000000 = 256M entries) cannot cause excessive palette allocation.
/// Swift's Int64-bounded check already prevents true overflow, but the palette
/// array allocation can still request as much as the file size permits.
final class Group1BmpClrUsedTests: XCTestCase {

    func testClampsOversizedClrUsedBomb() throws {
        // Build an 8-bit BMP in memory with biClrUsed = 0x10000000.
        // The actual on-disk palette is 256 entries (1024 bytes). With the clamp,
        // decode succeeds reading only those 1024 bytes.
        let bytes = buildPal8BmpWithClrUsed(0x10000000)
        let img = try Decoder.decode(bytes)
        XCTAssertEqual(img.width, 2)
        XCTAssertEqual(img.height, 2)
        // 2x2 RGB = 12 bytes — confirms no excessive allocation occurred.
        XCTAssertEqual(img.data.count, 12)
    }

    func testClampsClrUsed257to256() throws {
        let bytes = buildPal8BmpWithClrUsed(257)
        let img = try Decoder.decode(bytes)
        XCTAssertEqual(img.width, 2)
        XCTAssertEqual(img.height, 2)
        XCTAssertEqual(img.data.count, 12)
    }

    func testDecodesWithClrUsedZero() throws {
        let bytes = buildPal8BmpWithClrUsed(0)
        let img = try Decoder.decode(bytes)
        XCTAssertEqual(img.width, 2)
        XCTAssertEqual(img.height, 2)
    }

    func testDecodesWithClrUsedUnderMax() throws {
        let bytes = buildPal8BmpWithClrUsed(100)
        let img = try Decoder.decode(bytes)
        XCTAssertEqual(img.width, 2)
        XCTAssertEqual(img.height, 2)
    }

    func testClampEntryCountBoundaries() {
        XCTAssertEqual(BMPDecoder.clampEntryCount(0, bitDepth: 1), 2)
        XCTAssertEqual(BMPDecoder.clampEntryCount(100, bitDepth: 1), 2)
        XCTAssertEqual(BMPDecoder.clampEntryCount(1, bitDepth: 1), 1)
        XCTAssertEqual(BMPDecoder.clampEntryCount(0, bitDepth: 4), 16)
        XCTAssertEqual(BMPDecoder.clampEntryCount(0x40000000, bitDepth: 4), 16)
        XCTAssertEqual(BMPDecoder.clampEntryCount(8, bitDepth: 4), 8)
        XCTAssertEqual(BMPDecoder.clampEntryCount(0, bitDepth: 8), 256)
        XCTAssertEqual(BMPDecoder.clampEntryCount(0x10000000, bitDepth: 8), 256)
        XCTAssertEqual(BMPDecoder.clampEntryCount(257, bitDepth: 8), 256)
        XCTAssertEqual(BMPDecoder.clampEntryCount(100, bitDepth: 8), 100)
        XCTAssertEqual(BMPDecoder.clampEntryCount(-1, bitDepth: 8), 256)
    }

    // Build a minimal 2x2 8-bit paletted BMP whose header declares the supplied
    // biClrUsed, while the actual on-disk palette is exactly 256 entries (1024
    // bytes). A clamped decoder reads only those 1024 bytes; an un-clamped
    // decoder would attempt to read clrUsed*4 bytes.
    private func buildPal8BmpWithClrUsed(_ clrUsed: UInt32) -> [UInt8] {
        let width = 2
        let height = 2
        let paletteBytes = 256 * 4
        let rowStride = ((width + 3) / 4) * 4 // = 4
        let pixelDataSize = rowStride * height // = 8
        let pixelDataOffset = 14 + 40 + paletteBytes // = 1078
        let fileSize = pixelDataOffset + pixelDataSize

        var b = [UInt8](repeating: 0, count: fileSize)

        func putU16(_ off: Int, _ v: UInt16) {
            b[off]   = UInt8(v & 0xFF)
            b[off+1] = UInt8((v >> 8) & 0xFF)
        }
        func putU32(_ off: Int, _ v: UInt32) {
            b[off]   = UInt8(v & 0xFF)
            b[off+1] = UInt8((v >> 8) & 0xFF)
            b[off+2] = UInt8((v >> 16) & 0xFF)
            b[off+3] = UInt8((v >> 24) & 0xFF)
        }

        // BMP file header (14 bytes)
        b[0] = 0x42 // 'B'
        b[1] = 0x4D // 'M'
        putU32(2, UInt32(fileSize))
        // reserved fields (4-9) left zero
        putU32(10, UInt32(pixelDataOffset))
        // DIB BITMAPINFOHEADER (40 bytes)
        putU32(14, 40)            // biSize
        putU32(18, UInt32(width))
        putU32(22, UInt32(height))
        putU16(26, 1)             // planes
        putU16(28, 8)             // bitCount
        putU32(30, 0)             // compression BI_RGB
        putU32(34, UInt32(pixelDataSize)) // biSizeImage
        putU32(38, 2835)          // x ppm
        putU32(42, 2835)          // y ppm
        putU32(46, clrUsed)       // ATTACKER-CONTROLLED
        putU32(50, 0)             // biClrImportant
        // Palette: 256 entries of (B, G, R, reserved) starting at offset 54
        for i in 0..<256 {
            let off = 54 + i * 4
            b[off]   = UInt8(i)
            b[off+1] = UInt8(i)
            b[off+2] = UInt8(i)
            b[off+3] = 0
        }
        // Pixel data starts at pixelDataOffset = 1078
        b[pixelDataOffset + 0] = 0
        b[pixelDataOffset + 1] = 1
        b[pixelDataOffset + 4] = 2
        b[pixelDataOffset + 5] = 3
        return b
    }
}
