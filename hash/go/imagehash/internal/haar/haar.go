// Package haar implements 2-D Haar DWT/IDWT with pywt's 'symmetric' boundary mode.
//
// IMPORTANT: sqrt2Inv = math.Sqrt(0.5) (NOT 1.0 / math.Sqrt(2.0)).
// The latter is one ULP lower and accumulates errors through 8-10 wavedec levels,
// flipping bits at the whash median boundary. (Confirmed Java port Plan 2.)
//
// Column-pass before row-pass evaluation order matches pywt's floating-point
// addition order. Required for byte-exact parity.
package haar

import "math"

var sqrt2Inv = math.Sqrt(0.5)

// WavedecResult holds a multi-level decomposition: deepest LL in CA, plus
// detail tuples in Details (outer-to-inner = deepest-first).
type WavedecResult struct {
	CA      [][]float64
	Details [][3][][]float64 // each [3]: (cH, cV, cD)
}

// Dwt2 single-level 2-D Haar DWT.
func Dwt2(x [][]float64) (cA, cH, cV, cD [][]float64) {
	h := len(x)
	w := len(x[0])

	// Column pass first
	colLow := make([][]float64, w)
	colHigh := make([][]float64, w)
	col := make([]float64, h)
	for xCol := 0; xCol < w; xCol++ {
		for y := 0; y < h; y++ {
			col[y] = x[y][xCol]
		}
		low, high := dwt1d(col)
		colLow[xCol] = low
		colHigh[xCol] = high
	}
	outH := (h + 1) / 2
	outW := (w + 1) / 2

	cA = make([][]float64, outH)
	cH = make([][]float64, outH)
	cV = make([][]float64, outH)
	cD = make([][]float64, outH)
	for y := 0; y < outH; y++ {
		cA[y] = make([]float64, outW)
		cH[y] = make([]float64, outW)
		cV[y] = make([]float64, outW)
		cD[y] = make([]float64, outW)
	}
	rowLow := make([]float64, w)
	rowHigh := make([]float64, w)
	for y := 0; y < outH; y++ {
		for xCol := 0; xCol < w; xCol++ {
			rowLow[xCol] = colLow[xCol][y]
			rowHigh[xCol] = colHigh[xCol][y]
		}
		lowL, highL := dwt1d(rowLow)
		lowH, highH := dwt1d(rowHigh)
		for xd := 0; xd < outW; xd++ {
			cA[y][xd] = lowL[xd]
			cV[y][xd] = highL[xd]
			cH[y][xd] = lowH[xd]
			cD[y][xd] = highH[xd]
		}
	}
	return cA, cH, cV, cD
}

// Idwt2 single-level 2-D Haar inverse — undoes Dwt2.
// Inverts row-pass first then column-pass (mirror of Dwt2's column-then-row).
func Idwt2(cA, cH, cV, cD [][]float64) [][]float64 {
	sh := len(cA)
	sw := len(cA[0])
	outH := sh * 2
	outW := sw * 2

	// Row inverse.
	colLow := make([][]float64, sh)
	colHigh := make([][]float64, sh)
	for y := 0; y < sh; y++ {
		colLow[y] = idwt1d(cA[y], cV[y])
		colHigh[y] = idwt1d(cH[y], cD[y])
	}

	// Column inverse for each output column.
	out := make([][]float64, outH)
	for y := 0; y < outH; y++ {
		out[y] = make([]float64, outW)
	}
	colL := make([]float64, sh)
	colH := make([]float64, sh)
	for xCol := 0; xCol < outW; xCol++ {
		for y := 0; y < sh; y++ {
			colL[y] = colLow[y][xCol]
			colH[y] = colHigh[y][xCol]
		}
		colOut := idwt1d(colL, colH)
		for y := 0; y < outH; y++ {
			out[y][xCol] = colOut[y]
		}
	}
	return out
}

// Wavedec2 multi-level decomposition; returns deepest-LL-first.
func Wavedec2(x [][]float64, level int) WavedecResult {
	current := x
	details := make([][3][][]float64, 0, level)
	for l := 0; l < level; l++ {
		cA, cH, cV, cD := Dwt2(current)
		details = append(details, [3][][]float64{cH, cV, cD})
		current = cA
	}
	for i, j := 0, len(details)-1; i < j; i, j = i+1, j-1 {
		details[i], details[j] = details[j], details[i]
	}
	return WavedecResult{CA: current, Details: details}
}

// Waverec2 multi-level reconstruction.
func Waverec2(d WavedecResult) [][]float64 {
	current := d.CA
	for i := 0; i < len(d.Details); i++ {
		current = Idwt2(current, d.Details[i][0], d.Details[i][1], d.Details[i][2])
	}
	return current
}

func dwt1d(x []float64) (low, high []float64) {
	n := len(x)
	if (n & 1) != 0 {
		ext := make([]float64, n+1)
		copy(ext, x)
		ext[n] = x[n-1]
		x = ext
		n++
	}
	half := n / 2
	low = make([]float64, half)
	high = make([]float64, half)
	for i := 0; i < half; i++ {
		a := x[2*i]
		b := x[2*i+1]
		low[i] = sqrt2Inv*a + sqrt2Inv*b
		high[i] = sqrt2Inv*a - sqrt2Inv*b
	}
	return low, high
}

func idwt1d(low, high []float64) []float64 {
	half := len(low)
	n := half * 2
	out := make([]float64, n)
	for i := 0; i < half; i++ {
		out[2*i] = sqrt2Inv*low[i] + sqrt2Inv*high[i]
		out[2*i+1] = sqrt2Inv*low[i] - sqrt2Inv*high[i]
	}
	return out
}
