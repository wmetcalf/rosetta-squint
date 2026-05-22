package imagehash

import (
	"fmt"
	"image"

	"github.com/wmetcalf/rosetta-image-hash/go/imagehash/internal/imgrgb"
	"github.com/wmetcalf/rosetta-image-hash/go/imagehash/internal/pilgray"
	"github.com/wmetcalf/rosetta-image-hash/go/imagehash/internal/pilhsv"
)

func ColorHash(img image.Image, binbits int) (Hash, error) {
	if binbits < 1 {
		return Hash{}, fmt.Errorf("binbits must be >= 1, got %d", binbits)
	}
	rgb := imgrgb.ToRGB(img)
	h := len(rgb)
	w := len(rgb[0])
	n := int64(w) * int64(h)

	var blackCount, grayCount, colorfulCount int64
	var faintBins, brightBins [6]int64

	for y := 0; y < h; y++ {
		for x := 0; x < w; x++ {
			r, g, b := rgb[y][x][0], rgb[y][x][1], rgb[y][x][2]
			l := int(pilgray.ToGray(r, g, b))
			if l < 32 {
				blackCount++
				continue
			}
			hueByte, s, _ := pilhsv.ToHSV(r, g, b)
			if int(s) < 85 {
				grayCount++
				continue
			}
			colorfulCount++
			hueBin := int(hueByte) * 6 / 255
			if hueBin > 5 {
				hueBin = 5
			}
			switch {
			case int(s) < 170:
				faintBins[hueBin]++
			case int(s) > 170:
				brightBins[hueBin]++
			}
		}
	}

	maxVal := 1 << binbits
	c := colorfulCount
	if c < 1 {
		c = 1
	}
	values := make([]int, 14)
	values[0] = clipBin(int(float64(blackCount)/float64(n)*float64(maxVal)), maxVal-1)
	values[1] = clipBin(int(float64(grayCount)/float64(n)*float64(maxVal)), maxVal-1)
	for i := 0; i < 6; i++ {
		values[2+i] = clipBin(int(float64(faintBins[i])*float64(maxVal)/float64(c)), maxVal-1)
		values[8+i] = clipBin(int(float64(brightBins[i])*float64(maxVal)/float64(c)), maxVal-1)
	}

	bits := make([][]bool, 14)
	for i := 0; i < 14; i++ {
		bits[i] = ColorhashBinEncode(values[i], binbits)
	}
	return newHashFromBits(bits), nil
}

// ColorhashBinEncode is exported so the Group-1 binEncode test can verify the
// SPEC.md §8 quirky encoding in isolation. v=8 → [1,1,0,0] (0xc), NOT [1,0,0,0] (0x8).
func ColorhashBinEncode(v, binbits int) []bool {
	bits := make([]bool, binbits)
	for i := 0; i < binbits; i++ {
		shifted := uint(v) >> (binbits - i - 1)
		masked := shifted & ((1 << (binbits - i)) - 1)
		bits[i] = masked > 0
	}
	return bits
}

func clipBin(v, max int) int {
	if v > max {
		return max
	}
	return v
}
