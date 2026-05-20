// Package bitpack converts between boolean[][] hash arrays and lowercase hex strings.
// Row-major flatten, MSB-first per nibble, zero-padded to ceil(M*N / 4) chars.
package bitpack

import (
	"fmt"
	"math/big"
	"strings"
)

func Pack(bits [][]bool) string {
	h := len(bits)
	if h == 0 || len(bits[0]) == 0 {
		return ""
	}
	w := len(bits[0])
	total := h * w
	var sb strings.Builder
	sb.Grow(total)
	for y := 0; y < h; y++ {
		for x := 0; x < w; x++ {
			if bits[y][x] {
				sb.WriteByte('1')
			} else {
				sb.WriteByte('0')
			}
		}
	}
	width := (total + 3) / 4
	v, ok := new(big.Int).SetString(sb.String(), 2)
	if !ok {
		return ""
	}
	hex := v.Text(16)
	if len(hex) < width {
		return strings.Repeat("0", width-len(hex)) + hex
	}
	return hex
}

func UnpackSquare(hex string) ([][]bool, error) {
	bits, err := hexToBits(hex)
	if err != nil {
		return nil, err
	}
	totalBits := len(bits)
	n := 0
	for n*n < totalBits {
		n++
	}
	if n*n != totalBits {
		return nil, fmt.Errorf("hex length %d (%d bits) is not a square shape", len(hex), totalBits)
	}
	out := make([][]bool, n)
	idx := 0
	for y := 0; y < n; y++ {
		out[y] = make([]bool, n)
		for x := 0; x < n; x++ {
			out[y][x] = bits[idx]
			idx++
		}
	}
	return out, nil
}

func UnpackFlat(hex string, secondAxis int) ([][]bool, error) {
	bits, err := hexToBits(hex)
	if err != nil {
		return nil, err
	}
	totalBits := 14 * secondAxis
	if len(bits) < totalBits {
		return nil, fmt.Errorf("hex too short for 14x%d shape: %d bits", secondAxis, len(bits))
	}
	out := make([][]bool, 14)
	idx := len(bits) - totalBits
	for y := 0; y < 14; y++ {
		out[y] = make([]bool, secondAxis)
		for x := 0; x < secondAxis; x++ {
			out[y][x] = bits[idx]
			idx++
		}
	}
	return out, nil
}

func hexToBits(hex string) ([]bool, error) {
	if hex == "" {
		return nil, fmt.Errorf("empty hex")
	}
	for _, c := range hex {
		if !((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f')) {
			return nil, fmt.Errorf("invalid hex character %q in %q", c, hex)
		}
	}
	v, ok := new(big.Int).SetString(hex, 16)
	if !ok {
		return nil, fmt.Errorf("could not parse hex %q", hex)
	}
	totalBits := len(hex) * 4
	bits := make([]bool, totalBits)
	for i := 0; i < totalBits; i++ {
		bits[totalBits-1-i] = v.Bit(i) == 1
	}
	return bits, nil
}
