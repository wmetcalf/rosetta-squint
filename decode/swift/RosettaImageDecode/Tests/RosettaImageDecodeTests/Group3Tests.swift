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
