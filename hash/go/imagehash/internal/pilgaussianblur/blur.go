// Package pilgaussianblur implements PIL's ImageFilter.GaussianBlur(radius=2)
// as a 3-pass separable box blur, matching Pillow 10.4.0's BoxBlur.c exactly.
//
// Algorithm:
//   - _gaussian_blur_radius(sigma=2, passes=3) → floatRadius = 1.375
//   - radius = int(floatRadius) = 1  (truncation, not rounding)
//   - ww = (1<<24) / (floatRadius*2 + 1)  (fixed-point weight for full pixels)
//   - fw = ((1<<24) - (radius*2+1)*ww) / 2  (fixed-point weight for boundary pixels)
//   - All `passes` horizontal box-blur passes applied first (accumulator over rows),
//     then all `passes` vertical passes (accumulator over columns).
//   - Boundary mode: edge replication (clamp).
//   - Rounding: (bulk + (1<<23)) >> 24 after each output pixel.
package pilgaussianblur

import (
	"math"
)

const defaultSigma = 2.0
const defaultPasses = 3

// gaussianBlurRadius computes the per-pass float box radius for a Gaussian
// approximated by `passes` equal box filters, matching Pillow's _gaussian_blur_radius.
func gaussianBlurRadius(sigma float64, passes int) float64 {
	sigma2 := sigma * sigma / float64(passes)
	L := math.Sqrt(12.0*sigma2 + 1.0)
	l := math.Floor((L - 1.0) / 2.0)
	a := (2*l+1)*(l*(l+1)-3*sigma2) / (6 * (sigma2 - (l+1)*(l+1)))
	return l + a
}

// boxBlurLine applies a single 1-D box blur pass to `line` (length n) using
// the given float radius, edge-replication boundary, and Pillow's fixed-point
// rounding. The result is written into `out` (must be length n).
func boxBlurLine(line []uint8, floatRadius float64, n int, out []uint8) {
	radius := int(floatRadius) // truncate toward zero
	// Compute fixed-point weights matching Pillow's BoxBlur.c:
	//   ww = (UINT32)(1<<24) / (floatRadius * 2 + 1)
	// floatRadius*2+1 must be kept as float64; casting to uint64 first would truncate.
	ww := uint64(float64(1<<24) / (floatRadius*2 + 1))
	fw := (uint64(1<<24) - uint64(radius*2+1)*ww) / 2

	lastx := n - 1
	edgeA := radius + 1
	if edgeA > n {
		edgeA = n
	}
	edgeB := n - radius - 1
	if edgeB < 0 {
		edgeB = 0
	}

	// Initialize accumulator:
	// acc = line[0]*(radius+1) + sum(line[0..edgeA-2]) + line[lastx]*(radius-edgeA+1)
	var acc uint64
	acc = uint64(line[0]) * uint64(radius+1)
	for x := 0; x < edgeA-1; x++ {
		acc += uint64(line[x])
	}
	// radius - edgeA + 1 may be 0 when edgeA == radius+1
	if rem := radius - edgeA + 1; rem > 0 {
		acc += uint64(line[lastx]) * uint64(rem)
	}

	save := func(bulk uint64) uint8 {
		v := (bulk + (1 << 23)) >> 24
		if v > 255 {
			return 255
		}
		return uint8(v)
	}

	// Region 1: x in [0, edgeA)
	for x := 0; x < edgeA; x++ {
		right := x + radius
		if right > lastx {
			right = lastx
		}
		acc += uint64(line[right]) - uint64(line[0])
		rightF := x + radius + 1
		if rightF > lastx {
			rightF = lastx
		}
		bulk := acc*ww + (uint64(line[0])+uint64(line[rightF]))*fw
		out[x] = save(bulk)
	}

	// Region 2: x in [edgeA, edgeB)
	for x := edgeA; x < edgeB; x++ {
		right := x + radius
		if right > lastx {
			right = lastx
		}
		acc += uint64(line[right]) - uint64(line[x-radius-1])
		rightF := x + radius + 1
		if rightF > lastx {
			rightF = lastx
		}
		bulk := acc*ww + (uint64(line[x-radius-1])+uint64(line[rightF]))*fw
		out[x] = save(bulk)
	}

	// Region 3: x in [edgeB, lastx]
	for x := edgeB; x <= lastx; x++ {
		acc += uint64(line[lastx]) - uint64(line[x-radius-1])
		bulk := acc*ww + (uint64(line[x-radius-1])+uint64(line[lastx]))*fw
		out[x] = save(bulk)
	}
}

// Blur applies PIL GaussianBlur(radius=2) to a grayscale uint8 image represented
// as a [H][W] slice. Returns a new [H][W] slice.
func Blur(src [][]uint8) [][]uint8 {
	h := len(src)
	if h == 0 {
		return nil
	}
	w := len(src[0])
	if w == 0 {
		return nil
	}

	floatRadius := gaussianBlurRadius(defaultSigma, defaultPasses)

	// Allocate flat buffer for intermediate results.
	buf := make([]uint8, h*w)
	tmp := make([]uint8, h*w)

	// Copy src into buf
	for y := 0; y < h; y++ {
		copy(buf[y*w:(y+1)*w], src[y])
	}

	lineOut := make([]uint8, w)

	// All horizontal passes first
	for pass := 0; pass < defaultPasses; pass++ {
		for y := 0; y < h; y++ {
			boxBlurLine(buf[y*w:(y+1)*w], floatRadius, w, lineOut)
			copy(tmp[y*w:(y+1)*w], lineOut)
		}
		buf, tmp = tmp, buf
	}

	// Allocate column buffers
	colIn := make([]uint8, h)
	colOut := make([]uint8, h)

	// All vertical passes
	for pass := 0; pass < defaultPasses; pass++ {
		for x := 0; x < w; x++ {
			for y := 0; y < h; y++ {
				colIn[y] = buf[y*w+x]
			}
			boxBlurLine(colIn, floatRadius, h, colOut)
			for y := 0; y < h; y++ {
				tmp[y*w+x] = colOut[y]
			}
		}
		buf, tmp = tmp, buf
	}

	// Build output
	out := make([][]uint8, h)
	for y := 0; y < h; y++ {
		row := make([]uint8, w)
		copy(row, buf[y*w:(y+1)*w])
		out[y] = row
	}
	return out
}
