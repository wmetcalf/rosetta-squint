import Foundation
import XCTest
@testable import RosettaImageHash

final class Group4HexTests: XCTestCase {
	func testHexToHashRoundTrip() throws {
		let hex = "ffd7918181c9ffff"
		let h = try hexToHash(hex)
		XCTAssertEqual(h.bitCount, 64)
		XCTAssertEqual(h.hex, hex)
	}

	func testHexToFlathashRoundTrip() throws {
		let hex = "0123456789abcd"
		let h = try hexToFlathash(hex, hashSize: 4)
		XCTAssertEqual(h.bitCount, 14 * 4)
		XCTAssertEqual(h.hex, hex)
	}

	func testHexToHashRejectsNonSquare() {
		XCTAssertThrowsError(try hexToHash("12345")) { error in
			guard case .invalidHex = error as? ImageHashError else {
				XCTFail("expected .invalidHex, got \(error)")
				return
			}
		}
	}

	func testHexToHashRejectsInvalidChars() {
		XCTAssertThrowsError(try hexToHash("xyz!")) { error in
			guard case .invalidHex = error as? ImageHashError else {
				XCTFail("expected .invalidHex, got \(error)")
				return
			}
		}
	}

	func testRoundTripAllZeros() throws {
		let hex = "0000000000000000"
		XCTAssertEqual(try hexToHash(hex).hex, hex)
	}

	// Spec: lowercase hex only — matches Rust/Go/Java/JS port behavior.
	func testHexToHashRejectsUppercase() {
		XCTAssertThrowsError(try hexToHash("ABCD")) { error in
			guard case .invalidHex = error as? ImageHashError else {
				XCTFail("expected .invalidHex for uppercase, got \(error)")
				return
			}
		}
	}

	func testHexToHashRejectsMixedCase() {
		XCTAssertThrowsError(try hexToHash("ffd7918181c9ffFF")) { error in
			guard case .invalidHex = error as? ImageHashError else {
				XCTFail("expected .invalidHex for mixed case, got \(error)")
				return
			}
		}
	}
}
