package imagedecode

import (
	"bytes"
	"image"
	"image/gif"
)

// gifGlobalColorTable reads the global color table from raw GIF bytes, returning
// the RGB triples (one [3]byte per palette entry). Returns nil if no global
// color table is present or the data is too short.
func gifGlobalColorTable(b []byte) [][3]byte {
	if len(b) < 13 {
		return nil
	}
	flags := b[10]
	if flags>>7 == 0 {
		return nil // no global color table
	}
	entries := 1 << (int(flags&0x7) + 1)
	offset := 13
	if offset+entries*3 > len(b) {
		return nil
	}
	palette := make([][3]byte, entries)
	for i := 0; i < entries; i++ {
		palette[i] = [3]byte{b[offset], b[offset+1], b[offset+2]}
		offset += 3
	}
	return palette
}

// gifTransparentIndex scans the GIF byte stream for a Graphic Control Extension
// and returns the transparent color index, or -1 if none.
func gifTransparentIndex(b []byte, gctEntries int) int {
	pos := 13 + gctEntries*3
	for pos < len(b) {
		switch b[pos] {
		case 0x21: // Extension introducer
			if pos+1 >= len(b) {
				return -1
			}
			isGCE := b[pos+1] == 0xF9
			pos += 2
			for pos < len(b) {
				sz := int(b[pos])
				pos++
				if sz == 0 {
					break
				}
				if isGCE && sz >= 4 && pos+sz-1 < len(b) {
					packed := b[pos]
					if packed&0x01 != 0 {
						return int(b[pos+3])
					}
					isGCE = false
				}
				pos += sz
			}
		case 0x2C: // Image descriptor
			return -1
		case 0x3B: // Trailer
			return -1
		default:
			pos++
		}
	}
	return -1
}

func decodeGif(b []byte) (DecodedImage, error) {
	img, err := gif.Decode(bytes.NewReader(b))
	if err != nil {
		return DecodedImage{}, newError(CorruptInput, Gif, true, "gif.Decode failed: "+err.Error())
	}
	bnds := img.Bounds()
	width := bnds.Dx()
	height := bnds.Dy()

	p, isPaletted := img.(*image.Paletted)

	// Determine if the image has transparency and which palette index is transparent.
	// Go's image/gif decoder zeroes the RGB of the transparent palette entry (sets it to
	// {0,0,0,0}). PIL/Pillow preserves the original palette RGB. To match the reference
	// golden outputs we read the original palette from the raw GIF bytes.
	transpIdx := -1
	var rawPalette [][3]byte
	if isPaletted {
		rawPalette = gifGlobalColorTable(b)
		if rawPalette != nil {
			transpIdx = gifTransparentIndex(b, len(rawPalette))
		}
	}

	hasAlpha := transpIdx >= 0
	if !hasAlpha && !isPaletted {
		// Non-palette GIF (unusual): scan all pixels for alpha != 0xFFFF
		for y := bnds.Min.Y; y < bnds.Max.Y && !hasAlpha; y++ {
			for x := bnds.Min.X; x < bnds.Max.X; x++ {
				_, _, _, a := img.At(x, y).RGBA()
				if a != 0xFFFF {
					hasAlpha = true
					break
				}
			}
		}
	}

	channelCount := 3
	channels := RGB
	if hasAlpha {
		channelCount = 4
		channels = RGBA
	}

	out := make([]byte, width*height*channelCount)
	idx := 0

	if isPaletted && rawPalette != nil {
		// Fast path: use raw palette index to avoid premultiplied-alpha loss.
		// For transparent pixels, use the original palette RGB (not Go's zeroed {0,0,0}).
		for y := bnds.Min.Y; y < bnds.Max.Y; y++ {
			for x := bnds.Min.X; x < bnds.Max.X; x++ {
				pixOffset := p.PixOffset(x, y)
				palIdx := int(p.Pix[pixOffset])
				var r, g, bl byte
				var a byte = 255
				if palIdx < len(rawPalette) {
					rgb := rawPalette[palIdx]
					r, g, bl = rgb[0], rgb[1], rgb[2]
				}
				if palIdx == transpIdx {
					a = 0
				}
				out[idx] = r
				out[idx+1] = g
				out[idx+2] = bl
				idx += 3
				if hasAlpha {
					out[idx] = a
					idx++
				}
			}
		}
	} else {
		// Fallback: use At() for non-paletted images.
		for y := bnds.Min.Y; y < bnds.Max.Y; y++ {
			for x := bnds.Min.X; x < bnds.Max.X; x++ {
				r, g, bl, a := img.At(x, y).RGBA()
				out[idx] = byte(r >> 8)
				out[idx+1] = byte(g >> 8)
				out[idx+2] = byte(bl >> 8)
				idx += 3
				if hasAlpha {
					out[idx] = byte(a >> 8)
					idx++
				}
			}
		}
	}

	return DecodedImage{Width: width, Height: height, Data: out, Channels: channels, Format: Gif}, nil
}
