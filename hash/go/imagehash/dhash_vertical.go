package imagehash

import (
	"fmt"
	"image"

	"github.com/wmetcalf/rosetta-squint/hash/go/imagehash/internal/imgrgb"
	"github.com/wmetcalf/rosetta-squint/hash/go/imagehash/internal/lanczos"
)

// DHashVertical computes dhash_vertical: grayscale → Lanczos resize to (W=N, H=N+1) →
// column-wise adjacent-row diff with strict >.
//
// This preserves the pre-3.0 imagehash behavior (vertical instead of horizontal
// pixel comparisons) for users with stored hashes from that era.
func DHashVertical(img image.Image, hashSize int) (Hash, error) {
	if hashSize < 2 {
		return Hash{}, fmt.Errorf("hashSize must be >= 2, got %d", hashSize)
	}
	rgb := imgrgb.ToRGB(img)
	gray := rgbToGray(rgb)
	// Resize to (width=N, height=N+1): compare vertically adjacent pixels.
	resized := lanczos.Resize(gray, hashSize, hashSize+1)
	bits := make([][]bool, hashSize)
	for y := 0; y < hashSize; y++ {
		bits[y] = make([]bool, hashSize)
		for x := 0; x < hashSize; x++ {
			bits[y][x] = resized[y+1][x] > resized[y][x]
		}
	}
	return newHashFromBits(bits), nil
}
