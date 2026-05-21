import XCTest
@testable import RosettaImageDecode

final class Group3DetectionTests: XCTestCase {
    func testDetectsAllValidBmp() throws {
        for rel in try TestKit.listValidFixtures("bmp") {
            let bytes = try TestKit.readFixture(rel)
            XCTAssertEqual(Decoder.detectFormat(bytes), .bmp, rel)
        }
    }

    func testRejectsBadSignature() throws {
        let bytes = try TestKit.readFixture("bmp/invalid/bad-signature.bmp")
        XCTAssertNil(Decoder.detectFormat(bytes))
    }

    func testSupportedFormatsContainsBmp() {
        XCTAssertTrue(Decoder.supportedFormats().contains(.bmp))
    }
}

extension Group3DetectionTests {
    func testDetectsAllValidPng() throws {
        for rel in try TestKit.listValidFixtures("png") {
            let bytes = try TestKit.readFixture(rel)
            XCTAssertEqual(Decoder.detectFormat(bytes), .png, rel)
        }
    }

    func testSupportedFormatsContainsPng() {
        XCTAssertTrue(Decoder.supportedFormats().contains(.png))
    }
}

extension Group3DetectionTests {
    func testDetectsAllValidGif() throws {
        for rel in try TestKit.listValidFixtures("gif") {
            let bytes = try TestKit.readFixture(rel)
            XCTAssertEqual(Decoder.detectFormat(bytes), .gif, rel)
        }
    }

    func testRejectsGifBadMagic() throws {
        let bytes = try TestKit.readFixture("gif/invalid/bad-magic.gif")
        XCTAssertNil(Decoder.detectFormat(bytes))
    }

    func testSupportedFormatsContainsGif() {
        XCTAssertTrue(Decoder.supportedFormats().contains(.gif))
    }
}
