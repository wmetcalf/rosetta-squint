package squint_test

import (
	"os"
	"path/filepath"
	"runtime"
	"testing"

	"github.com/wmetcalf/rosetta-image-hash/go/imagehash"
	"github.com/wmetcalf/rosetta-squint/squint/go/squint"
)

// fixturesDir returns the absolute path to the decode spec fixtures directory.
func fixturesDir() string {
	// This file lives at squint/go/squint/; decode fixtures are at
	// ../../../decode/spec/fixtures relative to this package.
	_, file, _, _ := runtime.Caller(0)
	dir := filepath.Dir(file)
	return filepath.Join(dir, "..", "..", "..", "decode", "spec", "fixtures")
}

func fixturePath(format, name string) string {
	return filepath.Join(fixturesDir(), format, "valid", name)
}

func readFile(t *testing.T, path string) []byte {
	t.Helper()
	b, err := os.ReadFile(path)
	if err != nil {
		t.Fatalf("ReadFile(%s): %v", path, err)
	}
	return b
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

func assertNonEmpty(t *testing.T, hex, label string) {
	t.Helper()
	if len(hex) == 0 {
		t.Errorf("%s: got empty hex string", label)
	}
}

func assertHexEqual(t *testing.T, a, b, label string) {
	t.Helper()
	if a != b {
		t.Errorf("%s: path=%s bytes=%s; want equal", label, a, b)
	}
}

// ---------------------------------------------------------------------------
// PHash — PNG, JPEG, WebP
// ---------------------------------------------------------------------------

func TestPHash_PNG_NonEmpty(t *testing.T) {
	path := fixturePath("png", "photo-1024.png")
	h, err := squint.PHash(path, 8)
	if err != nil {
		t.Fatalf("PHash: %v", err)
	}
	assertNonEmpty(t, h.ToHex(), "PHash PNG")
}

func TestPHash_PNG_PathBytesAgree(t *testing.T) {
	path := fixturePath("png", "photo-1024.png")
	h1, err := squint.PHash(path, 8)
	if err != nil {
		t.Fatalf("PHash(path): %v", err)
	}
	h2, err := squint.PHashBytes(readFile(t, path), 8)
	if err != nil {
		t.Fatalf("PHashBytes: %v", err)
	}
	assertHexEqual(t, h1.ToHex(), h2.ToHex(), "PHash PNG")
}

func TestPHash_JPEG_NonEmpty(t *testing.T) {
	path := fixturePath("jpeg", "larger-photo-128.jpg")
	h, err := squint.PHash(path, 8)
	if err != nil {
		t.Fatalf("PHash JPEG: %v", err)
	}
	assertNonEmpty(t, h.ToHex(), "PHash JPEG")
	t.Logf("PHash(larger-photo-128.jpg, 8) = %s", h.ToHex())
}

func TestPHash_JPEG_PathBytesAgree(t *testing.T) {
	path := fixturePath("jpeg", "larger-photo-128.jpg")
	h1, err := squint.PHash(path, 8)
	if err != nil {
		t.Fatalf("PHash(path): %v", err)
	}
	h2, err := squint.PHashBytes(readFile(t, path), 8)
	if err != nil {
		t.Fatalf("PHashBytes: %v", err)
	}
	assertHexEqual(t, h1.ToHex(), h2.ToHex(), "PHash JPEG")
}

func TestPHash_WebP_NonEmpty(t *testing.T) {
	path := fixturePath("webp", "larger-128x96-lossy.webp")
	h, err := squint.PHash(path, 8)
	if err != nil {
		t.Fatalf("PHash WebP: %v", err)
	}
	assertNonEmpty(t, h.ToHex(), "PHash WebP")
}

func TestPHash_WebP_PathBytesAgree(t *testing.T) {
	path := fixturePath("webp", "larger-128x96-lossy.webp")
	h1, err := squint.PHash(path, 8)
	if err != nil {
		t.Fatalf("PHash(path): %v", err)
	}
	h2, err := squint.PHashBytes(readFile(t, path), 8)
	if err != nil {
		t.Fatalf("PHashBytes: %v", err)
	}
	assertHexEqual(t, h1.ToHex(), h2.ToHex(), "PHash WebP")
}

// ---------------------------------------------------------------------------
// PHash — chain consistency
// squint.PHashBytes(b) == imagehash.PHash(squint.DecodeBytes(b))
// ---------------------------------------------------------------------------

func TestPHash_ChainConsistency(t *testing.T) {
	path := fixturePath("png", "imagehash.png")
	b := readFile(t, path)

	h1, err := squint.PHashBytes(b, 8)
	if err != nil {
		t.Fatalf("PHashBytes: %v", err)
	}

	img, err := squint.DecodeBytes(b)
	if err != nil {
		t.Fatalf("DecodeBytes: %v", err)
	}
	h2, err := imagehash.PHash(img, 8)
	if err != nil {
		t.Fatalf("imagehash.PHash: %v", err)
	}

	if h1.ToHex() != h2.ToHex() {
		t.Errorf("chain inconsistency: squint=%s, direct=%s", h1.ToHex(), h2.ToHex())
	}
	t.Logf("PHash(imagehash.png, 8) = %s", h1.ToHex())
}

// ---------------------------------------------------------------------------
// AverageHash
// ---------------------------------------------------------------------------

func TestAverageHash_PNG_NonEmpty(t *testing.T) {
	path := fixturePath("png", "gradient-h-256.png")
	h, err := squint.AverageHash(path, 8)
	if err != nil {
		t.Fatalf("AverageHash: %v", err)
	}
	assertNonEmpty(t, h.ToHex(), "AverageHash PNG")
}

func TestAverageHash_PNG_PathBytesAgree(t *testing.T) {
	path := fixturePath("png", "gradient-h-256.png")
	h1, err := squint.AverageHash(path, 8)
	if err != nil {
		t.Fatalf("AverageHash(path): %v", err)
	}
	h2, err := squint.AverageHashBytes(readFile(t, path), 8)
	if err != nil {
		t.Fatalf("AverageHashBytes: %v", err)
	}
	assertHexEqual(t, h1.ToHex(), h2.ToHex(), "AverageHash PNG")
}

func TestAverageHash_JPEG_PathBytesAgree(t *testing.T) {
	path := fixturePath("jpeg", "32x32-quality-95.jpg")
	h1, err := squint.AverageHash(path, 8)
	if err != nil {
		t.Fatalf("AverageHash JPEG(path): %v", err)
	}
	h2, err := squint.AverageHashBytes(readFile(t, path), 8)
	if err != nil {
		t.Fatalf("AverageHashBytes JPEG: %v", err)
	}
	assertHexEqual(t, h1.ToHex(), h2.ToHex(), "AverageHash JPEG")
}

func TestAverageHash_ChainConsistency(t *testing.T) {
	path := fixturePath("png", "checker-256.png")
	b := readFile(t, path)

	h1, err := squint.AverageHashBytes(b, 8)
	if err != nil {
		t.Fatalf("AverageHashBytes: %v", err)
	}
	img, err := squint.DecodeBytes(b)
	if err != nil {
		t.Fatalf("DecodeBytes: %v", err)
	}
	h2, err := imagehash.AverageHash(img, 8)
	if err != nil {
		t.Fatalf("imagehash.AverageHash: %v", err)
	}
	if h1.ToHex() != h2.ToHex() {
		t.Errorf("AverageHash chain inconsistency: squint=%s, direct=%s", h1.ToHex(), h2.ToHex())
	}
}

// ---------------------------------------------------------------------------
// DHash
// ---------------------------------------------------------------------------

func TestDHash_PNG_NonEmpty(t *testing.T) {
	path := fixturePath("png", "checker-256.png")
	h, err := squint.DHash(path, 8)
	if err != nil {
		t.Fatalf("DHash: %v", err)
	}
	assertNonEmpty(t, h.ToHex(), "DHash PNG")
}

func TestDHash_PNG_PathBytesAgree(t *testing.T) {
	path := fixturePath("png", "checker-256.png")
	h1, err := squint.DHash(path, 8)
	if err != nil {
		t.Fatalf("DHash(path): %v", err)
	}
	h2, err := squint.DHashBytes(readFile(t, path), 8)
	if err != nil {
		t.Fatalf("DHashBytes: %v", err)
	}
	assertHexEqual(t, h1.ToHex(), h2.ToHex(), "DHash PNG")
}

func TestDHash_JPEG_PathBytesAgree(t *testing.T) {
	path := fixturePath("jpeg", "64x64-quality-50.jpg")
	h1, err := squint.DHash(path, 8)
	if err != nil {
		t.Fatalf("DHash JPEG(path): %v", err)
	}
	h2, err := squint.DHashBytes(readFile(t, path), 8)
	if err != nil {
		t.Fatalf("DHashBytes JPEG: %v", err)
	}
	assertHexEqual(t, h1.ToHex(), h2.ToHex(), "DHash JPEG")
}

// ---------------------------------------------------------------------------
// DHashVertical
// ---------------------------------------------------------------------------

func TestDHashVertical_PathBytesAgree(t *testing.T) {
	path := fixturePath("png", "gradient-v-256.png")
	h1, err := squint.DHashVertical(path, 8)
	if err != nil {
		t.Fatalf("DHashVertical(path): %v", err)
	}
	h2, err := squint.DHashVerticalBytes(readFile(t, path), 8)
	if err != nil {
		t.Fatalf("DHashVerticalBytes: %v", err)
	}
	assertHexEqual(t, h1.ToHex(), h2.ToHex(), "DHashVertical")
}

// ---------------------------------------------------------------------------
// PHashSimple
// ---------------------------------------------------------------------------

func TestPHashSimple_PathBytesAgree(t *testing.T) {
	path := fixturePath("png", "peppers.png")
	h1, err := squint.PHashSimple(path, 8)
	if err != nil {
		t.Fatalf("PHashSimple(path): %v", err)
	}
	h2, err := squint.PHashSimpleBytes(readFile(t, path), 8)
	if err != nil {
		t.Fatalf("PHashSimpleBytes: %v", err)
	}
	assertHexEqual(t, h1.ToHex(), h2.ToHex(), "PHashSimple")
}

// ---------------------------------------------------------------------------
// WHashHaar
// ---------------------------------------------------------------------------

func TestWHashHaar_PNG_NonEmpty(t *testing.T) {
	path := fixturePath("png", "photo-1024.png")
	h, err := squint.WHashHaar(path, 8)
	if err != nil {
		t.Fatalf("WHashHaar: %v", err)
	}
	assertNonEmpty(t, h.ToHex(), "WHashHaar PNG")
}

func TestWHashHaar_PathBytesAgree(t *testing.T) {
	path := fixturePath("png", "photo-1024.png")
	h1, err := squint.WHashHaar(path, 8)
	if err != nil {
		t.Fatalf("WHashHaar(path): %v", err)
	}
	h2, err := squint.WHashHaarBytes(readFile(t, path), 8)
	if err != nil {
		t.Fatalf("WHashHaarBytes: %v", err)
	}
	assertHexEqual(t, h1.ToHex(), h2.ToHex(), "WHashHaar")
}

// ---------------------------------------------------------------------------
// WHashDb4
// ---------------------------------------------------------------------------

func TestWHashDb4_PathBytesAgree(t *testing.T) {
	path := fixturePath("png", "scan-doc-1024.png")
	h1, err := squint.WHashDb4(path, 8)
	if err != nil {
		t.Fatalf("WHashDb4(path): %v", err)
	}
	h2, err := squint.WHashDb4Bytes(readFile(t, path), 8)
	if err != nil {
		t.Fatalf("WHashDb4Bytes: %v", err)
	}
	assertHexEqual(t, h1.ToHex(), h2.ToHex(), "WHashDb4")
}

// ---------------------------------------------------------------------------
// WHashDb4Robust
// ---------------------------------------------------------------------------

func TestWHashDb4Robust_PathBytesAgree(t *testing.T) {
	path := fixturePath("png", "screenshot-1200.png")
	h1, err := squint.WHashDb4Robust(path, 8)
	if err != nil {
		t.Fatalf("WHashDb4Robust(path): %v", err)
	}
	h2, err := squint.WHashDb4RobustBytes(readFile(t, path), 8)
	if err != nil {
		t.Fatalf("WHashDb4RobustBytes: %v", err)
	}
	assertHexEqual(t, h1.ToHex(), h2.ToHex(), "WHashDb4Robust")
}

// ---------------------------------------------------------------------------
// ColorHash
// ---------------------------------------------------------------------------

func TestColorHash_PNG_NonEmpty(t *testing.T) {
	path := fixturePath("png", "art-saturated-512.png")
	h, err := squint.ColorHash(path, 3)
	if err != nil {
		t.Fatalf("ColorHash: %v", err)
	}
	assertNonEmpty(t, h.ToHex(), "ColorHash PNG")
}

func TestColorHash_PNG_PathBytesAgree(t *testing.T) {
	path := fixturePath("png", "art-saturated-512.png")
	h1, err := squint.ColorHash(path, 3)
	if err != nil {
		t.Fatalf("ColorHash(path): %v", err)
	}
	h2, err := squint.ColorHashBytes(readFile(t, path), 3)
	if err != nil {
		t.Fatalf("ColorHashBytes: %v", err)
	}
	assertHexEqual(t, h1.ToHex(), h2.ToHex(), "ColorHash PNG")
}

func TestColorHash_JPEG_PathBytesAgree(t *testing.T) {
	path := fixturePath("jpeg", "larger-photo-128.jpg")
	h1, err := squint.ColorHash(path, 3)
	if err != nil {
		t.Fatalf("ColorHash JPEG(path): %v", err)
	}
	h2, err := squint.ColorHashBytes(readFile(t, path), 3)
	if err != nil {
		t.Fatalf("ColorHashBytes JPEG: %v", err)
	}
	assertHexEqual(t, h1.ToHex(), h2.ToHex(), "ColorHash JPEG")
}

// ---------------------------------------------------------------------------
// CropResistantHash
// ---------------------------------------------------------------------------

func TestCropResistantHash_PNG_NonEmpty(t *testing.T) {
	path := fixturePath("png", "photo-1024.png")
	mh, err := squint.CropResistantHash(path, nil)
	if err != nil {
		t.Fatalf("CropResistantHash: %v", err)
	}
	if len(mh.SegmentHashes) == 0 {
		t.Error("CropResistantHash: got zero segments")
	}
	assertNonEmpty(t, mh.ToHex(), "CropResistantHash PNG")
}

func TestCropResistantHash_PathBytesAgree(t *testing.T) {
	path := fixturePath("png", "photo-1024.png")
	mh1, err := squint.CropResistantHash(path, nil)
	if err != nil {
		t.Fatalf("CropResistantHash(path): %v", err)
	}
	mh2, err := squint.CropResistantHashBytes(readFile(t, path), nil)
	if err != nil {
		t.Fatalf("CropResistantHashBytes: %v", err)
	}
	if mh1.ToHex() != mh2.ToHex() {
		t.Errorf("CropResistantHash path=%s, bytes=%s; want equal", mh1.ToHex(), mh2.ToHex())
	}
}

func TestCropResistantHash_JPEG_NonEmpty(t *testing.T) {
	path := fixturePath("jpeg", "larger-photo-128.jpg")
	mh, err := squint.CropResistantHash(path, nil)
	if err != nil {
		t.Fatalf("CropResistantHash JPEG: %v", err)
	}
	if len(mh.SegmentHashes) == 0 {
		t.Error("CropResistantHash JPEG: got zero segments")
	}
}

func TestCropResistantHash_LimitSegmentsForwarded(t *testing.T) {
	// H-L7: verify limitSegments is forwarded through the squint wrapper.
	path := fixturePath("png", "photo-1024.png")
	mhAll, err := squint.CropResistantHash(path, nil)
	if err != nil {
		t.Fatalf("CropResistantHash unlimited: %v", err)
	}
	if len(mhAll.SegmentHashes) <= 1 {
		t.Skip("fixture only produced one segment; nothing to limit")
	}
	one := 1
	mh1, err := squint.CropResistantHash(path, &one)
	if err != nil {
		t.Fatalf("CropResistantHash limit=1: %v", err)
	}
	if len(mh1.SegmentHashes) != 1 {
		t.Errorf("CropResistantHash limit=1: got %d segments, want 1", len(mh1.SegmentHashes))
	}
}

// ---------------------------------------------------------------------------
// DecodeFile / DecodeBytes — returned image must be usable
// ---------------------------------------------------------------------------

func TestDecodeFile_ReturnsUsableImage(t *testing.T) {
	path := fixturePath("png", "checker-32.png")
	img, err := squint.DecodeFile(path)
	if err != nil {
		t.Fatalf("DecodeFile: %v", err)
	}
	b := img.Bounds()
	if b.Dx() == 0 || b.Dy() == 0 {
		t.Errorf("DecodeFile: empty image bounds %v", b)
	}
}

func TestDecodeBytes_ReturnsUsableImage(t *testing.T) {
	path := fixturePath("jpeg", "16x16-quality-95.jpg")
	raw := readFile(t, path)
	img, err := squint.DecodeBytes(raw)
	if err != nil {
		t.Fatalf("DecodeBytes: %v", err)
	}
	// img is already image.Image — a typed-nil check is the load-bearing
	// assertion, not the type assertion itself. Use the value directly.
	if img == nil {
		t.Error("DecodeBytes: returned nil image")
	}
}
