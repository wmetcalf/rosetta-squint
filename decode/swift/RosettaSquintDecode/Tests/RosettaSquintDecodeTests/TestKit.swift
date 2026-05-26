import Foundation
import XCTest
@testable import RosettaSquintDecode

enum TestKit {
    static let SPEC_DIR = "../../spec"

    struct DecodedGolden {
        let width: Int
        let height: Int
        let channels: Int
        let pixels: [UInt8]
    }

    static func readFixture(_ rel: String) throws -> [UInt8] {
        let path = "\(SPEC_DIR)/fixtures/\(rel)"
        let data = try Data(contentsOf: URL(fileURLWithPath: path))
        return Array(data)
    }

    static func readGolden(_ fixtureRel: String) throws -> DecodedGolden {
        let path = "\(SPEC_DIR)/decoded/\(fixtureRel).bin"
        let data = try Data(contentsOf: URL(fileURLWithPath: path))
        guard data.count >= 12 else {
            throw NSError(domain: "TestKit", code: 1,
                userInfo: [NSLocalizedDescriptionKey: "golden \(fixtureRel) too short"])
        }
        let bytes = Array(data)
        let width    = Int(UInt32(bytes[0]) | (UInt32(bytes[1]) << 8) | (UInt32(bytes[2]) << 16) | (UInt32(bytes[3]) << 24))
        let height   = Int(UInt32(bytes[4]) | (UInt32(bytes[5]) << 8) | (UInt32(bytes[6]) << 16) | (UInt32(bytes[7]) << 24))
        let channels = Int(bytes[8])
        let pixels   = Array(bytes[12...])
        return DecodedGolden(width: width, height: height, channels: channels, pixels: pixels)
    }

    static func listValidFixtures(_ format: String) throws -> [String] {
        let dir = "\(SPEC_DIR)/fixtures/\(format)/valid"
        let url = URL(fileURLWithPath: dir)
        let items = try FileManager.default.contentsOfDirectory(at: url, includingPropertiesForKeys: nil)
        // Accept both "jpeg"/"jpg" and "tiff"/"tif" alternate extensions
        let validExtensions: Set<String>
        switch format {
        case "jpeg": validExtensions = [format, "jpg"]
        case "tiff": validExtensions = [format, "tif"]
        case "heic": validExtensions = [format, "heif"]
        default: validExtensions = [format]
        }
        return items
            .filter { validExtensions.contains($0.pathExtension) }
            .map { "\(format)/valid/\($0.lastPathComponent)" }
            .sorted()
    }

    struct ExpectedError: Decodable {
        let format: String?
        let expected_kind: String
        let expected_detail_substring: String
    }

    private struct ErrorsDoc: Decodable {
        let fixtures: [String: ExpectedError]
    }

    static func readErrors() throws -> [String: ExpectedError] {
        let path = "\(SPEC_DIR)/errors.json"
        let data = try Data(contentsOf: URL(fileURLWithPath: path))
        let doc = try JSONDecoder().decode(ErrorsDoc.self, from: data)
        return doc.fixtures
    }
}
