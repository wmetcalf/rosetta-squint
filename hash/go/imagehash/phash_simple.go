package imagehash

import (
	"fmt"
	"image"

	"github.com/wmetcalf/rosetta-image-hash/go/imagehash/internal/dct"
	"github.com/wmetcalf/rosetta-image-hash/go/imagehash/internal/imgrgb"
	"github.com/wmetcalf/rosetta-image-hash/go/imagehash/internal/lanczos"
)

// PHashSimple with default highfreqFactor=4.
func PHashSimple(img image.Image, hashSize int) (Hash, error) {
	return PHashSimpleWithFactor(img, hashSize, 4)
}

// PHashSimpleWithFactor computes phash_simple.
//
// Unlike PHash (2-D DCT, median threshold), phash_simple applies a 1-D
// DCT row-wise only and uses the mean as the threshold. It also takes a
// different block slice — columns 1 through N (skipping the DC column 0).
//
// This matches the Python imagehash 4.3.2 implementation of phash_simple:
//
//	dct = scipy.fftpack.dct(pixels)     # 1-D DCT along each row
//	dctlowfreq = dct[:hash_size, 1:hash_size+1]
//	avg = dctlowfreq.mean()
//	diff = dctlowfreq > avg
//
// scipy.fftpack.dct(x) for a 2-D array x applies along the last axis (rows).
func PHashSimpleWithFactor(img image.Image, hashSize, highfreqFactor int) (Hash, error) {
	if hashSize < 2 {
		return Hash{}, fmt.Errorf("hashSize must be >= 2, got %d", hashSize)
	}
	imgSize := hashSize * highfreqFactor

	rgb := imgrgb.ToRGB(img)
	gray := rgbToGray(rgb)
	resized := lanczos.Resize(gray, imgSize, imgSize)

	// Apply 1-D DCT row-wise.
	// Uses the same DCT1D that PHash uses (Makhoul FFT trick for power-of-2 sizes,
	// which preserves exact zeros and matches scipy exactly).
	dctRows := make([][]float64, imgSize)
	for y := 0; y < imgSize; y++ {
		row := make([]float64, imgSize)
		for x := 0; x < imgSize; x++ {
			row[x] = float64(resized[y][x])
		}
		dctRows[y] = dct.DCT1D(row)
	}

	// Take dct[:hashSize, 1:hashSize+1]: first hashSize rows, columns 1..hashSize.
	// Column 0 (DC component of each row) is skipped, matching the Python source.
	var sum float64
	block := make([][]float64, hashSize)
	for y := 0; y < hashSize; y++ {
		block[y] = make([]float64, hashSize)
		for x := 0; x < hashSize; x++ {
			v := dctRows[y][x+1]
			block[y][x] = v
			sum += v
		}
	}
	mean := sum / float64(hashSize*hashSize)

	bits := make([][]bool, hashSize)
	for y := 0; y < hashSize; y++ {
		bits[y] = make([]bool, hashSize)
		for x := 0; x < hashSize; x++ {
			bits[y][x] = block[y][x] > mean
		}
	}
	return newHashFromBits(bits), nil
}
