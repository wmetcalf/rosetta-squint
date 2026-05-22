package bitpack_test

import (
	"testing"

	"github.com/wmetcalf/rosetta-image-hash/go/imagehash/internal/bitpack"
)

func TestPack4x4Pattern(t *testing.T) {
	bits := [][]bool{
		{true, false, true, false},
		{false, true, false, true},
		{true, true, true, true},
		{false, false, false, false},
	}
	if got := bitpack.Pack(bits); got != "a5f0" {
		t.Errorf("got %q, want a5f0", got)
	}
}

func TestPackAllOnes8x8(t *testing.T) {
	bits := make([][]bool, 8)
	for y := range bits {
		bits[y] = make([]bool, 8)
		for x := range bits[y] {
			bits[y][x] = true
		}
	}
	if got := bitpack.Pack(bits); got != "ffffffffffffffff" {
		t.Errorf("got %q", got)
	}
}

func TestPackAllZeros8x8(t *testing.T) {
	bits := make([][]bool, 8)
	for y := range bits {
		bits[y] = make([]bool, 8)
	}
	if got := bitpack.Pack(bits); got != "0000000000000000" {
		t.Errorf("got %q", got)
	}
}

func TestUnpackSquareIsInverse(t *testing.T) {
	expected := [][]bool{
		{true, false, true, false},
		{false, true, false, true},
		{true, true, true, true},
		{false, false, false, false},
	}
	got, err := bitpack.UnpackSquare("a5f0")
	if err != nil {
		t.Fatalf("err: %v", err)
	}
	if len(got) != 4 {
		t.Fatalf("rows: %d", len(got))
	}
	for y := range expected {
		for x := range expected[y] {
			if got[y][x] != expected[y][x] {
				t.Errorf("(%d,%d): got %v, want %v", y, x, got[y][x], expected[y][x])
			}
		}
	}
}

func TestUnpackFlat(t *testing.T) {
	got, err := bitpack.UnpackFlat("00000000000", 3)
	if err != nil {
		t.Fatalf("err: %v", err)
	}
	if len(got) != 14 || len(got[0]) != 3 {
		t.Fatalf("shape: %dx%d", len(got), len(got[0]))
	}
	for y := range got {
		for x := range got[y] {
			if got[y][x] {
				t.Errorf("expected all false at (%d,%d)", y, x)
			}
		}
	}
}

func TestUnpackSquareNonSquareLengthErrors(t *testing.T) {
	if _, err := bitpack.UnpackSquare("12345"); err == nil {
		t.Errorf("expected error for non-square hex")
	}
}

func TestUnpackInvalidCharsErrors(t *testing.T) {
	if _, err := bitpack.UnpackSquare("xyz!"); err == nil {
		t.Errorf("expected error for invalid chars")
	}
}
