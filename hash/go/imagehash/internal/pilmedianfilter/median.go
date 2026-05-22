// Package pilmedianfilter implements PIL's ImageFilter.MedianFilter(size=3)
// as a 3×3 windowed median with edge-replication boundary.
//
// For each output pixel (y, x), gather the 9 pixel values from the 3×3 window
// centered at (y, x), clamping at borders. Sort and return index 4 (0-based)
// of the sorted array.
package pilmedianfilter

import "sort"

// Filter applies PIL MedianFilter(size=3) to a grayscale uint8 image
// represented as a [H][W] slice. Returns a new [H][W] slice.
func Filter(src [][]uint8) [][]uint8 {
	h := len(src)
	if h == 0 {
		return nil
	}
	w := len(src[0])
	if w == 0 {
		return nil
	}

	out := make([][]uint8, h)
	for y := 0; y < h; y++ {
		out[y] = make([]uint8, w)
	}

	var win [9]uint8

	for y := 0; y < h; y++ {
		for x := 0; x < w; x++ {
			i := 0
			for dy := -1; dy <= 1; dy++ {
				for dx := -1; dx <= 1; dx++ {
					cy := y + dy
					cx := x + dx
					if cy < 0 {
						cy = 0
					} else if cy >= h {
						cy = h - 1
					}
					if cx < 0 {
						cx = 0
					} else if cx >= w {
						cx = w - 1
					}
					win[i] = src[cy][cx]
					i++
				}
			}
			// Sort 9 values, take the median (index 4)
			sorted := win
			sort.Slice(sorted[:], func(a, b int) bool { return sorted[a] < sorted[b] })
			out[y][x] = sorted[4]
		}
	}
	return out
}
