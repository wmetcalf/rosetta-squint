import Foundation
import XCTest
@testable import RosettaImageHash

final class Group2DHashTests: XCTestCase {
    func testByteExactAllFixturesAndSizes() throws {
        let cases = try TestKit.algorithmCases("dhash")
        var failures: [String] = []
        for c in cases {
            let img = try TestKit.loadPredecoded(c.fixture)
            let h = try dhash(img, hashSize: c.size)
            if h.hex != c.hex {
                failures.append("fixture=\(c.fixture) size=\(c.size) got=\(h.hex) want=\(c.hex)")
            }
        }
        if !failures.isEmpty {
            XCTFail("\(failures.count) failures:\n  " + failures.joined(separator: "\n  "))
        }
    }
}

final class Group2AverageHashTests: XCTestCase {
    func testByteExactAllFixturesAndSizes() throws {
        let cases = try TestKit.algorithmCases("average_hash")
        var failures: [String] = []
        for c in cases {
            let img = try TestKit.loadPredecoded(c.fixture)
            let h = try averageHash(img, hashSize: c.size)
            if h.hex != c.hex {
                failures.append("fixture=\(c.fixture) size=\(c.size) got=\(h.hex) want=\(c.hex)")
            }
        }
        if !failures.isEmpty {
            XCTFail("\(failures.count) failures:\n  " + failures.joined(separator: "\n  "))
        }
    }
}
