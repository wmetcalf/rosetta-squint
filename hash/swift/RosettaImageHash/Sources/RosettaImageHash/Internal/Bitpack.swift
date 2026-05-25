import Foundation

/// Boolean array ↔ hex string conversion. Row-major MSB-first, RIGHT-aligned
/// (treats the bit sequence as a big-endian integer and right-justifies in
/// the hex string). Matches Python `imagehash._binary_array_to_hex`.
///
/// CRITICAL: left-pad by (4 - total % 4) % 4 zero bits BEFORE writing into bytes.
/// Without this, colorhash binbits=3 hex would be off by a left-shift versus Python.
/// This is the bug the JS port caught mid-flight — applied here from the start.

enum Bitpack {

	static func pack(_ bits: [[Bool]]) -> String {
		let h = bits.count
		if h == 0 || bits[0].isEmpty { return "" }
		let w = bits[0].count
		let total = h * w
		let pad = (4 - (total % 4)) % 4
		let paddedTotal = total + pad
		let byteCount = (paddedTotal + 7) >> 3
		var bytes = [UInt8](repeating: 0, count: byteCount)

		// Write `pad` zero bits, then the actual bits, MSB-first into the byte array.
		var bi = pad
		for row in bits {
			for b in row {
				if b {
					bytes[bi >> 3] |= UInt8(1 << (7 - (bi & 7)))
				}
				bi += 1
			}
		}

		let width = paddedTotal >> 2   // hex chars = paddedTotal / 4
		var out = ""
		for i in 0..<width {
			let bitPos = i * 4
			let byteIdx = bitPos >> 3
			let nibble: UInt8 = (bitPos & 7) == 0 ? (bytes[byteIdx] >> 4) : (bytes[byteIdx] & 0x0f)
			out.append(String(nibble, radix: 16))
		}
		return out
	}

	static func unpackSquare(_ hex: String) throws -> [[Bool]] {
		let bits = try hexToBits(hex)
		let total = bits.count
		// Double-precision sqrt is exact for perfect squares within Int53 range;
		// the equality check below rejects any non-square `total`.
		let n = Int(Double(total).squareRoot())
		guard n * n == total else {
			throw ImageHashError.invalidHex("hex length \(hex.count) (\(total) bits) is not a square shape")
		}
		var out: [[Bool]] = []
		var idx = 0
		for _ in 0..<n {
			var row = [Bool](repeating: false, count: n)
			for x in 0..<n {
				row[x] = bits[idx]
				idx += 1
			}
			out.append(row)
		}
		return out
	}

	static func unpackFlat(_ hex: String, secondAxis: Int) throws -> [[Bool]] {
		let bits = try hexToBits(hex)
		let total = 14 * secondAxis
		guard bits.count >= total else {
			throw ImageHashError.invalidHex("hex too short for 14x\(secondAxis) shape: \(bits.count) bits")
		}
		var idx = bits.count - total
		var out: [[Bool]] = []
		for _ in 0..<14 {
			var row = [Bool](repeating: false, count: secondAxis)
			for x in 0..<secondAxis {
				row[x] = bits[idx]
				idx += 1
			}
			out.append(row)
		}
		return out
	}

	private static func hexToBits(_ hex: String) throws -> [Bool] {
		guard !hex.isEmpty else { throw ImageHashError.invalidHex("empty hex") }
		var bits = [Bool](repeating: false, count: hex.count * 4)
		var i = 0
		for c in hex {
			// Spec: lowercase hex only — matches Rust/Go/Java/JS port behavior.
			// hexDigitValue accepts both A-F and a-f, so we add an explicit lowercase check.
			guard c.isASCII, (c.isNumber || ("a"..."f").contains(c)) else {
				throw ImageHashError.invalidHex("invalid char '\(c)' in '\(hex)'")
			}
			guard let v = c.hexDigitValue, v >= 0 && v < 16 else {
				throw ImageHashError.invalidHex("invalid char '\(c)' in '\(hex)'")
			}
			for b in 0..<4 {
				bits[i * 4 + b] = ((v >> (3 - b)) & 1) == 1
			}
			i += 1
		}
		return bits
	}
}
