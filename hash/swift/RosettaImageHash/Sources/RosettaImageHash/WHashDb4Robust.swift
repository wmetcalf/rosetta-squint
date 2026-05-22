import Foundation

/// ε threshold for whashDb4Robust snap-to-zero. See spec/SPEC.md §whash_db4_robust.
public let WHASH_DB4_ROBUST_EPS: Double = 1e-12

/// Cross-port-stable variant of whashDb4. Identical pipeline up to LL band,
/// then snaps |c| < WHASH_DB4_ROBUST_EPS to 0 before median + threshold.
/// Real-world photos: same hash as whashDb4. Pathological symmetric inputs:
/// deterministic across all ports (NOT byte-exact-compatible with Python
/// imagehash on those inputs).
public func whashDb4Robust(_ image: RGBImage, hashSize: Int) throws -> Hash {
	guard hashSize >= 2 else { throw ImageHashError.invalidHashSize(hashSize) }
	guard isPowerOfTwoDb4Robust(hashSize) else { throw ImageHashError.notPowerOfTwo(hashSize) }

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
	var ll = db4Wavedec2(modified, level: dwtLevel)

	// Step 7: snap |c| < WHASH_DB4_ROBUST_EPS → 0 before median + threshold
	for y in 0..<ll.count {
		for x in 0..<ll[y].count {
			if abs(ll[y][x]) < WHASH_DB4_ROBUST_EPS {
				ll[y][x] = 0.0
			}
		}
	}

	// Step 8: median threshold
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

private func isPowerOfTwoDb4Robust(_ n: Int) -> Bool {
	return n > 0 && (n & (n - 1)) == 0
}
