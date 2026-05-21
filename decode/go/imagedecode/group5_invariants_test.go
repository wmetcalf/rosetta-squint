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

func TestGroup5SupportedFormatsOnlyBMP(t *testing.T) {
	supported := SupportedFormats()
	if len(supported) != 1 {
		t.Errorf("expected 1 supported format, got %d", len(supported))
	}
	if len(supported) > 0 && supported[0] != Bmp {
		t.Errorf("expected Bmp, got %v", supported[0])
	}
}
