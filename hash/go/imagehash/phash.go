package imagehash

import (
	"fmt"
	"image"
	"sort"

	"github.com/wmetcalf/rosetta-image-hash/go/imagehash/internal/dct"
	"github.com/wmetcalf/rosetta-image-hash/go/imagehash/internal/imgrgb"
	"github.com/wmetcalf/rosetta-image-hash/go/imagehash/internal/lanczos"
)

// SnapEps is the ε threshold for the snap-to-threshold tie-break used by
// PHash, PHashSimple, WHashDb4, and WHashDb4Robust. Coefficients within
// SnapEps of the threshold are deterministically mapped to bit 0 across
// all ports. See spec/SPEC.md §"Threshold tie-break".
const SnapEps = 1e-10

// PHash with default highfreqFactor=4.
func PHash(img image.Image, hashSize int) (Hash, error) {
	return PHashWithFactor(img, hashSize, 4)
}

// PHashWithFactor: grayscale → Lanczos to (N*F, N*F) → 2-D DCT → top-left NxN
// → bit = (coefficient > median + SnapEps).
func PHashWithFactor(img image.Image, hashSize, highfreqFactor int) (Hash, error) {
	if hashSize < 2 {
		return Hash{}, fmt.Errorf("hashSize must be >= 2, got %d", hashSize)
	}
	imgSize := hashSize * highfreqFactor

	rgb := imgrgb.ToRGB(img)
	gray := rgbToGray(rgb)
	resized := lanczos.Resize(gray, imgSize, imgSize)

	doubles := make([][]float64, imgSize)
	for y := 0; y < imgSize; y++ {
		row := make([]float64, imgSize)
		for x := 0; x < imgSize; x++ {
			row[x] = float64(resized[y][x])
		}
		doubles[y] = row
	}
	dctOut := dct.DCT2D(doubles)

	block := make([]float64, hashSize*hashSize)
	k := 0
	for y := 0; y < hashSize; y++ {
		for x := 0; x < hashSize; x++ {
			block[k] = dctOut[y][x]
			k++
		}
	}
	sorted := make([]float64, len(block))
	copy(sorted, block)
	sort.Float64s(sorted)
	var median float64
	if len(sorted)%2 == 1 {
		median = sorted[len(sorted)/2]
	} else {
		median = (sorted[len(sorted)/2-1] + sorted[len(sorted)/2]) / 2.0
	}

	// Snap-to-threshold tie-break: deterministic bit 0 on ties.
	threshold := median + SnapEps
	bits := make([][]bool, hashSize)
	for y := 0; y < hashSize; y++ {
		bits[y] = make([]bool, hashSize)
		for x := 0; x < hashSize; x++ {
			bits[y][x] = dctOut[y][x] > threshold
		}
	}
	return newHashFromBits(bits), nil
}
