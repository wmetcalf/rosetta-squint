package imagedecode

import (
	"encoding/binary"
	"testing"
)

// D-M1: BMP biClrUsed must be clamped to bit-depth max so an attacker-controlled
// value (e.g. 0x10000000 = 256M entries) cannot cause GB-scale palette allocation.
// Without the clamp, entryCount*4 would overflow signed-32 arithmetic on 32-bit
// builds (and even on 64-bit, a 4-billion-entry allocation would OOM the process).

func TestBmpClampsOversizedClrUsedBomb(t *testing.T) {
	// Build an 8-bit BMP in memory with biClrUsed = 0x10000000.
	// The actual on-disk palette is 256 entries (1024 bytes); the clamp must
	// limit reads to that, otherwise the decoder would attempt to read 1 GB.
	bytes := buildPal8BmpWithClrUsed(0x10000000)
	img, err := Decode(bytes)
	if err != nil {
		t.Fatalf("unexpected decode error: %v", err)
	}
	if img.Width != 2 || img.Height != 2 {
		t.Errorf("expected 2x2, got %dx%d", img.Width, img.Height)
	}
	// 2x2 RGB = 12 bytes — confirms no excessive allocation.
	if len(img.Data) != 12 {
		t.Errorf("expected 12 pixel bytes, got %d", len(img.Data))
	}
}

func TestBmpClampsClrUsed257to256(t *testing.T) {
	// biClrUsed = 257 (just over the 8-bit max of 256) must clamp to 256.
	bytes := buildPal8BmpWithClrUsed(257)
	img, err := Decode(bytes)
	if err != nil {
		t.Fatalf("unexpected decode error: %v", err)
	}
	if img.Width != 2 || img.Height != 2 {
		t.Errorf("expected 2x2, got %dx%d", img.Width, img.Height)
	}
}

func TestClampEntryCountBoundaries(t *testing.T) {
	cases := []struct {
		name     string
		clrUsed  int
		bitDepth int
		want     int
	}{
		{"1-bit default", 0, 1, 2},
		{"1-bit clamp", 100, 1, 2},
		{"1-bit pass-through", 1, 1, 1},
		{"4-bit default", 0, 4, 16},
		{"4-bit huge clamp", 0x40000000, 4, 16},
		{"4-bit pass-through", 8, 4, 8},
		{"8-bit default", 0, 8, 256},
		{"8-bit bomb clamp", 0x10000000, 8, 256},
		{"8-bit 257 clamps to 256", 257, 8, 256},
		{"8-bit pass-through", 100, 8, 100},
		// On a 64-bit platform, int is 64-bit so negative-as-overflow case is benign,
		// but clrUsed<=0 still falls through to "default" branch.
		{"negative falls to default", -1, 8, 256},
	}
	for _, tc := range cases {
		got := clampEntryCount(tc.clrUsed, tc.bitDepth)
		if got != tc.want {
			t.Errorf("%s: clampEntryCount(%d, %d) = %d; want %d",
				tc.name, tc.clrUsed, tc.bitDepth, got, tc.want)
		}
	}
}

// buildPal8BmpWithClrUsed constructs a minimal 2x2 8-bit paletted BMP whose
// header declares biClrUsed = clrUsed, while the actual on-disk palette is
// exactly 256 entries (1024 bytes). A clamped decoder reads only the 1024-byte
// palette; an un-clamped decoder would attempt to read clrUsed*4 bytes.
func buildPal8BmpWithClrUsed(clrUsed uint32) []byte {
	const width = 2
	const height = 2
	const paletteBytes = 256 * 4
	rowStride := ((width + 3) / 4) * 4
	pixelDataSize := rowStride * height
	pixelDataOffset := 14 + 40 + paletteBytes
	fileSize := pixelDataOffset + pixelDataSize

	b := make([]byte, fileSize)
	// BMP file header (14 bytes)
	b[0] = 'B'
	b[1] = 'M'
	binary.LittleEndian.PutUint32(b[2:6], uint32(fileSize))
	// reserved fields (4-9) left zero
	binary.LittleEndian.PutUint32(b[10:14], uint32(pixelDataOffset))
	// DIB BITMAPINFOHEADER (40 bytes) starting at offset 14
	binary.LittleEndian.PutUint32(b[14:18], 40) // biSize
	binary.LittleEndian.PutUint32(b[18:22], uint32(width))
	binary.LittleEndian.PutUint32(b[22:26], uint32(height))
	binary.LittleEndian.PutUint16(b[26:28], 1) // planes
	binary.LittleEndian.PutUint16(b[28:30], 8) // bitCount
	binary.LittleEndian.PutUint32(b[30:34], 0) // compression BI_RGB
	binary.LittleEndian.PutUint32(b[34:38], uint32(pixelDataSize))
	binary.LittleEndian.PutUint32(b[38:42], 2835) // x ppm
	binary.LittleEndian.PutUint32(b[42:46], 2835) // y ppm
	binary.LittleEndian.PutUint32(b[46:50], clrUsed) // ATTACKER-CONTROLLED
	binary.LittleEndian.PutUint32(b[50:54], 0) // biClrImportant
	// Palette: 256 entries of (B, G, R, reserved) starting at offset 54
	for i := 0; i < 256; i++ {
		off := 54 + i*4
		b[off] = byte(i)
		b[off+1] = byte(i)
		b[off+2] = byte(i)
		b[off+3] = 0
	}
	// Pixel data starts at pixelDataOffset = 14+40+1024 = 1078.
	// row 0 = [0, 1, pad, pad], row 1 = [2, 3, pad, pad]
	b[pixelDataOffset+0] = 0
	b[pixelDataOffset+1] = 1
	b[pixelDataOffset+4] = 2
	b[pixelDataOffset+5] = 3
	return b
}
