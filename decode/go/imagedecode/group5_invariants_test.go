package imagedecode

import "testing"

func TestGroup5AllDecodedImagesHaveValidShape(t *testing.T) {
	fixtures := listValidFixtures(t, "bmp")
	for _, rel := range fixtures {
		bytes := readFixture(t, rel)
		img, err := Decode(bytes)
		if err != nil {
			t.Errorf("%s: unexpected error: %v", rel, err)
			continue
		}
		if img.Width <= 0 || img.Height <= 0 {
			t.Errorf("%s: bad dimensions %dx%d", rel, img.Width, img.Height)
		}
		if img.Format != Bmp {
			t.Errorf("%s: format should be Bmp, got %v", rel, img.Format)
		}
		expectedBytes := img.Width * img.Height * img.Channels.BytesPerPixel()
		if len(img.Data) != expectedBytes {
			t.Errorf("%s: data length %d != expected %d", rel, len(img.Data), expectedBytes)
		}
		if img.Channels != RGB && img.Channels != RGBA {
			t.Errorf("%s: invalid channels %v", rel, img.Channels)
		}
	}
}

func TestGroup5SupportedFormatsBmpAndPng(t *testing.T) {
	supported := SupportedFormats()
	if len(supported) != 2 {
		t.Errorf("expected 2 supported formats, got %d", len(supported))
	}
	hasBmp, hasPng := false, false
	for _, f := range supported {
		if f == Bmp {
			hasBmp = true
		}
		if f == Png {
			hasPng = true
		}
	}
	if !hasBmp || !hasPng {
		t.Errorf("expected Bmp + Png in supportedFormats, got %v", supported)
	}
}

func TestGroup5AllDecodedPngImagesHaveValidShape(t *testing.T) {
	fixtures := listValidFixtures(t, "png")
	for _, rel := range fixtures {
		b := readFixture(t, rel)
		img, err := Decode(b)
		if err != nil {
			t.Errorf("%s: unexpected error: %v", rel, err)
			continue
		}
		if img.Width <= 0 || img.Height <= 0 {
			t.Errorf("%s: bad dimensions %dx%d", rel, img.Width, img.Height)
		}
		if img.Format != Png {
			t.Errorf("%s: format should be Png, got %v", rel, img.Format)
		}
		expectedBytes := img.Width * img.Height * img.Channels.BytesPerPixel()
		if len(img.Data) != expectedBytes {
			t.Errorf("%s: data length %d != expected %d", rel, len(img.Data), expectedBytes)
		}
	}
}
