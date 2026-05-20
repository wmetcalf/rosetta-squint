import Foundation

public func hexToHash(_ hex: String) throws -> Hash {
	let bits = try Bitpack.unpackSquare(hex)
	return try Hash(bits: bits)
}

public func hexToFlathash(_ hex: String, hashSize: Int) throws -> Hash {
	guard hashSize >= 1 else { throw ImageHashError.invalidBinbits(hashSize) }
	let bits = try Bitpack.unpackFlat(hex, secondAxis: hashSize)
	return try Hash(bits: bits)
}
