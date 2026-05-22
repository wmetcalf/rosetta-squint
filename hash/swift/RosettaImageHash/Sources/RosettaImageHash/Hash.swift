import Foundation

/// Input image type for all hash algorithms.
public struct RGBImage: Equatable {
	public let width: Int
	public let height: Int
	public let data: [UInt8]
	public let channels: Channels

	public enum Channels: Equatable {
		case rgb   // 3 bytes per pixel
		case rgba  // 4 bytes per pixel; alpha composited against opaque black internally
	}

	public init(width: Int, height: Int, data: [UInt8], channels: Channels) {
		self.width = width
		self.height = height
		self.data = data
		self.channels = channels
	}
}

/// All errors thrown by the public API.
public enum ImageHashError: Error, Equatable {
	case invalidHashSize(Int)
	case notPowerOfTwo(Int)
	case hashSizeTooLarge(level: Int, maxLevel: Int)
	case invalidBinbits(Int)
	case invalidHex(String)
	case shapeMismatch(lhs: ShapeKey, rhs: ShapeKey)
	case emptyBits
	case nonRectangular

	/// Helper struct because Swift doesn't auto-synthesize Equatable for enum
	/// cases with tuple associated values.
	public struct ShapeKey: Equatable {
		public let height: Int
		public let width: Int
		public init(_ height: Int, _ width: Int) {
			self.height = height
			self.width = width
		}
	}
}

/// Hash value: a 2-D boolean grid (typically NxN, but 14xB for colorhash).
public struct Hash: Equatable, Hashable, CustomStringConvertible {
	internal let bits: [[Bool]]

	public init(bits: [[Bool]]) throws {
		guard !bits.isEmpty, !bits[0].isEmpty else { throw ImageHashError.emptyBits }
		let w = bits[0].count
		for row in bits where row.count != w {
			throw ImageHashError.nonRectangular
		}
		self.bits = bits
	}

	public var hex: String { Bitpack.pack(bits) }
	public var description: String { hex }

	public var bitCount: Int {
		bits.isEmpty ? 0 : bits.count * bits[0].count
	}

	public func subtract(_ other: Hash) throws -> Int {
		let h1 = bits.count, w1 = bits[0].count
		let h2 = other.bits.count, w2 = other.bits[0].count
		guard h1 == h2 && w1 == w2 else {
			throw ImageHashError.shapeMismatch(
				lhs: ImageHashError.ShapeKey(h1, w1),
				rhs: ImageHashError.ShapeKey(h2, w2)
			)
		}
		var diff = 0
		for y in 0..<h1 {
			for x in 0..<w1 where bits[y][x] != other.bits[y][x] {
				diff += 1
			}
		}
		return diff
	}
}
