package imagehash_test

import (
	"testing"

	"github.com/wmetcalf/rosetta-image-hash/go/imagehash"
)

func TestHexToHashAndBack(t *testing.T) {
	hex := "ffd7918181c9ffff"
	h, err := imagehash.HexToHash(hex)
	if err != nil {
		t.Fatalf("HexToHash: %v", err)
	}
	if h.BitCount() != 64 {
		t.Errorf("BitCount: got %d, want 64", h.BitCount())
	}
	if got := h.ToHex(); got != hex {
		t.Errorf("round-trip: got %q, want %q", got, hex)
	}
}

func TestHexToFlathashAndBack(t *testing.T) {
	hex := "0123456789abcd"
	h, err := imagehash.HexToFlathash(hex, 4)
	if err != nil {
		t.Fatalf("HexToFlathash: %v", err)
	}
	if h.BitCount() != 14*4 {
		t.Errorf("BitCount: got %d, want %d", h.BitCount(), 14*4)
	}
	if got := h.ToHex(); got != hex {
		t.Errorf("round-trip: got %q, want %q", got, hex)
	}
}

func TestHexToHashNonSquareErrors(t *testing.T) {
	if _, err := imagehash.HexToHash("12345"); err == nil {
		t.Errorf("expected error for non-square hex")
	}
}

func TestHexToHashInvalidCharsErrors(t *testing.T) {
	if _, err := imagehash.HexToHash("xyz!"); err == nil {
		t.Errorf("expected error for invalid hex chars")
	}
}

func TestRoundTripAllZeros(t *testing.T) {
	hex := "0000000000000000"
	h, err := imagehash.HexToHash(hex)
	if err != nil {
		t.Fatalf("HexToHash: %v", err)
	}
	if got := h.ToHex(); got != hex {
		t.Errorf("got %q, want %q", got, hex)
	}
}
