package imagedecode

import (
	"testing"
)

func TestGroup3DetectsAllValidBmpFixtures(t *testing.T) {
	fixtures := listValidFixtures(t, "bmp")
	for _, rel := range fixtures {
		bytes := readFixture(t, rel)
		f, ok := DetectFormat(bytes)
		if !ok || f != Bmp {
			t.Errorf("%s: expected Bmp detection, got %v ok=%v", rel, f, ok)
		}
	}
}

func TestGroup3RejectsBadSignature(t *testing.T) {
	bytes := readFixture(t, "bmp/invalid/bad-signature.bmp")
	_, ok := DetectFormat(bytes)
	if ok {
		t.Errorf("expected DetectFormat to return ok=false for bad signature")
	}
}

func TestGroup3SupportedFormats(t *testing.T) {
	supported := SupportedFormats()
	found := false
	for _, f := range supported {
		if f == Bmp {
			found = true
		}
	}
	if !found {
		t.Errorf("SupportedFormats should contain Bmp")
	}
}

func TestGroup3DetectsAllValidPngFixtures(t *testing.T) {
	fixtures := listValidFixtures(t, "png")
	for _, rel := range fixtures {
		b := readFixture(t, rel)
		f, ok := DetectFormat(b)
		if !ok || f != Png {
			t.Errorf("%s: expected Png detection, got %v ok=%v", rel, f, ok)
		}
	}
}

func TestGroup3SupportedFormatsContainsPng(t *testing.T) {
	found := false
	for _, f := range SupportedFormats() {
		if f == Png {
			found = true
		}
	}
	if !found {
		t.Errorf("SupportedFormats should contain Png")
	}
}

func TestGroup3DetectsAllValidGifFixtures(t *testing.T) {
	fixtures := listValidFixtures(t, "gif")
	for _, rel := range fixtures {
		b := readFixture(t, rel)
		f, ok := DetectFormat(b)
		if !ok || f != Gif {
			t.Errorf("%s: expected Gif detection, got %v ok=%v", rel, f, ok)
		}
	}
}

func TestGroup3SupportedFormatsContainsGif(t *testing.T) {
	found := false
	for _, f := range SupportedFormats() {
		if f == Gif {
			found = true
		}
	}
	if !found {
		t.Errorf("SupportedFormats should contain Gif")
	}
}
