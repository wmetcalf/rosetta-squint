// Package pilhsv implements PIL's 'HSV' conversion using the integer formula
// from libImaging/Convert.c rgb2hsv_row. All values in 0..255.
package pilhsv

// ToHSV returns h, s, v each in 0..255 for uint8 RGB input.
// Matches Pillow Image.convert('HSV') byte-exact.
func ToHSV(r, g, b uint8) (h, s, v uint8) {
	ri, gi, bi := int(r), int(g), int(b)
	maxc := ri
	if gi > maxc {
		maxc = gi
	}
	if bi > maxc {
		maxc = bi
	}
	minc := ri
	if gi < minc {
		minc = gi
	}
	if bi < minc {
		minc = bi
	}
	v = uint8(maxc)
	if maxc == 0 {
		return 0, 0, v
	}
	si := (255 * (maxc - minc)) / maxc
	s = uint8(si)
	if minc == maxc {
		return 0, s, v
	}
	delta := maxc - minc
	rc := ((maxc - ri) * 255) / delta
	gc := ((maxc - gi) * 255) / delta
	bc := ((maxc - bi) * 255) / delta
	var hPre int
	switch {
	case ri == maxc:
		hPre = bc - gc
	case gi == maxc:
		hPre = 2*255 + rc - bc
	default:
		hPre = 4*255 + gc - rc
	}
	if hPre < 0 {
		hPre += 6 * 255
	}
	h = uint8(hPre / 6)
	return h, s, v
}
