// Package lanczos implements Pillow-compatible Lanczos3 resize on uint8 grayscale.
//
// Reproduces libImaging/Resample.c precompute_coeffs precisely:
//   - center = (idx + 0.5) * scale
//   - filterScale = max(1.0, scale)
//   - support = 3.0 * filterScale
//   - kernel = sinc(x) * sinc(x/3) for |x| < 3
//   - xmin = int(center - support + 0.5), clamped to [0, srcSize)
//   - xmax = int(center + support + 0.5), clamped to (xmin, srcSize]  EXCLUSIVE upper bound
//   - weights normalized per output pixel
//   - PRECISION_BITS = 32 - 8 - 2 = 22 for fixed-point coefficients (NOT 32)
//   - acc accumulated in int64
package lanczos

import "math"

const (
	precisionBits = 32 - 8 - 2 // = 22 — matches Pillow's #define PRECISION_BITS
	support       = 3.0
)

// Resize converts src[H][W] to a new [dstH][dstW] uint8 buffer via Lanczos3.
func Resize(src [][]uint8, dstW, dstH int) [][]uint8 {
	srcH := len(src)
	srcW := len(src[0])

	offsH, lensH, weightsH := precomputeCoeffs(srcW, dstW)
	mid := make([][]uint8, srcH)
	for y := 0; y < srcH; y++ {
		row := src[y]
		out := make([]uint8, dstW)
		for xd := 0; xd < dstW; xd++ {
			var acc int64
			w := weightsH[xd]
			off := offsH[xd]
			for i := 0; i < lensH[xd]; i++ {
				acc += int64(w[i]) * int64(row[off+i])
			}
			out[xd] = clip8(acc)
		}
		mid[y] = out
	}

	offsV, lensV, weightsV := precomputeCoeffs(srcH, dstH)
	result := make([][]uint8, dstH)
	for yd := 0; yd < dstH; yd++ {
		w := weightsV[yd]
		off := offsV[yd]
		out := make([]uint8, dstW)
		for x := 0; x < dstW; x++ {
			var acc int64
			for i := 0; i < lensV[yd]; i++ {
				acc += int64(w[i]) * int64(mid[off+i][x])
			}
			out[x] = clip8(acc)
		}
		result[yd] = out
	}
	return result
}

func clip8(acc int64) uint8 {
	rounded := (acc + (int64(1) << (precisionBits - 1))) >> precisionBits
	if rounded < 0 {
		return 0
	}
	if rounded > 255 {
		return 255
	}
	return uint8(rounded)
}

func precomputeCoeffs(srcSize, dstSize int) (offsets, lengths []int, weights [][]int32) {
	scale := float64(srcSize) / float64(dstSize)
	filterScale := scale
	if filterScale < 1.0 {
		filterScale = 1.0
	}
	sup := support * filterScale

	offsets = make([]int, dstSize)
	lengths = make([]int, dstSize)
	weights = make([][]int32, dstSize)

	for xd := 0; xd < dstSize; xd++ {
		center := (float64(xd) + 0.5) * scale
		xmin := int(center - sup + 0.5)
		if xmin < 0 {
			xmin = 0
		}
		xmax := int(center + sup + 0.5)
		if xmax > srcSize {
			xmax = srcSize
		}
		n := xmax - xmin
		if n < 0 {
			n = 0
		}

		tmp := make([]float64, n)
		var wsum float64
		for i := 0; i < n; i++ {
			dx := (float64(xmin+i) + 0.5 - center) / filterScale
			w := lanczosKernel(dx)
			tmp[i] = w
			wsum += w
		}
		if wsum != 0.0 {
			for i := 0; i < n; i++ {
				tmp[i] /= wsum
			}
		}
		q := make([]int32, n)
		for i := 0; i < n; i++ {
			q[i] = int32(math.Round(tmp[i] * float64(int64(1)<<precisionBits)))
		}
		offsets[xd] = xmin
		lengths[xd] = n
		weights[xd] = q
	}
	return offsets, lengths, weights
}

func lanczosKernel(x float64) float64 {
	if x == 0 {
		return 1
	}
	ax := math.Abs(x)
	if ax >= support {
		return 0
	}
	px := math.Pi * x
	return (math.Sin(px) / px) * (math.Sin(px/support) / (px / support))
}
