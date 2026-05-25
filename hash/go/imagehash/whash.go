package imagehash

import (
	"fmt"
	"image"
	"math"
	"sort"

	"github.com/wmetcalf/rosetta-squint/hash/go/imagehash/internal/haar"
	"github.com/wmetcalf/rosetta-squint/hash/go/imagehash/internal/imgrgb"
	"github.com/wmetcalf/rosetta-squint/hash/go/imagehash/internal/lanczos"
)

func WHashHaar(img image.Image, hashSize int) (Hash, error) {
	if hashSize < 2 {
		return Hash{}, fmt.Errorf("hashSize must be >= 2, got %d", hashSize)
	}
	if !isPowerOfTwo(hashSize) {
		return Hash{}, fmt.Errorf("hashSize must be a power of 2 for whash, got %d", hashSize)
	}

	rgb := imgrgb.ToRGB(img)
	gray := rgbToGray(rgb)
	h := len(gray)
	w := len(gray[0])

	minSide := w
	if h < w {
		minSide = h
	}
	imageNaturalScale := 1 << int(math.Floor(math.Log2(float64(minSide))))
	imageScale := imageNaturalScale
	if hashSize > imageScale {
		imageScale = hashSize
	}

	llMaxLevel := int(math.Log2(float64(imageScale)))
	level := int(math.Log2(float64(hashSize)))
	if level > llMaxLevel {
		return Hash{}, fmt.Errorf("hashSize too large for image (level=%d > ll_max_level=%d)", level, llMaxLevel)
	}
	dwtLevel := llMaxLevel - level

	resized := lanczos.Resize(gray, imageScale, imageScale)
	pixels := make([][]float64, imageScale)
	for y := 0; y < imageScale; y++ {
		row := make([]float64, imageScale)
		for x := 0; x < imageScale; x++ {
			row[x] = float64(resized[y][x]) / 255.0
		}
		pixels[y] = row
	}

	fullDec := haar.Wavedec2(pixels, llMaxLevel)
	for y := range fullDec.CA {
		for x := range fullDec.CA[y] {
			fullDec.CA[y][x] = 0
		}
	}
	modified := haar.Waverec2(fullDec)

	dec := haar.Wavedec2(modified, dwtLevel)
	ll := dec.CA

	n := len(ll) * len(ll[0])
	flat := make([]float64, 0, n)
	for y := range ll {
		flat = append(flat, ll[y]...)
	}
	sorted := make([]float64, n)
	copy(sorted, flat)
	sort.Float64s(sorted)
	var median float64
	if n%2 == 1 {
		median = sorted[n/2]
	} else {
		median = (sorted[n/2-1] + sorted[n/2]) / 2.0
	}

	bits := make([][]bool, len(ll))
	for y := range ll {
		bits[y] = make([]bool, len(ll[y]))
		for x := range ll[y] {
			bits[y][x] = ll[y][x] > median
		}
	}
	return newHashFromBits(bits), nil
}

func isPowerOfTwo(n int) bool { return n > 0 && (n&(n-1)) == 0 }
