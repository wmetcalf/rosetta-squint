import Foundation
import XCTest
@testable import RosettaImageHash

enum TestKit {
    /// Path to the shared /spec/ directory, relative to the package root (where `swift test` is invoked).
    static let SPEC_DIR = "../../spec"

    struct AlgorithmCase {
        let fixture: String
        let size: Int
        let hex: String
    }

    private struct GoldensJSON: Decodable {
        struct AlgorithmEntry: Decodable {
            let fixtures: [String: [String: String?]]
        }
        let algorithms: [String: AlgorithmEntry]
    }

    private static var goldensCache: GoldensJSON?

    private static func loadGoldens() throws -> GoldensJSON {
        if let cached = goldensCache { return cached }
        let path = "\(SPEC_DIR)/goldens.json"
        let data = try Data(contentsOf: URL(fileURLWithPath: path))
        let decoded = try JSONDecoder().decode(GoldensJSON.self, from: data)
        goldensCache = decoded
        return decoded
    }

    /// Returns (fixture, size, expectedHex) triples for the algorithm, skipping null hex (small fixtures for whash).
    static func algorithmCases(_ algorithm: String) throws -> [AlgorithmCase] {
        let g = try loadGoldens()
        guard let entry = g.algorithms[algorithm] else {
            XCTFail("algorithm \(algorithm) not in goldens.json")
            return []
        }
        var out: [AlgorithmCase] = []
        for fixture in entry.fixtures.keys.sorted() {
            guard let sizes = entry.fixtures[fixture] else { continue }
            for sizeStr in sizes.keys.sorted() {
                guard let hex = sizes[sizeStr], let h = hex else { continue }
                guard let sz = Int(sizeStr) else { continue }
                out.append(AlgorithmCase(fixture: fixture, size: sz, hex: h))
            }
        }
        return out
    }

    /// Reads spec/decoded/<name>.rgb.bin into an RGBImage with channels = .rgb.
    static func loadPredecoded(_ name: String) throws -> RGBImage {
        let path = "\(SPEC_DIR)/decoded/\(name).rgb.bin"
        let data = try Data(contentsOf: URL(fileURLWithPath: path))
        guard data.count >= 8 else {
            throw NSError(domain: "TestKit", code: 1, userInfo: [NSLocalizedDescriptionKey: "predecoded \(name) too short"])
        }
        let width = Int(readUInt32LE(data, offset: 0))
        let height = Int(readUInt32LE(data, offset: 4))
        let expected = 8 + width * height * 3
        guard data.count == expected else {
            throw NSError(domain: "TestKit", code: 1, userInfo: [NSLocalizedDescriptionKey: "predecoded \(name) length mismatch: got \(data.count), expected \(expected)"])
        }
        let bytes = [UInt8](data[8..<data.count])
        return RGBImage(width: width, height: height, data: bytes, channels: .rgb)
    }

    struct LanczosCase {
        let srcW: Int
        let srcH: Int
        let dstW: Int
        let dstH: Int
        let src: [UInt8]
        let dst: [UInt8]
    }

    /// Reads spec/lanczos_cases/<name>.bin into typed buffers.
    static func loadLanczosCase(_ name: String) throws -> LanczosCase {
        let path = "\(SPEC_DIR)/lanczos_cases/\(name).bin"
        let data = try Data(contentsOf: URL(fileURLWithPath: path))
        guard data.count >= 16 else {
            throw NSError(domain: "TestKit", code: 1, userInfo: [NSLocalizedDescriptionKey: "lanczos case \(name) too short"])
        }
        let srcW = Int(readUInt32LE(data, offset: 0))
        let srcH = Int(readUInt32LE(data, offset: 4))
        let dstW = Int(readUInt32LE(data, offset: 8))
        let dstH = Int(readUInt32LE(data, offset: 12))
        let expected = 16 + srcW * srcH + dstW * dstH
        guard data.count == expected else {
            throw NSError(domain: "TestKit", code: 1, userInfo: [NSLocalizedDescriptionKey: "lanczos \(name) length mismatch"])
        }
        let src = [UInt8](data[16..<(16 + srcW * srcH)])
        let dst = [UInt8](data[(16 + srcW * srcH)..<data.count])
        return LanczosCase(srcW: srcW, srcH: srcH, dstW: dstW, dstH: dstH, src: src, dst: dst)
    }

    private static func readUInt32LE(_ data: Data, offset: Int) -> UInt32 {
        return UInt32(data[offset])
            | (UInt32(data[offset + 1]) << 8)
            | (UInt32(data[offset + 2]) << 16)
            | (UInt32(data[offset + 3]) << 24)
    }
}
