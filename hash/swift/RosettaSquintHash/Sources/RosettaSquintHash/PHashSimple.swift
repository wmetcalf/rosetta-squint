import Foundation

/// phashSimple: grayscale → Lanczos to (N*F, N*F) → row-wise 1-D DCT → block [0:N, 1:N+1] → mean threshold.
///
/// This matches `imagehash.phash_simple` (Python imagehash 4.3.2) exactly:
///   dct = scipy.fftpack.dct(pixels)          # 1-D DCT applied row-wise (NOT 2-D DCT)
///   dctlowfreq = dct[:hash_size, 1:hash_size+1]   # rows 0..N-1, cols 1..N (skip DC)
///   avg = dctlowfreq.mean()
///   diff = dctlowfreq > avg
///
/// Note: despite the name, this is NOT simply phash with mean instead of median.
/// It uses a 1-D (row-wise) DCT and skips column 0 (DC component).
public func phashSimple(_ image: RGBImage, hashSize: Int, highfreqFactor: Int = 4) throws -> Hash {
	try image.validate()
	guard hashSize >= 2 else { throw ImageHashError.invalidHashSize(hashSize) }
	let imgSize = hashSize * highfreqFactor

	let rgb = ImgRGB.toRGB(image)
	let gray = rgbToGray(rgb.data, width: rgb.width, height: rgb.height)
	let resized = lanczosResize(gray, srcW: rgb.width, srcH: rgb.height, dstW: imgSize, dstH: imgSize)

	// Row-wise 1-D DCT (scipy.fftpack.dct(pixels) applies DCT to each row)
	var dctRows = [[Double]](repeating: [], count: imgSize)
	for y in 0..<imgSize {
		var row = [Double](repeating: 0.0, count: imgSize)
		for x in 0..<imgSize {
			row[x] = Double(resized[y * imgSize + x])
		}
		dctRows[y] = dct1d(row)
	}

	// Extract dct[:hashSize, 1:hashSize+1] — rows 0..<N, cols 1..<N+1 (skip DC column 0)
	var block = [Double](repeating: 0.0, count: hashSize * hashSize)
	var k = 0
	for y in 0..<hashSize {
		for x in 1...hashSize {
			block[k] = dctRows[y][x]
			k += 1
		}
	}

	// Mean threshold with snap-to-threshold tie-break.
	let mean = block.reduce(0.0, +) / Double(block.count)

	// Snap-to-threshold tie-break: deterministic bit 0 on ties.
	// See spec/SPEC.md §"Threshold tie-break".
	let threshold = mean + SNAP_EPS
	var bits: [[Bool]] = []
	for y in 0..<hashSize {
		var row = [Bool](repeating: false, count: hashSize)
		for x in 1...hashSize {
			row[x - 1] = dctRows[y][x] > threshold
		}
		bits.append(row)
	}
	return try Hash(bits: bits)
}
