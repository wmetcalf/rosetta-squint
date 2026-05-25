package imagehash

import (
	"image"
	"image/color"
	"math"
	"sort"

	"github.com/wmetcalf/rosetta-image-hash/go/imagehash/internal/findsegments"
	"github.com/wmetcalf/rosetta-image-hash/go/imagehash/internal/imgrgb"
	"github.com/wmetcalf/rosetta-image-hash/go/imagehash/internal/lanczos"
	"github.com/wmetcalf/rosetta-image-hash/go/imagehash/internal/pilgaussianblur"
	"github.com/wmetcalf/rosetta-image-hash/go/imagehash/internal/pilgray"
	"github.com/wmetcalf/rosetta-image-hash/go/imagehash/internal/pilmedianfilter"
)

// sortSegsByLengthDesc sorts segments by pixel count descending, with stable
// ordering so equal-sized segments preserve discovery order for cross-port
// determinism. Matches Python `sorted(segs, key=len, reverse=True)`.
func sortSegsByLengthDesc(segs []findsegments.Segment) {
	sort.SliceStable(segs, func(i, j int) bool {
		return len(segs[i]) > len(segs[j])
	})
}

// pilRound implements Python's built-in round() (banker's rounding / round-half-to-even)
// applied to a float64 value, matching PIL's _crop: map(int, map(round, box)).
func pilRound(x float64) int {
	// math.Round rounds half away from zero; Python's round() rounds half to even.
	floor := math.Floor(x)
	frac := x - floor
	if frac == 0.5 {
		// Round half to even
		if int(floor)%2 == 0 {
			return int(floor)
		}
		return int(floor) + 1
	}
	return int(math.Round(x))
}

const (
	cropResistantSegSize  = 300
	cropResistantThresh   = float32(128)
	cropResistantMinSize  = 500
	cropResistantHashSize = 8
)

// CropResistantHash computes an ImageMultiHash by segmenting the image into
// bright/dark regions via a smoothed grayscale copy, then hashing each segment's
// bounding box in the original image using DHash.
//
// Implements the algorithm from "Efficient Cropping-Resistant Robust Image Hashing"
// (DOI 10.1109/ARES.2014.85), matching Python imagehash.crop_resistant_hash with
// default parameters: hash_func=dhash, segment_threshold=128,
// min_segment_size=500, segmentation_image_size=300.
//
// limitSegments: pass a non-nil *int to keep only the N largest segments
// (matches Python `sorted(segments, key=len, reverse=True)[:N]`). Pass nil
// for the Python default (no limit).
func CropResistantHash(img image.Image, limitSegments *int) (ImageMultiHash, error) {
	// Step 1: keep original for per-segment cropping.
	origBounds := img.Bounds()
	origW := origBounds.Dx()
	origH := origBounds.Dy()

	// Step 2: grayscale + Lanczos resize to 300×300.
	rgb := imgrgb.ToRGB(img)
	grayFull := make([][]uint8, origH)
	for y := 0; y < origH; y++ {
		grayFull[y] = make([]uint8, origW)
		for x := 0; x < origW; x++ {
			grayFull[y][x] = pilgray.ToGray(rgb[y][x][0], rgb[y][x][1], rgb[y][x][2])
		}
	}
	segImg := lanczos.Resize(grayFull, cropResistantSegSize, cropResistantSegSize)

	// Step 3: GaussianBlur(radius=2).
	blurred := pilgaussianblur.Blur(segImg)

	// Step 4: MedianFilter(size=3).
	filtered := pilmedianfilter.Filter(blurred)

	// Step 5: convert to float32.
	pixelsF := make([][]float32, cropResistantSegSize)
	for y := 0; y < cropResistantSegSize; y++ {
		row := make([]float32, cropResistantSegSize)
		for x := 0; x < cropResistantSegSize; x++ {
			row[x] = float32(filtered[y][x])
		}
		pixelsF[y] = row
	}

	// Step 6: find segments.
	segs := findsegments.FindAllSegments(pixelsF, cropResistantThresh, cropResistantMinSize)

	// Step 7: if no segments, synthesize whole-image segment.
	if len(segs) == 0 {
		wholeImg := findsegments.Segment{
			{Y: 0, X: 0},
			{Y: cropResistantSegSize - 1, X: cropResistantSegSize - 1},
		}
		segs = []findsegments.Segment{wholeImg}
	}

	// Step 8: optional limit — keep the N largest by pixel count.
	// Matches Python: sorted(segments, key=len, reverse=True)[:N].
	// sort.SliceStable preserves discovery order for equal-sized segments,
	// which keeps cross-port output deterministic when sizes tie.
	if limitSegments != nil && *limitSegments < len(segs) {
		sortLimit := *limitSegments
		sortSegsByLengthDesc(segs)
		segs = segs[:sortLimit]
	}

	// Step 9: for each segment, compute bounding box, scale to original, crop, DHash.
	scaleW := float64(origW) / float64(cropResistantSegSize)
	scaleH := float64(origH) / float64(cropResistantSegSize)

	hashes := make([]Hash, 0, len(segs))
	for _, seg := range segs {
		// Bounding box in segmentation coords.
		minY, minX := seg[0].Y, seg[0].X
		maxY, maxX := seg[0].Y, seg[0].X
		for _, p := range seg[1:] {
			if p.Y < minY {
				minY = p.Y
			}
			if p.Y > maxY {
				maxY = p.Y
			}
			if p.X < minX {
				minX = p.X
			}
			if p.X > maxX {
				maxX = p.X
			}
		}

		// Scale to original image coordinates.
		// PIL.Image.crop internally does: map(int, map(round, box))
		// where round() is Python's banker's rounding (round half to even).
		// We must replicate this exactly.
		cropLeft := pilRound(float64(minX) * scaleW)
		cropTop := pilRound(float64(minY) * scaleH)
		cropRight := pilRound(float64(maxX+1) * scaleW)
		cropBottom := pilRound(float64(maxY+1) * scaleH)

		// Clamp to image bounds.
		if cropLeft < 0 {
			cropLeft = 0
		}
		if cropTop < 0 {
			cropTop = 0
		}
		if cropRight > origW {
			cropRight = origW
		}
		if cropBottom > origH {
			cropBottom = origH
		}

		// Crop original image.
		cropRect := image.Rect(
			origBounds.Min.X+cropLeft,
			origBounds.Min.Y+cropTop,
			origBounds.Min.X+cropRight,
			origBounds.Min.Y+cropBottom,
		)
		cropped := cropImage(img, cropRect)

		// DHash with default hash_size=8.
		h, err := DHash(cropped, cropResistantHashSize)
		if err != nil {
			return ImageMultiHash{}, err
		}
		hashes = append(hashes, h)
	}

	return ImageMultiHash{SegmentHashes: hashes}, nil
}

// cropImage crops img to rect. Uses SubImage if available, otherwise copies pixels.
func cropImage(img image.Image, rect image.Rectangle) image.Image {
	type subImager interface {
		SubImage(image.Rectangle) image.Image
	}
	if si, ok := img.(subImager); ok {
		return si.SubImage(rect)
	}
	// Fallback: copy pixels into a new NRGBA image.
	dst := image.NewNRGBA(image.Rect(0, 0, rect.Dx(), rect.Dy()))
	for y := rect.Min.Y; y < rect.Max.Y; y++ {
		for x := rect.Min.X; x < rect.Max.X; x++ {
			r, g, b, a := img.At(x, y).RGBA()
			dst.Set(x-rect.Min.X, y-rect.Min.Y, color.NRGBA{
				R: uint8(r >> 8),
				G: uint8(g >> 8),
				B: uint8(b >> 8),
				A: uint8(a >> 8),
			})
		}
	}
	return dst
}
