import Foundation
import XCTest
@testable import RosettaSquintHash

final class Group3PNGTests: XCTestCase {
    private func loadExemptions() -> Set<String> {
        var exempt = Set<String>()
        let path = "DECODER_NOTES.md"
        guard let text = try? String(contentsOf: URL(fileURLWithPath: path), encoding: .utf8) else {
            return exempt
        }
        for line in text.split(separator: "\n", omittingEmptySubsequences: false) {
            if let dashIdx = line.firstIndex(of: "—") {
                let name = line[..<dashIdx].trimmingCharacters(in: .whitespaces)
                if name.hasSuffix(".png") {
                    exempt.insert(name)
                }
            }
        }
        return exempt
    }

    private func decodePNGFromSpec(_ fixture: String) throws -> RGBImage {
        let path = "\(TestKit.SPEC_DIR)/fixtures/\(fixture)"
        let data = try Data(contentsOf: URL(fileURLWithPath: path))
        return try decodePNG([UInt8](data))
    }

    func testAllAlgorithmsViaPNGDecode() throws {
        let exempt = loadExemptions()
        var failures: [String] = []

        let algorithms: [(String, (RGBImage, Int) throws -> Hash)] = [
            ("average_hash", { try averageHash($0, hashSize: $1) }),
            ("dhash",        { try dhash($0, hashSize: $1) }),
            ("phash",        { try phash($0, hashSize: $1) }),
            ("whash_haar",   { try whashHaar($0, hashSize: $1) }),
            ("colorhash",    { try colorhash($0, binbits: $1) }),
        ]

        for (name, compute) in algorithms {
            for c in try TestKit.algorithmCases(name) {
                if exempt.contains(c.fixture) { continue }
                let img = try decodePNGFromSpec(c.fixture)
                let h = try compute(img, c.size)
                if h.hex != c.hex {
                    failures.append("\(name) fixture=\(c.fixture) size=\(c.size): got \(h.hex) want \(c.hex)")
                }
            }
        }

        if !failures.isEmpty {
            XCTFail("\(failures.count) Group-3 failures:\n  " + failures.joined(separator: "\n  "))
        }
    }
}
