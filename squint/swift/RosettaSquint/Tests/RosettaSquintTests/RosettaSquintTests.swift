import Foundation
import XCTest
import RosettaSquint
import RosettaSquintHash

/// Path to the shared hash/spec/fixtures directory, relative to the package root.
private let FIXTURES_DIR = "../../../hash/spec/fixtures"

final class PhashConvenienceTests: XCTestCase {
    // 1. phash(at:hashSize:) returns a Hash and matches the known golden.
    func testPhashAtPath_artSaturated() throws {
        let path = "\(FIXTURES_DIR)/art-saturated-512.png"
        let h = try RosettaSquint.phash(at: path, hashSize: 8)
        XCTAssertEqual(h.hex, "8080000000808000")
    }

    func testPhashAtPath_photo1024() throws {
        let path = "\(FIXTURES_DIR)/photo-1024.png"
        let h = try RosettaSquint.phash(at: path, hashSize: 8)
        XCTAssertEqual(h.hex, "ec9533242312bf73")
    }

    func testPhashAtPath_peppers() throws {
        let path = "\(FIXTURES_DIR)/peppers.png"
        let h = try RosettaSquint.phash(at: path, hashSize: 8)
        XCTAssertEqual(h.hex, "92a71cdc79d980e3")
    }

    // 2. phash(at:) == phash(bytes:) for the same file.
    func testPhashAtPathEqualsPhashFromBytes() throws {
        let path = "\(FIXTURES_DIR)/art-saturated-512.png"
        let url = URL(fileURLWithPath: path)
        let bytes = Array(try Data(contentsOf: url))
        let hashFromPath = try RosettaSquint.phash(at: path, hashSize: 8)
        let hashFromBytes = try RosettaSquint.phash(bytes: bytes, hashSize: 8)
        XCTAssertEqual(hashFromPath, hashFromBytes)
    }

    func testPhashBytesEqualsPathForPhoto() throws {
        let path = "\(FIXTURES_DIR)/photo-1024.png"
        let url = URL(fileURLWithPath: path)
        let bytes = Array(try Data(contentsOf: url))
        let hashFromPath = try RosettaSquint.phash(at: path, hashSize: 8)
        let hashFromBytes = try RosettaSquint.phash(bytes: bytes, hashSize: 8)
        XCTAssertEqual(hashFromPath, hashFromBytes)
    }

    // 3. phash(at:) == RosettaSquintHash.phash on RosettaSquint.decodeFile(at:) directly.
    func testPhashAtPathMatchesDirectPipeline() throws {
        let path = "\(FIXTURES_DIR)/peppers.png"
        let squintHash = try RosettaSquint.phash(at: path, hashSize: 8)
        let img = try RosettaSquint.decodeFile(at: path)
        let directHash = try RosettaSquintHash.phash(img, hashSize: 8)
        XCTAssertEqual(squintHash, directHash)
    }
}

final class AverageHashConvenienceTests: XCTestCase {
    func testAverageHashAtPath_artSaturated() throws {
        let path = "\(FIXTURES_DIR)/art-saturated-512.png"
        let h = try RosettaSquint.averageHash(at: path, hashSize: 8)
        XCTAssertEqual(h.hex, "0000ffffffff0000")
    }

    func testAverageHashAtPath_photo1024() throws {
        let path = "\(FIXTURES_DIR)/photo-1024.png"
        let h = try RosettaSquint.averageHash(at: path, hashSize: 8)
        XCTAssertEqual(h.hex, "c3e7e7c3c3c181c3")
    }

    func testAverageHashAtPath_peppers() throws {
        let path = "\(FIXTURES_DIR)/peppers.png"
        let h = try RosettaSquint.averageHash(at: path, hashSize: 8)
        XCTAssertEqual(h.hex, "9f172786e71f1e00")
    }

    func testAverageHashBytesEqualsPath() throws {
        let path = "\(FIXTURES_DIR)/art-saturated-512.png"
        let url = URL(fileURLWithPath: path)
        let bytes = Array(try Data(contentsOf: url))
        XCTAssertEqual(
            try RosettaSquint.averageHash(at: path, hashSize: 8),
            try RosettaSquint.averageHash(bytes: bytes, hashSize: 8)
        )
    }
}

final class DhashConvenienceTests: XCTestCase {
    func testDhashAtPath_artSaturated() throws {
        let path = "\(FIXTURES_DIR)/art-saturated-512.png"
        let h = try RosettaSquint.dhash(at: path, hashSize: 8)
        XCTAssertEqual(h.hex, "0000000000000000")
    }

    func testDhashAtPath_photo1024() throws {
        let path = "\(FIXTURES_DIR)/photo-1024.png"
        let h = try RosettaSquint.dhash(at: path, hashSize: 8)
        XCTAssertEqual(h.hex, "0f0f0f0f0f0f0f0f")
    }

    func testDhashAtPath_peppers() throws {
        let path = "\(FIXTURES_DIR)/peppers.png"
        let h = try RosettaSquint.dhash(at: path, hashSize: 8)
        XCTAssertEqual(h.hex, "3a7ece1c9df4fcb9")
    }

    func testDhashBytesEqualsPath() throws {
        let path = "\(FIXTURES_DIR)/peppers.png"
        let url = URL(fileURLWithPath: path)
        let bytes = Array(try Data(contentsOf: url))
        XCTAssertEqual(
            try RosettaSquint.dhash(at: path, hashSize: 8),
            try RosettaSquint.dhash(bytes: bytes, hashSize: 8)
        )
    }
}

final class AdaptTests: XCTestCase {
    func testDecodeFilePipelineMatchesDirectHashCall() throws {
        let path = "\(FIXTURES_DIR)/art-saturated-512.png"
        let img = try RosettaSquint.decodeFile(at: path)
        let squintHash = try RosettaSquint.phash(at: path, hashSize: 8)
        let pipelineHash = try RosettaSquintHash.phash(img, hashSize: 8)
        XCTAssertEqual(squintHash, pipelineHash)
    }

    func testDecodeBytesMatchesDecodeFile() throws {
        let path = "\(FIXTURES_DIR)/photo-1024.png"
        let url = URL(fileURLWithPath: path)
        let bytes = Array(try Data(contentsOf: url))
        let fromFile = try RosettaSquint.decodeFile(at: path)
        let fromBytes = try RosettaSquint.decodeBytes(bytes)
        XCTAssertEqual(fromFile.width, fromBytes.width)
        XCTAssertEqual(fromFile.height, fromBytes.height)
        XCTAssertEqual(fromFile.data, fromBytes.data)
        XCTAssertEqual(fromFile.channels, fromBytes.channels)
    }
}

final class IOErrorTests: XCTestCase {
    func testMissingFileThrowsRSError() {
        XCTAssertThrowsError(try RosettaSquint.phash(at: "/nonexistent/file.png", hashSize: 8)) { err in
            guard case RosettaSquint.RSError.io = err else {
                XCTFail("expected RSError.io, got \(err)")
                return
            }
        }
    }
}
