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

final class ErrorSemanticsTests: XCTestCase {
	private func tinyImage() -> RGBImage {
		return RGBImage(width: 8, height: 8, data: [UInt8](repeating: 0, count: 8 * 8 * 3), channels: .rgb)
	}

	private func smallImage() -> RGBImage {
		var data = [UInt8](repeating: 0, count: 32 * 32 * 3)
		for i in 0..<(32 * 32) {
			data[i * 3] = 128
			data[i * 3 + 1] = 64
			data[i * 3 + 2] = 192
		}
		return RGBImage(width: 32, height: 32, data: data, channels: .rgb)
	}

	func testAverageHashRejectsHashSizeLessThan2() {
		XCTAssertThrowsError(try averageHash(tinyImage(), hashSize: 1)) { e in
			XCTAssertEqual(e as? ImageHashError, .invalidHashSize(1))
		}
		XCTAssertThrowsError(try averageHash(tinyImage(), hashSize: 0)) { e in
			XCTAssertEqual(e as? ImageHashError, .invalidHashSize(0))
		}
	}

	func testDHashRejectsHashSizeLessThan2() {
		XCTAssertThrowsError(try dhash(tinyImage(), hashSize: 1)) { e in
			XCTAssertEqual(e as? ImageHashError, .invalidHashSize(1))
		}
	}

	func testPHashRejectsHashSizeLessThan2() {
		XCTAssertThrowsError(try phash(tinyImage(), hashSize: 1)) { e in
			XCTAssertEqual(e as? ImageHashError, .invalidHashSize(1))
		}
	}

	func testWHashHaarRejectsHashSizeLessThan2() {
		XCTAssertThrowsError(try whashHaar(smallImage(), hashSize: 1)) { e in
			XCTAssertEqual(e as? ImageHashError, .invalidHashSize(1))
		}
	}

	func testWHashHaarRejectsNonPowerOfTwo() {
		XCTAssertThrowsError(try whashHaar(smallImage(), hashSize: 3)) { e in
			XCTAssertEqual(e as? ImageHashError, .notPowerOfTwo(3))
		}
		XCTAssertThrowsError(try whashHaar(smallImage(), hashSize: 5)) { e in
			XCTAssertEqual(e as? ImageHashError, .notPowerOfTwo(5))
		}
	}

	func testColorHashRejectsBinbitsLessThan1() {
		XCTAssertThrowsError(try colorhash(tinyImage(), binbits: 0)) { e in
			XCTAssertEqual(e as? ImageHashError, .invalidBinbits(0))
		}
	}

	func testHexToHashRejectsNonSquare() {
		XCTAssertThrowsError(try hexToHash("12345"))
	}

	func testHexToHashRejectsInvalidChars() {
		XCTAssertThrowsError(try hexToHash("xyz!"))
	}

	func testHexToFlathashRejectsHashSizeLessThan1() {
		XCTAssertThrowsError(try hexToFlathash("00", hashSize: 0)) { e in
			XCTAssertEqual(e as? ImageHashError, .invalidBinbits(0))
		}
	}
}
