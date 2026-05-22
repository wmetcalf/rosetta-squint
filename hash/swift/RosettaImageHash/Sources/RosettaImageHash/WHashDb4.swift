import Foundation

/// whashDb4: same pipeline as whashHaar but using Daubechies-4 for the second wavedec2.
///
/// 1.  imageNaturalScale = 2^floor(log2(min(W, H))), imageScale = max(imageNaturalScale, hashSize).
/// 2.  llMaxLevel = log2(imageScale), level = log2(hashSize), dwtLevel = llMaxLevel - level.
/// 3.  Validate hashSize power-of-2 and level <= llMaxLevel.
/// 4.  Lanczos resize to (imageScale, imageScale), convert to float64 / 255.
/// 5.  Haar wavedec2 at llMaxLevel → zero LL band → Haar waverec2.
/// 6.  db4 wavedec2 at dwtLevel on the modified pixels → LL subband.
/// 7.  Median threshold → boolean bits → hex.
public func whashDb4(_ image: RGBImage, hashSize: Int) throws -> Hash {
	guard hashSize >= 2 else { throw ImageHashError.invalidHashSize(hashSize) }
	guard isPowerOfTwoDb4(hashSize) else { throw ImageHashError.notPowerOfTwo(hashSize) }

	let rgb = ImgRGB.toRGB(image)
	let gray = rgbToGray(rgb.data, width: rgb.width, height: rgb.height)

	let minSide = min(rgb.width, rgb.height)
	let imageNaturalScale = 1 << Int(log2(Double(minSide)))
	let imageScale = max(imageNaturalScale, hashSize)

	let llMaxLevel = Int(log2(Double(imageScale)))
	let level = Int(log2(Double(hashSize)))
	guard level <= llMaxLevel else {
		throw ImageHashError.hashSizeTooLarge(level: level, maxLevel: llMaxLevel)
	}
	let dwtLevel = llMaxLevel - level

	let resized = lanczosResize(gray, srcW: rgb.width, srcH: rgb.height, dstW: imageScale, dstH: imageScale)
	var pixels: [[Double]] = []
	for y in 0..<imageScale {
		var row = [Double](repeating: 0.0, count: imageScale)
		for x in 0..<imageScale {
			row[x] = Double(resized[y * imageScale + x]) / 255.0
		}
		pixels.append(row)
	}

	// Step 5: Haar remove_max_haar_ll (same as whashHaar)
	var fullDec = wavedec2(pixels, level: llMaxLevel)
	for y in 0..<fullDec.cA.count {
		for x in 0..<fullDec.cA[y].count {
			fullDec.cA[y][x] = 0
		}
	}
	let modified = waverec2(fullDec)

	// Step 6: db4 wavedec2 at dwtLevel → LL subband
	let ll = db4Wavedec2(modified, level: dwtLevel)

	// Step 7: median threshold
	var flat: [Double] = []
	for row in ll { flat.append(contentsOf: row) }
	let sorted = flat.sorted()
	let n = sorted.count
	let median: Double = n % 2 == 1 ? sorted[(n - 1) / 2] : (sorted[n / 2 - 1] + sorted[n / 2]) / 2.0

	var bits: [[Bool]] = []
	for row in ll {
		bits.append(row.map { $0 > median })
	}
	return try Hash(bits: bits)
}

private func isPowerOfTwoDb4(_ n: Int) -> Bool {
	return n > 0 && (n & (n - 1)) == 0
}
