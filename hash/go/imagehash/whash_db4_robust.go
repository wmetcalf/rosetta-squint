package imagehash

import (
	"fmt"
	"image"
	"math"
	"sort"

	"github.com/wmetcalf/rosetta-squint/hash/go/imagehash/internal/db4"
	"github.com/wmetcalf/rosetta-squint/hash/go/imagehash/internal/haar"
	"github.com/wmetcalf/rosetta-squint/hash/go/imagehash/internal/imgrgb"
	"github.com/wmetcalf/rosetta-squint/hash/go/imagehash/internal/lanczos"
)

// WHashDb4RobustEps is the ε threshold for snap-to-zero in WHashDb4Robust.
const WHashDb4RobustEps = 1e-12

// WHashDb4Robust is a cross-port-stable variant of WHashDb4. Identical
// pipeline up to the LL band, then snaps coefficients with |c| < WHashDb4RobustEps
// to exactly zero before median + threshold. Real-world photos produce the
// same hash as WHashDb4; pathological symmetric inputs produce a deterministic
// hash across all ports (NOT byte-exact-compatible with Python imagehash on
// those inputs). See spec/SPEC.md §whash_db4_robust.
func WHashDb4Robust(img image.Image, hashSize int) (Hash, error) {
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

	// Step 7-9: Haar-based LL removal (always Haar, regardless of hash mode).
	haarDec := haar.Wavedec2(pixels, llMaxLevel)
	for y := range haarDec.CA {
		for x := range haarDec.CA[y] {
			haarDec.CA[y][x] = 0
		}
	}
	modified := haar.Waverec2(haarDec)

	// Step 10: db4 decomposition at dwtLevel.
	dec := db4.Wavedec2(modified, dwtLevel)
	ll := dec.CA

	// Snap near-zero coefficients to exactly zero before median + threshold.
	for y := range ll {
		for x := range ll[y] {
			if math.Abs(ll[y][x]) < WHashDb4RobustEps {
				ll[y][x] = 0.0
			}
		}
	}

	// Step 12: median threshold with snap-to-threshold tie-break.
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

	// Snap-to-threshold tie-break (on top of snap-to-zero): deterministic
	// bit 0 on ties. See spec/SPEC.md §"Threshold tie-break".
	threshold := median + SnapEps
	bits := make([][]bool, len(ll))
	for y := range ll {
		bits[y] = make([]bool, len(ll[y]))
		for x := range ll[y] {
			bits[y][x] = ll[y][x] > threshold
		}
	}
	return newHashFromBits(bits), nil
}
