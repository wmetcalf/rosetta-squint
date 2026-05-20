// Package pilgray implements PIL's 'L' (grayscale) conversion via the
// fixed-point ITU-R 601 luma formula.
package pilgray

// ToGray returns the grayscale value 0..255 for uint8 RGB input,
// matching Pillow Image.convert('L') exactly.
func ToGray(r, g, b uint8) uint8 {
	return uint8((uint32(r)*19595 + uint32(g)*38470 + uint32(b)*7471 + 32768) >> 16)
}
