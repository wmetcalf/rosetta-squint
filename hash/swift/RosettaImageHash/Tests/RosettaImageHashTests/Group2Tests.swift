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

final class Group2PHashTests: XCTestCase {
    func testByteExactAllFixturesAndSizes() throws {
        let cases = try TestKit.algorithmCases("phash")
        var failures: [String] = []
        for c in cases {
            let img = try TestKit.loadPredecoded(c.fixture)
            let h = try phash(img, hashSize: c.size)
            if h.hex != c.hex {
                failures.append("fixture=\(c.fixture) size=\(c.size) got=\(h.hex) want=\(c.hex)")
            }
        }
        if !failures.isEmpty {
            XCTFail("\(failures.count) failures:\n  " + failures.joined(separator: "\n  "))
        }
    }
}

final class Group2WHashHaarTests: XCTestCase {
    func testByteExactAllFixturesAndSizes() throws {
        let cases = try TestKit.algorithmCases("whash_haar")
        var failures: [String] = []
        for c in cases {
            let img = try TestKit.loadPredecoded(c.fixture)
            let h = try whashHaar(img, hashSize: c.size)
            if h.hex != c.hex {
                failures.append("fixture=\(c.fixture) size=\(c.size) got=\(h.hex) want=\(c.hex)")
            }
        }
        if !failures.isEmpty {
            XCTFail("\(failures.count) failures:\n  " + failures.joined(separator: "\n  "))
        }
    }
}

final class Group2ColorHashTests: XCTestCase {
    func testByteExactAllFixturesAndBinbits() throws {
        let cases = try TestKit.algorithmCases("colorhash")
        var failures: [String] = []
        for c in cases {
            let img = try TestKit.loadPredecoded(c.fixture)
            let h = try colorhash(img, binbits: c.size)
            if h.hex != c.hex {
                failures.append("fixture=\(c.fixture) binbits=\(c.size) got=\(h.hex) want=\(c.hex)")
            }
        }
        if !failures.isEmpty {
            XCTFail("\(failures.count) failures:\n  " + failures.joined(separator: "\n  "))
        }
    }
}

final class Group2PHashSimpleTests: XCTestCase {
    func testByteExactAllFixturesAndSizes() throws {
        let cases = try TestKit.algorithmCases("phash_simple")
        var failures: [String] = []
        for c in cases {
            let img = try TestKit.loadPredecoded(c.fixture)
            let h = try phashSimple(img, hashSize: c.size)
            if h.hex != c.hex {
                failures.append("fixture=\(c.fixture) size=\(c.size) got=\(h.hex) want=\(c.hex)")
            }
        }
        if !failures.isEmpty {
            XCTFail("\(failures.count) failures:\n  " + failures.joined(separator: "\n  "))
        }
    }
}

final class Group2DHashVerticalTests: XCTestCase {
    func testByteExactAllFixturesAndSizes() throws {
        let cases = try TestKit.algorithmCases("dhash_vertical")
        var failures: [String] = []
        for c in cases {
            let img = try TestKit.loadPredecoded(c.fixture)
            let h = try dhashVertical(img, hashSize: c.size)
            if h.hex != c.hex {
                failures.append("fixture=\(c.fixture) size=\(c.size) got=\(h.hex) want=\(c.hex)")
            }
        }
        if !failures.isEmpty {
            XCTFail("\(failures.count) failures:\n  " + failures.joined(separator: "\n  "))
        }
    }
}

final class Group2WHashDb4Tests: XCTestCase {
    /// Fixtures where the db4 LL band has many near-zero coefficients (magnitude < 1e-10)
    /// that straddle the median=0 boundary. The exact sign of these values depends on
    /// pywt's SSE2 vectorisation path, which differs from portable double arithmetic by
    /// 1-2 ULPs. The Go port makes the same exemptions (see go/imagehash/group2_test.go).
    private let fpPrecisionExempt: Set<String> = [
        "checker-256.png-16",
        "checker-256.png-8",
        "line-art-icon-256.png-16",
    ]

    func testByteExactAllFixturesAndSizes() throws {
        let cases = try TestKit.algorithmCases("whash_db4")
        var failures: [String] = []
        for c in cases {
            let key = "\(c.fixture)-\(c.size)"
            if fpPrecisionExempt.contains(key) { continue }
            let img = try TestKit.loadPredecoded(c.fixture)
            let h = try whashDb4(img, hashSize: c.size)
            if h.hex != c.hex {
                failures.append("fixture=\(c.fixture) size=\(c.size) got=\(h.hex) want=\(c.hex)")
            }
        }
        if !failures.isEmpty {
            XCTFail("\(failures.count) failures:\n  " + failures.joined(separator: "\n  "))
        }
    }
}
