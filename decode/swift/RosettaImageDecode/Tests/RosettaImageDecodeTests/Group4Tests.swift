import XCTest
@testable import RosettaImageDecode

final class Group4ErrorTests: XCTestCase {
    func testInvalidBmpFixtures() throws {
        let errors = try TestKit.readErrors()
        var failures: [String] = []
        for (key, expected) in errors {
            guard key.hasPrefix("bmp/") else { continue }
            let input = try TestKit.readFixture(key)
            do {
                _ = try Decoder.decode(input)
                failures.append("\(key): decode succeeded, expected \(expected.expected_kind)")
            } catch let e as DecodeError {
                if e.kindString != expected.expected_kind {
                    failures.append("\(key): kind \(e.kindString) != \(expected.expected_kind)")
                    continue
                }
                if !expected.expected_detail_substring.isEmpty
                    && !e.detail.contains(expected.expected_detail_substring) {
                    failures.append("\(key): detail '\(e.detail)' does not contain '\(expected.expected_detail_substring)'")
                }
            } catch {
                failures.append("\(key): unexpected error type \(type(of: error)): \(error)")
            }
        }
        if !failures.isEmpty {
            XCTFail("\(failures.count) Group-4 failures:\n  \(failures.joined(separator: "\n  "))")
        }
    }
}

extension Group4ErrorTests {
    func testInvalidPngFixtures() throws {
        let errors = try TestKit.readErrors()
        var failures: [String] = []
        for (key, expected) in errors {
            guard key.hasPrefix("png/") else { continue }
            let input = try TestKit.readFixture(key)
            do {
                _ = try Decoder.decode(input)
                failures.append("\(key): decode succeeded, expected \(expected.expected_kind)")
            } catch let e as DecodeError {
                if e.kindString != expected.expected_kind {
                    failures.append("\(key): kind \(e.kindString) != \(expected.expected_kind)")
                    continue
                }
                if !expected.expected_detail_substring.isEmpty && !e.detail.contains(expected.expected_detail_substring) {
                    failures.append("\(key): detail '\(e.detail)' does not contain '\(expected.expected_detail_substring)'")
                }
            } catch {
                failures.append("\(key): unexpected error type \(type(of: error)): \(error)")
            }
        }
        if !failures.isEmpty {
            XCTFail("\(failures.count) Group-4 PNG failures:\n  \(failures.joined(separator: "\n  "))")
        }
    }
}

extension Group4ErrorTests {
    func testInvalidGifFixtures() throws {
        let errors = try TestKit.readErrors()
        var failures: [String] = []
        for (key, expected) in errors {
            guard key.hasPrefix("gif/") else { continue }
            let input = try TestKit.readFixture(key)
            do {
                _ = try Decoder.decode(input)
                failures.append("\(key): decode succeeded, expected \(expected.expected_kind)")
            } catch let e as DecodeError {
                if e.kindString != expected.expected_kind {
                    failures.append("\(key): kind \(e.kindString) != \(expected.expected_kind)")
                    continue
                }
                if !expected.expected_detail_substring.isEmpty && !e.detail.contains(expected.expected_detail_substring) {
                    failures.append("\(key): detail '\(e.detail)' does not contain '\(expected.expected_detail_substring)'")
                }
            } catch {
                failures.append("\(key): unexpected error type \(type(of: error)): \(error)")
            }
        }
        if !failures.isEmpty {
            XCTFail("\(failures.count) Group-4 GIF failures:\n  \(failures.joined(separator: "\n  "))")
        }
    }
}
