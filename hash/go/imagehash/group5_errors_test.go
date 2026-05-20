package imagehash_test

import (
	"image"
	"image/color"
	"testing"

	"github.com/wmetcalf/rosetta-image-hash/go/imagehash"
)

func tiny() image.Image {
	return image.NewRGBA(image.Rect(0, 0, 8, 8))
}

func small() image.Image {
	img := image.NewRGBA(image.Rect(0, 0, 32, 32))
	for y := 0; y < 32; y++ {
		for x := 0; x < 32; x++ {
			img.Set(x, y, color.RGBA{R: 128, G: 64, B: 192, A: 255})
		}
	}
	return img
}

func TestAverageHashRejectsHashSizeBelowTwo(t *testing.T) {
	if _, err := imagehash.AverageHash(tiny(), 1); err == nil {
		t.Errorf("expected error for hashSize=1")
	}
	if _, err := imagehash.AverageHash(tiny(), 0); err == nil {
		t.Errorf("expected error for hashSize=0")
	}
}

func TestDHashRejectsHashSizeBelowTwo(t *testing.T) {
	if _, err := imagehash.DHash(tiny(), 1); err == nil {
		t.Errorf("expected error for hashSize=1")
	}
}

func TestPHashRejectsHashSizeBelowTwo(t *testing.T) {
	if _, err := imagehash.PHash(tiny(), 1); err == nil {
		t.Errorf("expected error for hashSize=1")
	}
}

func TestWHashRejectsHashSizeBelowTwo(t *testing.T) {
	if _, err := imagehash.WHashHaar(small(), 1); err == nil {
		t.Errorf("expected error for hashSize=1")
	}
}

func TestWHashRejectsNonPowerOfTwo(t *testing.T) {
	if _, err := imagehash.WHashHaar(small(), 3); err == nil {
		t.Errorf("expected error for hashSize=3")
	}
	if _, err := imagehash.WHashHaar(small(), 5); err == nil {
		t.Errorf("expected error for hashSize=5")
	}
}

func TestColorHashRejectsBinbitsBelowOne(t *testing.T) {
	if _, err := imagehash.ColorHash(tiny(), 0); err == nil {
		t.Errorf("expected error for binbits=0")
	}
}

func TestHexToHashRejectsNonSquare(t *testing.T) {
	if _, err := imagehash.HexToHash("12345"); err == nil {
		t.Errorf("expected error for non-square hex")
	}
}

func TestHexToHashRejectsInvalidChars(t *testing.T) {
	if _, err := imagehash.HexToHash("xyz!"); err == nil {
		t.Errorf("expected error for invalid hex chars")
	}
}

func TestHexToFlathashRejectsZeroHashSize(t *testing.T) {
	if _, err := imagehash.HexToFlathash("00", 0); err == nil {
		t.Errorf("expected error for hashSize=0")
	}
}
