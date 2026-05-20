import Foundation

/// Type-II DCT, no normalization. Matches scipy.fftpack.dct(x, type=2, norm=None).
///
/// y[k] = 2 * Σ_{n=0..N-1} x[n] * cos(π * k * (2n+1) / (2N))
///
/// For power-of-2 N, uses Makhoul's FFT trick to keep exact zeros where the
/// continuous DCT produces them. Direct O(N²) summation accumulates ~1e-11
/// noise that flips pHash median bits on uniform fixtures (verified Java/Go/Rust/JS).

func dct1d(_ x: [Double]) -> [Double] {
	let n = x.count
	if n == 0 { return [] }
	if (n & (n - 1)) == 0 { return makhoulDCT(x) }
	return directDCT(x)
}

/// 2-D DCT-II via column-wise then row-wise 1-D DCT. `x` is shape [n][n] flattened row-major.
func dct2d(_ x: [Double], _ n: Int) -> [Double] {
	var mid = [Double](repeating: 0, count: n * n)
	var col = [Double](repeating: 0, count: n)
	for xCol in 0..<n {
		for y in 0..<n { col[y] = x[y * n + xCol] }
		let c = dct1d(col)
		for y in 0..<n { mid[y * n + xCol] = c[y] }
	}
	var out = [Double](repeating: 0, count: n * n)
	var row = [Double](repeating: 0, count: n)
	for y in 0..<n {
		for xCol in 0..<n { row[xCol] = mid[y * n + xCol] }
		let r = dct1d(row)
		for xCol in 0..<n { out[y * n + xCol] = r[xCol] }
	}
	return out
}

private func makhoulDCT(_ x: [Double]) -> [Double] {
	let n = x.count
	var re = [Double](repeating: 0, count: n)
	var im = [Double](repeating: 0, count: n)
	for i in 0..<(n / 2) {
		re[i] = x[2 * i]
		re[n - 1 - i] = x[2 * i + 1]
	}
	if n % 2 == 1 { re[n / 2] = x[n - 1] }
	fft(&re, &im)
	var out = [Double](repeating: 0, count: n)
	for k in 0..<n {
		let angle = (-Double.pi * Double(k)) / (2.0 * Double(n))
		out[k] = 2.0 * (re[k] * cos(angle) - im[k] * sin(angle))
	}
	return out
}

private func directDCT(_ x: [Double]) -> [Double] {
	let n = x.count
	var y = [Double](repeating: 0, count: n)
	let factor = Double.pi / (2.0 * Double(n))
	for k in 0..<n {
		var sum: Double = 0
		for i in 0..<n {
			sum += x[i] * cos(factor * Double(k) * Double(2 * i + 1))
		}
		y[k] = 2.0 * sum
	}
	return y
}

/// In-place iterative radix-2 Cooley-Tukey FFT. Requires power-of-2 length.
private func fft(_ re: inout [Double], _ im: inout [Double]) {
	let n = re.count
	var j = 0
	for i in 1..<n {
		var bit = n >> 1
		while (j & bit) != 0 {
			j ^= bit
			bit >>= 1
		}
		j ^= bit
		if i < j {
			re.swapAt(i, j)
			im.swapAt(i, j)
		}
	}
	var length = 2
	while length <= n {
		let ang = -2.0 * Double.pi / Double(length)
		let wRe = cos(ang)
		let wIm = sin(ang)
		var i = 0
		while i < n {
			var curRe: Double = 1.0
			var curIm: Double = 0.0
			for k in 0..<(length / 2) {
				let uRe = re[i + k]
				let uIm = im[i + k]
				let vRe = re[i + k + length / 2] * curRe - im[i + k + length / 2] * curIm
				let vIm = re[i + k + length / 2] * curIm + im[i + k + length / 2] * curRe
				re[i + k] = uRe + vRe
				im[i + k] = uIm + vIm
				re[i + k + length / 2] = uRe - vRe
				im[i + k + length / 2] = uIm - vIm
				let nRe = curRe * wRe - curIm * wIm
				curIm = curRe * wIm + curIm * wRe
				curRe = nRe
			}
			i += length
		}
		length <<= 1
	}
}
