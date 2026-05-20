import Foundation
import XCTest
@testable import RosettaImageHash

final class PILGrayTests: XCTestCase {
    private struct GrayscaleCase: Decodable {
        let rgb: [Int]
        let L: Int
    }
    private struct GrayscaleDoc: Decodable {
        let cases: [GrayscaleCase]
    }

    func testAllGrayscaleCases() throws {
        let path = "\(TestKit.SPEC_DIR)/grayscale_cases.json"
        let data = try Data(contentsOf: URL(fileURLWithPath: path))
        let doc = try JSONDecoder().decode(GrayscaleDoc.self, from: data)
        XCTAssertEqual(doc.cases.count, 30)
        for c in doc.cases {
            let got = toGray(c.rgb[0], c.rgb[1], c.rgb[2])
            XCTAssertEqual(got, c.L, "rgb=\(c.rgb)")
        }
    }
}
