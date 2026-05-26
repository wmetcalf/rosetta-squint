import Foundation

/// dhashVertical: grayscale → Lanczos to (W=N, H=N+1) → column-wise adjacent-row diff (strict >).
/// This preserves the pre-3.0 (buggy) dhash direction for backward compatibility with stored hashes.
public func dhashVertical(_ image: RGBImage, hashSize: Int) throws -> Hash {
	try image.validate()
	guard hashSize >= 2 else { throw ImageHashError.invalidHashSize(hashSize) }
	let rgb = ImgRGB.toRGB(image)
	let gray = rgbToGray(rgb.data, width: rgb.width, height: rgb.height)
	// Resize to (width=N, height=N+1)
	let resized = lanczosResize(gray, srcW: rgb.width, srcH: rgb.height, dstW: hashSize, dstH: hashSize + 1)

	var bits: [[Bool]] = []
	for y in 0..<hashSize {
		let rowOff = y * hashSize
		let nextOff = (y + 1) * hashSize
		var row = [Bool](repeating: false, count: hashSize)
		for x in 0..<hashSize {
			row[x] = resized[nextOff + x] > resized[rowOff + x]
		}
		bits.append(row)
	}
	return try Hash(bits: bits)
}
