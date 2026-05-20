package imagehash_test

import (
	"testing"

	"github.com/wmetcalf/rosetta-image-hash/go/imagehash"
)

func mustHash(t *testing.T, bits [][]bool) imagehash.Hash {
	t.Helper()
	h, err := imagehash.NewHash(bits)
	if err != nil {
		t.Fatalf("NewHash: %v", err)
	}
	return h
}

func TestHammingDistanceIsZeroForEqualHashes(t *testing.T) {
	a := mustHash(t, [][]bool{{true, false}, {true, true}})
	b := mustHash(t, [][]bool{{true, false}, {true, true}})
	d, err := a.Subtract(b)
	if err != nil {
		t.Fatalf("Subtract: %v", err)
	}
	if d != 0 {
		t.Errorf("got %d, want 0", d)
	}
}

func TestHammingDistanceCountsDifferingBits(t *testing.T) {
	a := mustHash(t, [][]bool{{true, false}, {true, true}})
	b := mustHash(t, [][]bool{{false, false}, {true, false}})
	d, err := a.Subtract(b)
	if err != nil {
		t.Fatalf("Subtract: %v", err)
	}
	if d != 2 {
		t.Errorf("got %d, want 2", d)
	}
}

func TestBitCountIsHeightTimesWidth(t *testing.T) {
	bits := make([][]bool, 8)
	for y := range bits {
		bits[y] = make([]bool, 8)
	}
	h := mustHash(t, bits)
	if h.BitCount() != 64 {
		t.Errorf("got %d, want 64", h.BitCount())
	}
}

func TestEqualsIsValueBased(t *testing.T) {
	a := mustHash(t, [][]bool{{true, false}})
	b := mustHash(t, [][]bool{{true, false}})
	c := mustHash(t, [][]bool{{false, false}})
	if !a.Equals(b) {
		t.Errorf("a should equal b")
	}
	if a.Equals(c) {
		t.Errorf("a should not equal c")
	}
}

func TestToHexProducesExpectedFormat(t *testing.T) {
	bits := make([][]bool, 8)
	for y := range bits {
		bits[y] = make([]bool, 8)
		for x := range bits[y] {
			bits[y][x] = true
		}
	}
	h := mustHash(t, bits)
	if got := h.ToHex(); got != "ffffffffffffffff" {
		t.Errorf("got %q", got)
	}
}

func TestSubtractRequiresMatchingShape(t *testing.T) {
	a := mustHash(t, [][]bool{{true, false}})
	b := mustHash(t, [][]bool{{true, false}, {true, false}})
	if _, err := a.Subtract(b); err == nil {
		t.Errorf("expected error on shape mismatch")
	}
}
