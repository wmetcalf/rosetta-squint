package imgrgb_test

import (
	"image"
	"image/color"
	"image/draw"
	"testing"

	"github.com/wmetcalf/rosetta-squint/hash/go/imagehash/internal/imgrgb"
)

func TestRGBAPassthrough(t *testing.T) {
	src := image.NewRGBA(image.Rect(0, 0, 2, 1))
	src.Set(0, 0, color.RGBA{R: 255, G: 0, B: 0, A: 255})
	src.Set(1, 0, color.RGBA{R: 0, G: 255, B: 0, A: 255})
	rgb := imgrgb.ToRGB(src)
	if len(rgb) != 1 || len(rgb[0]) != 2 {
		t.Fatalf("shape: %dx%d", len(rgb), len(rgb[0]))
	}
	if rgb[0][0] != [3]uint8{255, 0, 0} {
		t.Errorf("(0,0): got %v, want [255,0,0]", rgb[0][0])
	}
	if rgb[0][1] != [3]uint8{0, 255, 0} {
		t.Errorf("(0,1): got %v, want [0,255,0]", rgb[0][1])
	}
}

func TestNRGBATransparentCompositesOnBlack(t *testing.T) {
	src := image.NewNRGBA(image.Rect(0, 0, 1, 1))
	src.Set(0, 0, color.NRGBA{R: 255, G: 255, B: 255, A: 0})
	rgb := imgrgb.ToRGB(src)
	if rgb[0][0] != [3]uint8{0, 0, 0} {
		t.Errorf("transparent should composite on black; got %v", rgb[0][0])
	}
}

func TestNRGBASemiTransparent(t *testing.T) {
	src := image.NewNRGBA(image.Rect(0, 0, 1, 1))
	src.Set(0, 0, color.NRGBA{R: 255, G: 0, B: 0, A: 128})
	rgb := imgrgb.ToRGB(src)
	if (rgb[0][0][1] != 0) || (rgb[0][0][2] != 0) {
		t.Errorf("G/B should be 0; got %v", rgb[0][0])
	}
	if rgb[0][0][0] < 50 || rgb[0][0][0] > 200 {
		t.Errorf("R should be in (50,200), got %d", rgb[0][0][0])
	}
}

func TestPalettedExpansion(t *testing.T) {
	palette := color.Palette{
		color.RGBA{R: 0, G: 0, B: 0, A: 255},
		color.RGBA{R: 200, G: 100, B: 50, A: 255},
	}
	src := image.NewPaletted(image.Rect(0, 0, 1, 1), palette)
	src.SetColorIndex(0, 0, 1)
	rgb := imgrgb.ToRGB(src)
	if rgb[0][0] != [3]uint8{200, 100, 50} {
		t.Errorf("palette expansion: got %v, want [200,100,50]", rgb[0][0])
	}
}

func TestGrayReplicates(t *testing.T) {
	src := image.NewGray(image.Rect(0, 0, 1, 1))
	src.SetGray(0, 0, color.Gray{Y: 128})
	rgb := imgrgb.ToRGB(src)
	// Gray (128) drawn onto a black RGB will composite to roughly (128,128,128).
	// Allow slight variation if image/draw applies any gamma — but for image.Gray
	// onto image.RGBA with draw.Over, alpha is opaque so result should be exactly (128,128,128).
	if rgb[0][0] != [3]uint8{128, 128, 128} {
		t.Errorf("gray replicate: got %v, want [128,128,128]", rgb[0][0])
	}
}

func TestShapeMatchesImageSize(t *testing.T) {
	src := image.NewRGBA(image.Rect(0, 0, 5, 3))
	rgb := imgrgb.ToRGB(src)
	if len(rgb) != 3 || len(rgb[0]) != 5 {
		t.Errorf("shape: got %dx%d, want 3x5", len(rgb), len(rgb[0]))
	}
}

func TestDrawableEquivalence(t *testing.T) {
	src := image.NewNRGBA(image.Rect(0, 0, 1, 1))
	src.Set(0, 0, color.NRGBA{R: 100, G: 150, B: 200, A: 255})
	rgb := imgrgb.ToRGB(src)
	if rgb[0][0] != [3]uint8{100, 150, 200} {
		t.Errorf("opaque NRGBA: got %v, want [100,150,200]", rgb[0][0])
	}

	dst := image.NewNRGBA(src.Bounds())
	draw.Draw(dst, dst.Bounds(), src, image.Point{}, draw.Src)
	if dst.NRGBAAt(0, 0).R != 100 {
		t.Errorf("draw sanity: %d", dst.NRGBAAt(0, 0).R)
	}
}
