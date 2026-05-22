// Package db4 implements 2-D Daubechies-4 (db4) DWT/IDWT with pywt's
// 'symmetric' boundary mode (half-point symmetric, boundary value repeated).
//
// Filter bank (length 8, from PyWavelets):
//
//	dec_lo: [-0.0106, 0.0329, 0.0308, -0.1870, -0.0280, 0.6309, 0.7148, 0.2304]
//	dec_hi: [-0.2304, 0.7148, -0.6309, -0.0280, 0.1870, 0.0308, -0.0329, -0.0106]
//	rec_lo: [ 0.2304, 0.7148,  0.6309, -0.0280, -0.1870, 0.0308,  0.0329, -0.0106]
//	rec_hi: [-0.0106,-0.0329,  0.0308,  0.1870, -0.0280,-0.6309,  0.7148, -0.2304]
//
// 1-D DWT formula (verified against pywt.dwt with mode='symmetric'):
//
//	cA[k] = sum_{j=0}^{7} dec_lo[j] * symExt(x, 2k - j + 1)
//
// where symExt uses half-point symmetric extension (boundary value repeated),
// implemented as: period = 2*N; normalize i into [0, 2N); if i >= N, mirror as 2N-1-i.
//
// Note the shift +1 (not -(L-1)): this anti-causal, shifted form matches pywt's
// internal C implementation exactly.
//
// 1-D IDWT formula (verified against pywt.idwt):
//  1. Upsample cA by 2 (zeros between samples).
//  2. Full convolution with rec_lo: output length 2*M + L - 1.
//  3. Same for cD/rec_hi, add results.
//  4. Crop result[L-2 : L-2+N] = result[6 : 6+N] to recover the original N samples.
//
// 2-D DWT: row-wise first (axis=1), then column-wise (axis=0).
// 2-D IDWT: column-wise first, then row-wise. Crop each dimension to stored input size.
package db4

// db4 filter coefficients (from PyWavelets pywt.Wavelet('db4')).
var (
	decLo = [8]float64{
		-0.010597401785069032,
		0.0328830116668852,
		0.030841381835560764,
		-0.18703481171909309,
		-0.027983769416859854,
		0.6308807679298589,
		0.7148465705529157,
		0.2303778133088965,
	}
	decHi = [8]float64{
		-0.2303778133088965,
		0.7148465705529157,
		-0.6308807679298589,
		-0.027983769416859854,
		0.18703481171909309,
		0.030841381835560764,
		-0.0328830116668852,
		-0.010597401785069032,
	}
	recLo = [8]float64{
		0.2303778133088965,
		0.7148465705529157,
		0.6308807679298589,
		-0.027983769416859854,
		-0.18703481171909309,
		0.030841381835560764,
		0.0328830116668852,
		-0.010597401785069032,
	}
	recHi = [8]float64{
		-0.010597401785069032,
		-0.0328830116668852,
		0.030841381835560764,
		0.18703481171909309,
		-0.027983769416859854,
		-0.6308807679298589,
		0.7148465705529157,
		-0.2303778133088965,
	}
)

const filterLen = 8

// CoeffLen returns the number of DWT coefficients produced from an input of
// length n with the db4 filter in symmetric mode.
// Formula: floor((n + filterLen - 1) / 2) = (n + 7) / 2.
func CoeffLen(n int) int {
	return (n + filterLen - 1) / 2
}

// WavedecResult holds a multi-level 2-D db4 decomposition.
// CA is the deepest approximation (LL) band.
// Details[i] = (cH, cV, cD) for level i (innermost = deepest first).
// InputSizes[i] = (height, width) of the input to level i+1 of the DWT,
// used during reconstruction to crop IDWT output to exact original dimensions.
type WavedecResult struct {
	CA         [][]float64
	Details    [][3][][]float64
	InputSizes [][2]int // [i] = (h, w) of input at level i (0 = first level)
}

// Dwt2 applies a single 2-D db4 DWT. Row-wise first (axis=1), then column-wise (axis=0).
func Dwt2(x [][]float64) (cA, cH, cV, cD [][]float64) {
	h := len(x)
	w := len(x[0])
	outH := CoeffLen(h)
	outW := CoeffLen(w)

	// Row pass: for each row, apply DWT1D along columns.
	rowLo := make([][]float64, h)
	rowHi := make([][]float64, h)
	for y := 0; y < h; y++ {
		rowLo[y], rowHi[y] = dwt1d(x[y], outW)
	}

	// Column pass: for each output column, apply DWT1D along rows.
	cA = makeGrid(outH, outW)
	cH = makeGrid(outH, outW)
	cV = makeGrid(outH, outW)
	cD = makeGrid(outH, outW)

	col := make([]float64, h)
	for xc := 0; xc < outW; xc++ {
		for y := 0; y < h; y++ {
			col[y] = rowLo[y][xc]
		}
		lo, hi := dwt1d(col, outH)
		for y := 0; y < outH; y++ {
			cA[y][xc] = lo[y]
			cH[y][xc] = hi[y]
		}
		for y := 0; y < h; y++ {
			col[y] = rowHi[y][xc]
		}
		lo, hi = dwt1d(col, outH)
		for y := 0; y < outH; y++ {
			cV[y][xc] = lo[y]
			cD[y][xc] = hi[y]
		}
	}
	return
}

// Idwt2 inverts a single 2-D db4 DWT. Column-wise first, then row-wise.
// targetH and targetW are the original input dimensions (to crop IDWT output).
func Idwt2(cA, cH, cV, cD [][]float64, targetH, targetW int) [][]float64 {
	sh := len(cA)
	sw := len(cA[0])

	// Column inverse: recover rows of lo (= rowLo) and hi (= rowHi).
	rowLo := makeGrid(targetH, sw)
	rowHi := makeGrid(targetH, sw)
	col := make([]float64, sh)
	for xc := 0; xc < sw; xc++ {
		for y := 0; y < sh; y++ {
			col[y] = cA[y][xc]
		}
		hi := make([]float64, sh)
		for y := 0; y < sh; y++ {
			hi[y] = cH[y][xc]
		}
		colOut := idwt1d(col, hi, targetH)
		for y := 0; y < targetH; y++ {
			rowLo[y][xc] = colOut[y]
		}
		for y := 0; y < sh; y++ {
			col[y] = cV[y][xc]
		}
		for y := 0; y < sh; y++ {
			hi[y] = cD[y][xc]
		}
		colOut = idwt1d(col, hi, targetH)
		for y := 0; y < targetH; y++ {
			rowHi[y][xc] = colOut[y]
		}
	}

	// Row inverse.
	out := makeGrid(targetH, targetW)
	for y := 0; y < targetH; y++ {
		row := idwt1d(rowLo[y], rowHi[y], targetW)
		copy(out[y], row)
	}
	return out
}

// Wavedec2 applies multi-level 2-D db4 decomposition.
func Wavedec2(x [][]float64, level int) WavedecResult {
	current := x
	details := make([][3][][]float64, 0, level)
	sizes := make([][2]int, 0, level)
	for l := 0; l < level; l++ {
		h := len(current)
		w := len(current[0])
		sizes = append(sizes, [2]int{h, w})
		cA, cH, cV, cD := Dwt2(current)
		details = append(details, [3][][]float64{cH, cV, cD})
		current = cA
	}
	// Reverse so that Details[0] = outermost (deepest in the recursion = finest scale).
	for i, j := 0, len(details)-1; i < j; i, j = i+1, j-1 {
		details[i], details[j] = details[j], details[i]
		sizes[i], sizes[j] = sizes[j], sizes[i]
	}
	return WavedecResult{CA: current, Details: details, InputSizes: sizes}
}

// Waverec2 reconstructs from a WavedecResult.
func Waverec2(d WavedecResult) [][]float64 {
	current := d.CA
	n := len(d.Details)
	for i := 0; i < n; i++ {
		// Details[i] is the outermost in the stored (reversed) list.
		// InputSizes[i] is the input size at that level.
		sz := d.InputSizes[i]
		det := d.Details[i]
		current = Idwt2(current, det[0], det[1], det[2], sz[0], sz[1])
	}
	return current
}

// dwt1d computes a 1-D db4 DWT with symmetric extension.
// outLen is the expected number of output samples (CoeffLen(len(x))).
// Returns (low, high) each of length outLen.
//
// Formula: cA[k] = sum_{j=0}^{L-1} dec_lo[j] * symExt(x, 2k - j + 1)
// Verified against pywt.dwt(x, 'db4', mode='symmetric').
func dwt1d(x []float64, outLen int) (low, high []float64) {
	n := len(x)
	low = make([]float64, outLen)
	high = make([]float64, outLen)
	L := filterLen // 8

	for k := 0; k < outLen; k++ {
		var sumLo, sumHi float64
		for j := 0; j < L; j++ {
			s := symExt(x, n, 2*k-j+1)
			sumLo += decLo[j] * s
			sumHi += decHi[j] * s
		}
		low[k] = sumLo
		high[k] = sumHi
	}
	return
}

// idwt1d computes a 1-D db4 IDWT.
// targetN is the length of the original input (used for cropping).
// Formula: upsample cA and cD, convolve with rec_lo/rec_hi, add, crop to targetN.
func idwt1d(cA, cD []float64, targetN int) []float64 {
	M := len(cA)
	L := filterLen
	// Upsampled length: 2*M (with zeros inserted between samples).
	// Full convolution length: 2*M + L - 1.
	convLen := 2*M + L - 1
	raw := make([]float64, convLen)

	// Apply rec_lo to upsampled cA and rec_hi to upsampled cD, accumulating.
	for k := 0; k < M; k++ {
		pos := 2 * k // position of the k-th coefficient in the upsampled signal
		for j := 0; j < L; j++ {
			idx := pos + j
			raw[idx] += recLo[j]*cA[k] + recHi[j]*cD[k]
		}
	}

	// Crop: start at L-2 = 6, take targetN samples.
	start := L - 2
	out := make([]float64, targetN)
	copy(out, raw[start:start+targetN])
	return out
}

// symExt returns x[i] with half-point symmetric (boundary-repeated) extension.
// period = 2*N; normalize i into [0, 2N); if i >= N, map as 2N-1-i.
func symExt(x []float64, n, i int) float64 {
	period := 2 * n
	i = ((i % period) + period) % period
	if i >= n {
		i = period - 1 - i
	}
	return x[i]
}

// makeGrid allocates an h×w float64 grid.
func makeGrid(h, w int) [][]float64 {
	g := make([][]float64, h)
	for y := range g {
		g[y] = make([]float64, w)
	}
	return g
}
