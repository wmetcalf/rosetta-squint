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

final class DCTTests: XCTestCase {
    private struct DCTCase: Decodable {
        let input: [Double]
        let output: [Double]
    }
    private struct DCTDoc: Decodable {
        let n: Int
        let cases: [String: DCTCase]
    }

    func testScipyReference() throws {
        let path = "\(TestKit.SPEC_DIR)/dct_cases.json"
        let data = try Data(contentsOf: URL(fileURLWithPath: path))
        let doc = try JSONDecoder().decode(DCTDoc.self, from: data)
        let tol = 1e-9
        for (name, c) in doc.cases {
            let got = dct1d(c.input)
            XCTAssertEqual(got.count, doc.n, "\(name) length")
            for k in 0..<doc.n {
                XCTAssertEqual(got[k], c.output[k], accuracy: tol, "\(name) k=\(k)")
            }
        }
    }

    func testArangeFirstOutput() {
        var x = [Double](repeating: 0, count: 32)
        for i in 0..<32 { x[i] = Double(i) }
        let y = dct1d(x)
        XCTAssertEqual(y[0], 992.0, accuracy: 1e-9)
    }
}

final class HaarTests: XCTestCase {
    private struct Bands: Decodable {
        let cA: [[Double]]
        let cH: [[Double]]
        let cV: [[Double]]
        let cD: [[Double]]
    }
    private struct MultiLevel: Decodable {
        let cA: [[Double]]
        let reconstructed: [[Double]]
    }
    private struct HaarDoc: Decodable {
        let input: [[Double]]
        let single_level: Bands
        let multi_level_4: MultiLevel
    }

    private let tol = 1e-12

    private func assertClose(_ expected: [[Double]], _ actual: [[Double]], _ label: String, file: StaticString = #file, line: UInt = #line) {
        XCTAssertEqual(actual.count, expected.count, "\(label) rows", file: file, line: line)
        for y in 0..<expected.count {
            XCTAssertEqual(actual[y].count, expected[y].count, "\(label) row \(y) cols", file: file, line: line)
            for x in 0..<expected[y].count {
                XCTAssertEqual(actual[y][x], expected[y][x], accuracy: tol, "\(label) (\(y),\(x))", file: file, line: line)
            }
        }
    }

    private func loadHaar() throws -> HaarDoc {
        let path = "\(TestKit.SPEC_DIR)/haar_cases.json"
        let data = try Data(contentsOf: URL(fileURLWithPath: path))
        return try JSONDecoder().decode(HaarDoc.self, from: data)
    }

    func testSingleLevel() throws {
        let doc = try loadHaar()
        let res = dwt2(doc.input)
        assertClose(doc.single_level.cA, res.cA, "cA")
        assertClose(doc.single_level.cH, res.cH, "cH")
        assertClose(doc.single_level.cV, res.cV, "cV")
        assertClose(doc.single_level.cD, res.cD, "cD")
    }

    func testMultiLevelAndReconstruction() throws {
        let doc = try loadHaar()
        let dec = wavedec2(doc.input, level: 4)
        XCTAssertEqual(dec.cA.count, 1)
        XCTAssertEqual(dec.cA[0].count, 1)
        assertClose(doc.multi_level_4.cA, dec.cA, "multi cA")
        let recon = waverec2(dec)
        assertClose(doc.multi_level_4.reconstructed, recon, "reconstructed")
        assertClose(doc.input, recon, "round-trip == input")
    }

    func testZeroLLRemovesDC() {
        let x: [[Double]] = Array(repeating: [7.5, 7.5, 7.5, 7.5], count: 4)
        var dec = wavedec2(x, level: 2)
        dec.cA[0][0] = 0
        let recon = waverec2(dec)
        for y in 0..<4 {
            for xCol in 0..<4 {
                XCTAssertEqual(abs(recon[y][xCol]), 0, accuracy: tol)
            }
        }
    }
}
