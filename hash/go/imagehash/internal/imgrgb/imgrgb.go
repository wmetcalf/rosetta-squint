// Package imgrgb normalizes any image.Image to a canonical [H][W][3]uint8
// row-major RGB buffer. Non-opaque sources are composited on opaque black
// to match Pillow's Image.convert('RGB') behavior.
package imgrgb

import (
	"image"
	"image/draw"
)

func ToRGB(img image.Image) [][][3]uint8 {
	bounds := img.Bounds()
	w := bounds.Dx()
	h := bounds.Dy()

	dst := image.NewRGBA(image.Rect(0, 0, w, h))
	// Initialize all pixels to opaque black so draw.Over composites against black background.
	for i := 3; i < len(dst.Pix); i += 4 {
		dst.Pix[i] = 255
	}
	draw.Draw(dst, dst.Bounds(), img, bounds.Min, draw.Over)

	out := make([][][3]uint8, h)
	for y := 0; y < h; y++ {
		row := make([][3]uint8, w)
		for x := 0; x < w; x++ {
			i := dst.PixOffset(x, y)
			row[x] = [3]uint8{dst.Pix[i], dst.Pix[i+1], dst.Pix[i+2]}
		}
		out[y] = row
	}
	return out
}
