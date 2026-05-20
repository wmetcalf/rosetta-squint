// Package dct implements 1-D and 2-D Type-II DCT, no normalization.
// Matches scipy.fftpack.dct(x, type=2, norm=None).
//
// For power-of-2 N, uses Makhoul's FFT trick to keep exact zeros where the
// continuous DCT produces them. The direct O(N²) summation accumulates
// ~1e-11 floating-point noise that flips pHash median bits on uniform fixtures.
// (Java port Plan 2 hit this and switched to Makhoul.)
package dct

import "math"

// DCT1D returns y[k] = 2 * Σ_{n=0..N-1} x[n] * cos(π * k * (2n+1) / (2N)) for k in [0, N).
func DCT1D(x []float64) []float64 {
	n := len(x)
	if n == 0 {
		return nil
	}
	if isPowerOfTwo(n) {
		return makhoulDCT(x)
	}
	return directDCT(x)
}

// DCT2D applies 1-D DCT column-wise then row-wise.
func DCT2D(pixels [][]float64) [][]float64 {
	h := len(pixels)
	w := len(pixels[0])
	mid := make([][]float64, h)
	for i := range mid {
		mid[i] = make([]float64, w)
	}
	col := make([]float64, h)
	for x := 0; x < w; x++ {
		for y := 0; y < h; y++ {
			col[y] = pixels[y][x]
		}
		c := DCT1D(col)
		for y := 0; y < h; y++ {
			mid[y][x] = c[y]
		}
	}
	out := make([][]float64, h)
	for y := 0; y < h; y++ {
		out[y] = DCT1D(mid[y])
	}
	return out
}

func isPowerOfTwo(n int) bool { return n > 0 && (n&(n-1)) == 0 }

func makhoulDCT(x []float64) []float64 {
	n := len(x)
	re := make([]float64, n)
	im := make([]float64, n)
	for i := 0; i < n/2; i++ {
		re[i] = x[2*i]
		re[n-1-i] = x[2*i+1]
	}
	if n%2 == 1 {
		re[n/2] = x[n-1]
	}
	fft(re, im)
	out := make([]float64, n)
	for k := 0; k < n; k++ {
		angle := -math.Pi * float64(k) / (2.0 * float64(n))
		out[k] = 2.0 * (re[k]*math.Cos(angle) - im[k]*math.Sin(angle))
	}
	return out
}

func directDCT(x []float64) []float64 {
	n := len(x)
	y := make([]float64, n)
	factor := math.Pi / (2.0 * float64(n))
	for k := 0; k < n; k++ {
		var sum float64
		for i := 0; i < n; i++ {
			sum += x[i] * math.Cos(factor*float64(k)*float64(2*i+1))
		}
		y[k] = 2.0 * sum
	}
	return y
}

func fft(re, im []float64) {
	n := len(re)
	j := 0
	for i := 1; i < n; i++ {
		bit := n >> 1
		for ; (j & bit) != 0; bit >>= 1 {
			j ^= bit
		}
		j ^= bit
		if i < j {
			re[i], re[j] = re[j], re[i]
			im[i], im[j] = im[j], im[i]
		}
	}
	for length := 2; length <= n; length <<= 1 {
		ang := -2.0 * math.Pi / float64(length)
		wRe, wIm := math.Cos(ang), math.Sin(ang)
		for i := 0; i < n; i += length {
			curRe, curIm := 1.0, 0.0
			for k := 0; k < length/2; k++ {
				uRe, uIm := re[i+k], im[i+k]
				vRe := re[i+k+length/2]*curRe - im[i+k+length/2]*curIm
				vIm := re[i+k+length/2]*curIm + im[i+k+length/2]*curRe
				re[i+k] = uRe + vRe
				im[i+k] = uIm + vIm
				re[i+k+length/2] = uRe - vRe
				im[i+k+length/2] = uIm - vIm
				nRe := curRe*wRe - curIm*wIm
				curIm = curRe*wIm + curIm*wRe
				curRe = nRe
			}
		}
	}
}
