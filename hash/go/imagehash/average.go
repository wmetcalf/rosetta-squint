package imagehash

import (
	"fmt"
	"image"

	"github.com/wmetcalf/rosetta-squint/hash/go/imagehash/internal/imgrgb"
	"github.com/wmetcalf/rosetta-squint/hash/go/imagehash/internal/lanczos"
	"github.com/wmetcalf/rosetta-squint/hash/go/imagehash/internal/pilgray"
)

func AverageHash(img image.Image, hashSize int) (Hash, error) {
	if hashSize < 2 {
		return Hash{}, fmt.Errorf("hashSize must be >= 2, got %d", hashSize)
	}
	rgb := imgrgb.ToRGB(img)
	gray := rgbToGray(rgb)
	resized := lanczos.Resize(gray, hashSize, hashSize)

	var sum int64
	for y := 0; y < hashSize; y++ {
		for x := 0; x < hashSize; x++ {
			sum += int64(resized[y][x])
		}
	}
	avg := float64(sum) / float64(hashSize*hashSize)

	bits := make([][]bool, hashSize)
	for y := 0; y < hashSize; y++ {
		bits[y] = make([]bool, hashSize)
		for x := 0; x < hashSize; x++ {
			bits[y][x] = float64(resized[y][x]) > avg
		}
	}
	return newHashFromBits(bits), nil
}

func rgbToGray(rgb [][][3]uint8) [][]uint8 {
	h := len(rgb)
	w := len(rgb[0])
	out := make([][]uint8, h)
	for y := 0; y < h; y++ {
		row := make([]uint8, w)
		for x := 0; x < w; x++ {
			row[x] = pilgray.ToGray(rgb[y][x][0], rgb[y][x][1], rgb[y][x][2])
		}
		out[y] = row
	}
	return out
}
