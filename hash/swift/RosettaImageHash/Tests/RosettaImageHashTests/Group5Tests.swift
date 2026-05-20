import Foundation
import XCTest
@testable import RosettaImageHash

final class HashSemanticsTests: XCTestCase {
	func testHammingDistanceZeroForEqual() throws {
		let a = try Hash(bits: [[true, false], [true, true]])
		let b = try Hash(bits: [[true, false], [true, true]])
		XCTAssertEqual(try a.subtract(b), 0)
	}

	func testHammingDistanceCountsDifferingBits() throws {
		let a = try Hash(bits: [[true, false], [true, true]])
		let b = try Hash(bits: [[false, false], [true, false]])
		XCTAssertEqual(try a.subtract(b), 2)
	}

	func testBitCountIsHeightTimesWidth() throws {
		let bits = [[Bool]](repeating: [Bool](repeating: false, count: 8), count: 8)
		XCTAssertEqual(try Hash(bits: bits).bitCount, 64)
	}

	func testEqualsIsValueBased() throws {
		let a = try Hash(bits: [[true, false]])
		let b = try Hash(bits: [[true, false]])
		let c = try Hash(bits: [[false, false]])
		XCTAssertEqual(a, b)
		XCTAssertNotEqual(a, c)
	}

	func testHexAllOnes8x8() throws {
		let bits = [[Bool]](repeating: [Bool](repeating: true, count: 8), count: 8)
		XCTAssertEqual(try Hash(bits: bits).hex, "ffffffffffffffff")
	}

	func testSubtractShapeMismatchThrows() throws {
		let a = try Hash(bits: [[true, false]])
		let b = try Hash(bits: [[true, false], [true, false]])
		XCTAssertThrowsError(try a.subtract(b)) { error in
			guard case .shapeMismatch = error as? ImageHashError else {
				XCTFail("expected ImageHashError.shapeMismatch, got \(error)")
				return
			}
		}
	}

	func testInitRejectsEmptyBits() {
		XCTAssertThrowsError(try Hash(bits: [])) { error in
			XCTAssertEqual(error as? ImageHashError, .emptyBits)
		}
		XCTAssertThrowsError(try Hash(bits: [[]])) { error in
			XCTAssertEqual(error as? ImageHashError, .emptyBits)
		}
	}

	func testInitRejectsNonRectangular() {
		XCTAssertThrowsError(try Hash(bits: [[true, false], [true]])) { error in
			XCTAssertEqual(error as? ImageHashError, .nonRectangular)
		}
	}

	func testDescriptionEqualsHex() throws {
		let bits = [[Bool]](repeating: [Bool](repeating: true, count: 8), count: 8)
		let h = try Hash(bits: bits)
		XCTAssertEqual(h.description, h.hex)
	}
}
