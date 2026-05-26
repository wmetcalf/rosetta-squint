import Foundation

/// ε threshold for the snap-to-threshold tie-break used by `phash`,
/// `phashSimple`, `whashDb4`, and `whashDb4Robust`. Coefficients within
/// `SNAP_EPS` of the threshold map deterministically to bit 0 across all
/// ports. Fixed across all 6 ports. See spec/SPEC.md §"Threshold tie-break".
public let SNAP_EPS: Double = 1e-10

/// phash: grayscale → Lanczos to (N*F, N*F) → 2-D DCT → top-left NxN block
/// → bit = (coefficient > median + `SNAP_EPS`).
///
/// The snap-to-threshold tie-break (ε = 1e-10) deterministically maps
/// coefficients within ε of the median to bit 0, eliminating cross-port
/// FP-noise divergence at large hash sizes.
public func phash(_ image: RGBImage, hashSize: Int, highfreqFactor: Int = 4) throws -> Hash {
	try image.validate()
	guard hashSize >= 2 else { throw ImageHashError.invalidHashSize(hashSize) }
	let imgSize = hashSize * highfreqFactor

	let rgb = ImgRGB.toRGB(image)
	let gray = rgbToGray(rgb.data, width: rgb.width, height: rgb.height)
	let resized = lanczosResize(gray, srcW: rgb.width, srcH: rgb.height, dstW: imgSize, dstH: imgSize)

	var doubles = [Double](repeating: 0, count: imgSize * imgSize)
	for i in 0..<resized.count { doubles[i] = Double(resized[i]) }

	let dctOut = dct2d(doubles, imgSize)

	// Extract top-left hashSize x hashSize block
	var block = [Double](repeating: 0, count: hashSize * hashSize)
	var k = 0
	for y in 0..<hashSize {
		for x in 0..<hashSize {
			block[k] = dctOut[y * imgSize + x]
			k += 1
		}
	}
	let sorted = block.sorted()
	let n = sorted.count
	let median: Double = n % 2 == 1 ? sorted[(n - 1) / 2] : (sorted[n / 2 - 1] + sorted[n / 2]) / 2.0

	// Snap-to-threshold tie-break: deterministic bit 0 on ties.
	let threshold = median + SNAP_EPS
	var bits: [[Bool]] = []
	for y in 0..<hashSize {
		var row = [Bool](repeating: false, count: hashSize)
		for x in 0..<hashSize {
			row[x] = dctOut[y * imgSize + x] > threshold
		}
		bits.append(row)
	}
	return try Hash(bits: bits)
}
