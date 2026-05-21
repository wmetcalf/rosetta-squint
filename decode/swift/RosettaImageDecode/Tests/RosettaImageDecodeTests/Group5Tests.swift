import XCTest
@testable import RosettaImageDecode

final class Group5InvariantsTests: XCTestCase {
    func testAllDecodedImagesHaveValidShape() throws {
        for rel in try TestKit.listValidFixtures("bmp") {
            let bytes = try TestKit.readFixture(rel)
            let img = try Decoder.decode(bytes)
            XCTAssertGreaterThan(img.width, 0, rel)
            XCTAssertGreaterThan(img.height, 0, rel)
            XCTAssertEqual(img.format, .bmp, rel)
            XCTAssertEqual(img.data.count, img.width * img.height * img.channels.bytesPerPixel, rel)
        }
    }

    func testSupportedFormatsContainsBmpAndPng() {
        let supported = Decoder.supportedFormats()
        XCTAssertEqual(supported.count, 2)
        XCTAssertTrue(supported.contains(.bmp))
        XCTAssertTrue(supported.contains(.png))
    }
}

extension Group5InvariantsTests {
    func testAllDecodedPngImagesHaveValidShape() throws {
        for rel in try TestKit.listValidFixtures("png") {
            let bytes = try TestKit.readFixture(rel)
            let img = try Decoder.decode(bytes)
            XCTAssertGreaterThan(img.width, 0, rel)
            XCTAssertGreaterThan(img.height, 0, rel)
            XCTAssertEqual(img.format, .png, rel)
            XCTAssertEqual(img.data.count, img.width * img.height * img.channels.bytesPerPixel, rel)
        }
    }
}
