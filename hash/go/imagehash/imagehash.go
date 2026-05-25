// Package imagehash provides byte-exact ports of Python imagehash 4.3.2
// algorithms (ahash, dhash, phash, whash, colorhash) for Go.
package imagehash

import (
	"fmt"

	"github.com/wmetcalf/rosetta-squint/hash/go/imagehash/internal/bitpack"
)

// Hash is a 2-D boolean hash backed by a row-major bits[H][W] array.
type Hash struct {
	bits [][]bool
}

// NewHash constructs a Hash from a rectangular boolean[][].
func NewHash(bits [][]bool) (Hash, error) {
	if len(bits) == 0 || len(bits[0]) == 0 {
		return Hash{}, fmt.Errorf("bits must be non-empty")
	}
	w := len(bits[0])
	for _, row := range bits {
		if len(row) != w {
			return Hash{}, fmt.Errorf("bits must be rectangular; got width %d vs %d", len(row), w)
		}
	}
	dup := make([][]bool, len(bits))
	for y := range bits {
		dup[y] = append([]bool(nil), bits[y]...)
	}
	return Hash{bits: dup}, nil
}

func (h Hash) ToHex() string {
	return bitpack.Pack(h.bits)
}

func (h Hash) String() string {
	return h.ToHex()
}

func (h Hash) Subtract(other Hash) (int, error) {
	if len(h.bits) != len(other.bits) || len(h.bits[0]) != len(other.bits[0]) {
		return 0, fmt.Errorf("shapes don't match: this=(%d,%d), other=(%d,%d)",
			len(h.bits), len(h.bits[0]), len(other.bits), len(other.bits[0]))
	}
	diff := 0
	for y := range h.bits {
		for x := range h.bits[y] {
			if h.bits[y][x] != other.bits[y][x] {
				diff++
			}
		}
	}
	return diff, nil
}

func (h Hash) BitCount() int {
	if len(h.bits) == 0 {
		return 0
	}
	return len(h.bits) * len(h.bits[0])
}

func (h Hash) Equals(other Hash) bool {
	if len(h.bits) != len(other.bits) {
		return false
	}
	for y := range h.bits {
		if len(h.bits[y]) != len(other.bits[y]) {
			return false
		}
		for x := range h.bits[y] {
			if h.bits[y][x] != other.bits[y][x] {
				return false
			}
		}
	}
	return true
}

// newHashFromBits is internal: constructs a Hash without copying. Used by Hex parsers
// where the bits slice is already freshly allocated.
func newHashFromBits(bits [][]bool) Hash { return Hash{bits: bits} }
