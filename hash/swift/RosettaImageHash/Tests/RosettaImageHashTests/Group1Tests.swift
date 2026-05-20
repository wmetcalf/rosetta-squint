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

final class PILHSVTests: XCTestCase {
    private struct HSVCase: Decodable {
        let rgb: [Int]
        let hsv: [Int]
    }
    private struct HSVDoc: Decodable {
        let cases: [HSVCase]
    }

    func testAllHSVCases() throws {
        let path = "\(TestKit.SPEC_DIR)/hsv_cases.json"
        let data = try Data(contentsOf: URL(fileURLWithPath: path))
        let doc = try JSONDecoder().decode(HSVDoc.self, from: data)
        XCTAssertEqual(doc.cases.count, 31)
        for c in doc.cases {
            let (h, s, v) = toHSV(c.rgb[0], c.rgb[1], c.rgb[2])
            XCTAssertEqual([h, s, v], c.hsv, "rgb=\(c.rgb)")
        }
    }

    func testNegativeHPreWrap() {
        let (h, s, v) = toHSV(200, 100, 150)
        XCTAssertEqual([h, s, v], [233, 127, 200])
    }

    func testHalfBoundaryFloor() {
        let (h, s, v) = toHSV(100, 150, 200)
        XCTAssertEqual([h, s, v], [148, 127, 200])
    }

    func testSaturation170Boundary() {
        let (_, s, _) = toHSV(255, 85, 85)
        XCTAssertEqual(s, 170)
    }
}

final class LanczosTests: XCTestCase {
    func testAllCases() throws {
        let names = [
            "downsample_64_to_32_gradient",
            "upsample_16_to_32_gradient",
            "identity_32_to_32_random",
            "asymmetric_64x48_to_32x24",
        ]
        for name in names {
            let c = try TestKit.loadLanczosCase(name)
            let got = lanczosResize(c.src, srcW: c.srcW, srcH: c.srcH, dstW: c.dstW, dstH: c.dstH)
            XCTAssertEqual(got.count, c.dstW * c.dstH, "\(name) length mismatch")
            for y in 0..<c.dstH {
                for x in 0..<c.dstW {
                    let i = y * c.dstW + x
                    XCTAssertEqual(got[i], c.dst[i], "\(name) pixel (\(y),\(x))")
                }
            }
        }
    }
}
