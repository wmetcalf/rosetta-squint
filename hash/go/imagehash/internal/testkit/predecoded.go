package testkit

import (
	"encoding/binary"
	"image"
	"os"
	"testing"
)

// LoadPreDecodedFromRoot reads spec/decoded/<name>.rgb.bin and returns it as
// a *image.NRGBA with canonical RGB layout (A=255 in every pixel).
func LoadPreDecodedFromRoot(t *testing.T, name string) image.Image {
	t.Helper()
	return loadPreDecoded(t, DirRoot(), name)
}

// LoadPreDecodedFromInternal is the same, for internal/* tests.
func LoadPreDecodedFromInternal(t *testing.T, name string) image.Image {
	t.Helper()
	return loadPreDecoded(t, DirInternal(), name)
}

func loadPreDecoded(t *testing.T, specDir, name string) image.Image {
	t.Helper()
	path := specDir + "/decoded/" + name + ".rgb.bin"
	data, err := os.ReadFile(path)
	if err != nil {
		t.Fatalf("read %s: %v", path, err)
	}
	if len(data) < 8 {
		t.Fatalf("decoded file %s too short: %d bytes", name, len(data))
	}
	w := int(binary.LittleEndian.Uint32(data[0:4]))
	h := int(binary.LittleEndian.Uint32(data[4:8]))
	expected := 8 + w*h*3
	if len(data) != expected {
		t.Fatalf("decoded %s length mismatch: got %d, expected %d", name, len(data), expected)
	}
	img := image.NewNRGBA(image.Rect(0, 0, w, h))
	off := 8
	for y := 0; y < h; y++ {
		for x := 0; x < w; x++ {
			i := img.PixOffset(x, y)
			img.Pix[i+0] = data[off]
			img.Pix[i+1] = data[off+1]
			img.Pix[i+2] = data[off+2]
			img.Pix[i+3] = 255
			off += 3
		}
	}
	return img
}
