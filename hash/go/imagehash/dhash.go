package imagehash

import (
	"fmt"
	"image"

	"github.com/wmetcalf/rosetta-image-hash/go/imagehash/internal/imgrgb"
	"github.com/wmetcalf/rosetta-image-hash/go/imagehash/internal/lanczos"
)

// DHash computes dhash: grayscale → Lanczos resize to (W=N+1, H=N) →
// row-wise adjacent-column diff with strict >.
func DHash(img image.Image, hashSize int) (Hash, error) {
	if hashSize < 2 {
		return Hash{}, fmt.Errorf("hashSize must be >= 2, got %d", hashSize)
	}
	rgb := imgrgb.ToRGB(img)
	gray := rgbToGray(rgb)
	resized := lanczos.Resize(gray, hashSize+1, hashSize)
	bits := make([][]bool, hashSize)
	for y := 0; y < hashSize; y++ {
		bits[y] = make([]bool, hashSize)
		for x := 0; x < hashSize; x++ {
			bits[y][x] = resized[y][x+1] > resized[y][x]
		}
	}
	return newHashFromBits(bits), nil
}
