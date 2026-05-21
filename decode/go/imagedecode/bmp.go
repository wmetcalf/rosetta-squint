package imagedecode

import (
	"encoding/binary"
	"fmt"
	"math/bits"
)

const (
	biRgb           = 0
	biRle8          = 1
	biRle4          = 2
	biBitfields     = 3
	biJpeg          = 4
	biPng           = 5
	biAlphabitfields = 6
)

type bmpHeader struct {
	width, height       int
	topDown             bool
	bitCount            int
	compression         int
	clrUsed             int
	redMask, greenMask, blueMask, alphaMask uint32
	pixelDataOffset     int
	dibHeaderSize       int
}

func decodeBmp(b []byte) (DecodedImage, error) {
	hdr, err := parseBmpHeader(b)
	if err != nil {
		return DecodedImage{}, err
	}
	switch hdr.compression {
	case biRgb:
		switch hdr.bitCount {
		case 24:
			return decodeBmpRgb24(b, hdr)
		case 32:
			return decodeBmpRgb32(b, hdr)
		case 8:
			return decodeBmpPal8(b, hdr)
		case 4:
			return decodeBmpPal4(b, hdr)
		case 1:
			return decodeBmpPal1(b, hdr)
		case 16:
			return DecodedImage{}, newError(CorruptInput, Bmp, true, "BI_RGB 16-bit not supported")
		default:
			return DecodedImage{}, newError(CorruptInput, Bmp, true, fmt.Sprintf("biBitCount %d for BI_RGB", hdr.bitCount))
		}
	case biBitfields, biAlphabitfields:
		switch hdr.bitCount {
		case 16:
			return decodeBmpBitfields(b, hdr, 16)
		case 32:
			return decodeBmpBitfields(b, hdr, 32)
		default:
			return DecodedImage{}, newError(CorruptInput, Bmp, true, fmt.Sprintf("BI_BITFIELDS with biBitCount %d", hdr.bitCount))
		}
	case biRle8:
		return decodeBmpRle(b, hdr, 8)
	case biRle4:
		return decodeBmpRle(b, hdr, 4)
	}
	return DecodedImage{}, newError(CorruptInput, Bmp, true, fmt.Sprintf("unreachable: bitCount=%d compression=%d", hdr.bitCount, hdr.compression))
}

func parseBmpHeader(b []byte) (*bmpHeader, error) {
	if len(b) < 14 {
		return nil, newError(Truncated, Bmp, true, "file header truncated")
	}
	if b[0] != 0x42 || b[1] != 0x4D {
		return nil, newError(CorruptInput, Bmp, true, "Not a BMP file (no 'BM' signature)")
	}

	bfOffBits := int(binary.LittleEndian.Uint32(b[10:14]))

	if len(b) < 18 {
		return nil, newError(Truncated, Bmp, true, "DIB header size not readable")
	}
	biSize := int(binary.LittleEndian.Uint32(b[14:18]))

	if biSize == 12 {
		return nil, newError(UnsupportedFeature, Bmp, true, "OS/2 BMP header (size 12)")
	}
	if biSize != 40 && biSize != 52 && biSize != 56 && biSize != 108 && biSize != 124 {
		return nil, newError(CorruptInput, Bmp, true, fmt.Sprintf("DIB header size %d not supported", biSize))
	}
	if len(b) < 14+biSize {
		return nil, newError(Truncated, Bmp, true, "DIB header truncated")
	}

	biWidth := int(int32(binary.LittleEndian.Uint32(b[18:22])))
	biHeight := int(int32(binary.LittleEndian.Uint32(b[22:26])))
	biPlanes := binary.LittleEndian.Uint16(b[26:28])
	biBitCount := int(binary.LittleEndian.Uint16(b[28:30]))
	biCompression := int(binary.LittleEndian.Uint32(b[30:34]))
	biClrUsed := int(binary.LittleEndian.Uint32(b[46:50]))

	if biWidth <= 0 {
		return nil, newError(CorruptInput, Bmp, true, "biWidth must be positive")
	}
	if biHeight == 0 {
		return nil, newError(CorruptInput, Bmp, true, "biHeight must be non-zero")
	}
	if biPlanes != 1 {
		return nil, newError(CorruptInput, Bmp, true, "biPlanes must be 1")
	}
	if biBitCount != 1 && biBitCount != 4 && biBitCount != 8 &&
		biBitCount != 16 && biBitCount != 24 && biBitCount != 32 {
		return nil, newError(CorruptInput, Bmp, true, fmt.Sprintf("biBitCount %d not supported", biBitCount))
	}
	if biCompression > 6 {
		return nil, newError(CorruptInput, Bmp, true, fmt.Sprintf("biCompression %d not supported", biCompression))
	}
	if biCompression == biJpeg {
		return nil, newError(UnsupportedFeature, Bmp, true, "embedded JPEG")
	}
	if biCompression == biPng {
		return nil, newError(UnsupportedFeature, Bmp, true, "embedded PNG")
	}

	// Masks if applicable
	var redMask, greenMask, blueMask, alphaMask uint32
	hasMasks := biCompression == biBitfields || biCompression == biAlphabitfields || biSize >= 52
	if hasMasks {
		if len(b) < 14+40+12 {
			return nil, newError(Truncated, Bmp, true, "BI_BITFIELDS masks truncated")
		}
		redMask = binary.LittleEndian.Uint32(b[54:58])
		greenMask = binary.LittleEndian.Uint32(b[58:62])
		blueMask = binary.LittleEndian.Uint32(b[62:66])
		if biCompression == biAlphabitfields || biSize >= 56 {
			if len(b) < 14+40+16 {
				return nil, newError(Truncated, Bmp, true, "alpha mask truncated")
			}
			alphaMask = binary.LittleEndian.Uint32(b[66:70])
		}
		if biCompression == biBitfields {
			if redMask == 0 || greenMask == 0 || blueMask == 0 {
				return nil, newError(CorruptInput, Bmp, true, "BI_BITFIELDS mask is zero")
			}
		}
	}

	topDown := biHeight < 0
	absHeight := biHeight
	if absHeight < 0 {
		absHeight = -absHeight
	}

	return &bmpHeader{
		width:           biWidth,
		height:          absHeight,
		topDown:         topDown,
		bitCount:        biBitCount,
		compression:     biCompression,
		clrUsed:         biClrUsed,
		redMask:         redMask,
		greenMask:       greenMask,
		blueMask:        blueMask,
		alphaMask:       alphaMask,
		pixelDataOffset: bfOffBits,
		dibHeaderSize:   biSize,
	}, nil
}

func decodeBmpRgb24(b []byte, hdr *bmpHeader) (DecodedImage, error) {
	stride := ((hdr.width*3 + 3) / 4) * 4
	if len(b)-hdr.pixelDataOffset < stride*hdr.height {
		return DecodedImage{}, newError(Truncated, Bmp, true, "pixel data truncated (24-bit RGB)")
	}
	pixels := make([]byte, hdr.width*hdr.height*3)
	for srcRow := 0; srcRow < hdr.height; srcRow++ {
		dstRow := srcRow
		if !hdr.topDown {
			dstRow = hdr.height - 1 - srcRow
		}
		for x := 0; x < hdr.width; x++ {
			srcIdx := hdr.pixelDataOffset + srcRow*stride + x*3
			dstIdx := (dstRow*hdr.width + x) * 3
			pixels[dstIdx] = b[srcIdx+2]   // R (from BGR+2)
			pixels[dstIdx+1] = b[srcIdx+1] // G (unchanged)
			pixels[dstIdx+2] = b[srcIdx]   // B (from BGR+0)
		}
	}
	return DecodedImage{Width: hdr.width, Height: hdr.height, Data: pixels, Channels: RGB, Format: Bmp}, nil
}

func decodeBmpRgb32(b []byte, hdr *bmpHeader) (DecodedImage, error) {
	stride := hdr.width * 4
	if len(b)-hdr.pixelDataOffset < stride*hdr.height {
		return DecodedImage{}, newError(Truncated, Bmp, true, "pixel data truncated (32-bit RGB)")
	}
	// Always output RGB to match Pillow 11 behavior (Pillow discards alpha for BI_RGB 32-bit)
	pixels := make([]byte, hdr.width*hdr.height*3)
	for srcRow := 0; srcRow < hdr.height; srcRow++ {
		dstRow := srcRow
		if !hdr.topDown {
			dstRow = hdr.height - 1 - srcRow
		}
		for x := 0; x < hdr.width; x++ {
			srcIdx := hdr.pixelDataOffset + srcRow*stride + x*4
			dstIdx := (dstRow*hdr.width + x) * 3
			pixels[dstIdx] = b[srcIdx+2]   // R (from BGRA+2)
			pixels[dstIdx+1] = b[srcIdx+1] // G (unchanged)
			pixels[dstIdx+2] = b[srcIdx]   // B (from BGRA+0)
			// alpha byte at srcIdx+3 discarded
		}
	}
	return DecodedImage{Width: hdr.width, Height: hdr.height, Data: pixels, Channels: RGB, Format: Bmp}, nil
}

func decodeBmpPal8(b []byte, hdr *bmpHeader) (DecodedImage, error) {
	colorTableOffset := 14 + hdr.dibHeaderSize
	entryCount := hdr.clrUsed
	if entryCount <= 0 {
		entryCount = 256
	}
	colorTableEnd := colorTableOffset + entryCount*4
	if len(b) < colorTableEnd {
		return DecodedImage{}, newError(Truncated, Bmp, true, "color table truncated (8-bit paletted)")
	}
	// Build palette as RGB triples (each entry is 4 bytes: B, G, R, reserved)
	palette := make([][3]byte, entryCount)
	for i := 0; i < entryCount; i++ {
		off := colorTableOffset + i*4
		palette[i][0] = b[off+2] // R
		palette[i][1] = b[off+1] // G
		palette[i][2] = b[off]   // B
	}
	stride := ((hdr.width + 3) / 4) * 4
	if len(b)-hdr.pixelDataOffset < stride*hdr.height {
		return DecodedImage{}, newError(Truncated, Bmp, true, "pixel data truncated (8-bit paletted)")
	}
	pixels := make([]byte, hdr.width*hdr.height*3)
	for srcRow := 0; srcRow < hdr.height; srcRow++ {
		dstRow := srcRow
		if !hdr.topDown {
			dstRow = hdr.height - 1 - srcRow
		}
		for x := 0; x < hdr.width; x++ {
			srcIdx := hdr.pixelDataOffset + srcRow*stride + x
			palIdx := int(b[srcIdx])
			if palIdx >= entryCount {
				palIdx = entryCount - 1
			}
			dstIdx := (dstRow*hdr.width + x) * 3
			pixels[dstIdx] = palette[palIdx][0]   // R
			pixels[dstIdx+1] = palette[palIdx][1] // G
			pixels[dstIdx+2] = palette[palIdx][2] // B
		}
	}
	return DecodedImage{Width: hdr.width, Height: hdr.height, Data: pixels, Channels: RGB, Format: Bmp}, nil
}

// readColorTable reads the color table for paletted images; returns [][3]byte (R,G,B).
func readColorTable(b []byte, hdr *bmpHeader, entryCount int) ([][3]byte, error) {
	colorTableOffset := 14 + hdr.dibHeaderSize
	colorTableEnd := colorTableOffset + entryCount*4
	if len(b) < colorTableEnd {
		return nil, newError(Truncated, Bmp, true, "color table truncated")
	}
	palette := make([][3]byte, entryCount)
	for i := 0; i < entryCount; i++ {
		off := colorTableOffset + i*4
		palette[i][0] = b[off+2] // R
		palette[i][1] = b[off+1] // G
		palette[i][2] = b[off]   // B
	}
	return palette, nil
}

func decodeBmpPal4(b []byte, hdr *bmpHeader) (DecodedImage, error) {
	entryCount := hdr.clrUsed
	if entryCount <= 0 {
		entryCount = 16
	}
	palette, err := readColorTable(b, hdr, entryCount)
	if err != nil {
		return DecodedImage{}, err
	}
	// Row stride: ceil(width*4 / 32) * 4 bytes = ((width * 4 + 31) / 32) * 4
	stride := ((hdr.width*4 + 31) / 32) * 4
	if len(b)-hdr.pixelDataOffset < stride*hdr.height {
		return DecodedImage{}, newError(Truncated, Bmp, true, "pixel data truncated (4-bit paletted)")
	}
	pixels := make([]byte, hdr.width*hdr.height*3)
	for srcRow := 0; srcRow < hdr.height; srcRow++ {
		dstRow := srcRow
		if !hdr.topDown {
			dstRow = hdr.height - 1 - srcRow
		}
		for x := 0; x < hdr.width; x++ {
			byteOff := hdr.pixelDataOffset + srcRow*stride + (x / 2)
			bv := int(b[byteOff])
			var idx int
			if x%2 == 0 {
				idx = (bv >> 4) & 0xF
			} else {
				idx = bv & 0xF
			}
			if idx >= entryCount {
				idx = entryCount - 1
			}
			dstIdx := (dstRow*hdr.width + x) * 3
			pixels[dstIdx] = palette[idx][0]   // R
			pixels[dstIdx+1] = palette[idx][1] // G
			pixels[dstIdx+2] = palette[idx][2] // B
		}
	}
	return DecodedImage{Width: hdr.width, Height: hdr.height, Data: pixels, Channels: RGB, Format: Bmp}, nil
}

func decodeBmpPal1(b []byte, hdr *bmpHeader) (DecodedImage, error) {
	entryCount := hdr.clrUsed
	if entryCount <= 0 {
		entryCount = 2
	}
	palette, err := readColorTable(b, hdr, entryCount)
	if err != nil {
		return DecodedImage{}, err
	}
	// Row stride: ceil(width / 32) * 4 bytes = ((width + 31) / 32) * 4
	stride := ((hdr.width + 31) / 32) * 4
	if len(b)-hdr.pixelDataOffset < stride*hdr.height {
		return DecodedImage{}, newError(Truncated, Bmp, true, "pixel data truncated (1-bit paletted)")
	}
	pixels := make([]byte, hdr.width*hdr.height*3)
	for srcRow := 0; srcRow < hdr.height; srcRow++ {
		dstRow := srcRow
		if !hdr.topDown {
			dstRow = hdr.height - 1 - srcRow
		}
		for x := 0; x < hdr.width; x++ {
			byteOff := hdr.pixelDataOffset + srcRow*stride + (x / 8)
			bv := int(b[byteOff])
			// MSB first: bit 7 is pixel 0
			idx := (bv >> (7 - (x % 8))) & 1
			if idx >= entryCount {
				idx = entryCount - 1
			}
			dstIdx := (dstRow*hdr.width + x) * 3
			pixels[dstIdx] = palette[idx][0]   // R
			pixels[dstIdx+1] = palette[idx][1] // G
			pixels[dstIdx+2] = palette[idx][2] // B
		}
	}
	return DecodedImage{Width: hdr.width, Height: hdr.height, Data: pixels, Channels: RGB, Format: Bmp}, nil
}

func decodeBmpBitfields(b []byte, hdr *bmpHeader, bitsPerPixel int) (DecodedImage, error) {
	hasAlpha := hdr.alphaMask != 0
	channels := RGB
	if hasAlpha {
		channels = RGBA
	}

	// Pre-compute shifts and ranges for each channel
	redShift := bits.TrailingZeros32(hdr.redMask)
	greenShift := bits.TrailingZeros32(hdr.greenMask)
	blueShift := bits.TrailingZeros32(hdr.blueMask)
	redRange := uint64(hdr.redMask >> redShift)
	greenRange := uint64(hdr.greenMask >> greenShift)
	blueRange := uint64(hdr.blueMask >> blueShift)

	alphaShift := 0
	alphaRange := uint64(1)
	if hasAlpha {
		alphaShift = bits.TrailingZeros32(hdr.alphaMask)
		alphaRange = uint64(hdr.alphaMask >> alphaShift)
	}

	var stride int
	if bitsPerPixel == 16 {
		stride = ((hdr.width*2 + 3) / 4) * 4
	} else {
		stride = hdr.width * 4
	}
	if len(b)-hdr.pixelDataOffset < stride*hdr.height {
		return DecodedImage{}, newError(Truncated, Bmp, true,
			fmt.Sprintf("pixel data truncated (BI_BITFIELDS %d-bit)", bitsPerPixel))
	}

	pixBytes := channels.BytesPerPixel()
	pixels := make([]byte, hdr.width*hdr.height*pixBytes)
	for srcRow := 0; srcRow < hdr.height; srcRow++ {
		dstRow := srcRow
		if !hdr.topDown {
			dstRow = hdr.height - 1 - srcRow
		}
		for x := 0; x < hdr.width; x++ {
			srcIdx := hdr.pixelDataOffset + srcRow*stride
			var pixel uint64
			if bitsPerPixel == 16 {
				pixel = uint64(binary.LittleEndian.Uint16(b[srcIdx+x*2 : srcIdx+x*2+2]))
			} else {
				pixel = uint64(binary.LittleEndian.Uint32(b[srcIdx+x*4 : srcIdx+x*4+4]))
			}
			maskedR := uint64(pixel&uint64(hdr.redMask)) >> redShift
			maskedG := uint64(pixel&uint64(hdr.greenMask)) >> greenShift
			maskedB := uint64(pixel&uint64(hdr.blueMask)) >> blueShift
			r := uint8(maskedR * 255 / redRange)
			g := uint8(maskedG * 255 / greenRange)
			bChannel := uint8(maskedB * 255 / blueRange)
			dstIdx := (dstRow*hdr.width + x) * pixBytes
			pixels[dstIdx] = r
			pixels[dstIdx+1] = g
			pixels[dstIdx+2] = bChannel
			if hasAlpha {
				maskedA := uint64(pixel&uint64(hdr.alphaMask)) >> alphaShift
				pixels[dstIdx+3] = uint8(maskedA * 255 / alphaRange)
			}
		}
	}
	return DecodedImage{Width: hdr.width, Height: hdr.height, Data: pixels, Channels: channels, Format: Bmp}, nil
}

func decodeBmpRle(b []byte, hdr *bmpHeader, bitsPerPixel int) (DecodedImage, error) {
	entryCount := hdr.clrUsed
	if entryCount <= 0 {
		if bitsPerPixel == 8 {
			entryCount = 256
		} else {
			entryCount = 16
		}
	}
	palette, err := readColorTable(b, hdr, entryCount)
	if err != nil {
		return DecodedImage{}, err
	}

	xsize := hdr.width
	ysize := hdr.height

	// Replicate Pillow's BmpRleDecoder exactly.
	// Pillow accumulates pixel indices into a flat buffer in file-scanline order,
	// then calls set_as_raw with direction=-1 (bottom-up) or +1 (top-down).
	dataBuf := make([]int, 0, xsize*ysize)
	x := 0
	pos := hdr.pixelDataOffset
	end := len(b)

	total := xsize * ysize
outer:
	for len(dataBuf) < total {
		if pos+1 >= end {
			break
		}
		numPixels := int(b[pos])
		pos++
		dataByte := int(b[pos])
		pos++

		if numPixels != 0 {
			// Encoded mode: clip at end of row (Pillow behavior)
			if x+numPixels > xsize {
				numPixels = xsize - x
				if numPixels < 0 {
					numPixels = 0
				}
			}
			if bitsPerPixel == 8 {
				for i := 0; i < numPixels; i++ {
					dataBuf = append(dataBuf, dataByte)
				}
			} else {
				// RLE4: alternating high/low nibble
				for i := 0; i < numPixels; i++ {
					if i%2 == 0 {
						dataBuf = append(dataBuf, (dataByte>>4)&0xF)
					} else {
						dataBuf = append(dataBuf, dataByte&0xF)
					}
				}
			}
			x += numPixels
		} else {
			if dataByte == 0 {
				// EOL: pad with zeros to next row boundary (Pillow behavior)
				for len(dataBuf)%xsize != 0 {
					dataBuf = append(dataBuf, 0)
				}
				x = 0
			} else if dataByte == 1 {
				// End of bitmap
				break outer
			} else if dataByte == 2 {
				// Delta: Pillow reads 4 bytes (first 2 discarded, second 2 are dx/dy).
				// This is a Pillow bug we must replicate.
				if pos+3 >= end {
					break
				}
				pos += 2 // skip first 2 bytes (discarded in Pillow)
				right := int(b[pos])
				pos++
				up := int(b[pos])
				pos++
				zeros := right + up*xsize
				for i := 0; i < zeros; i++ {
					dataBuf = append(dataBuf, 0)
				}
				x = len(dataBuf) % xsize
			} else {
				// Absolute mode: dataByte >= 3 pixels follow
				numAbs := dataByte
				var byteCount int
				if bitsPerPixel == 8 {
					byteCount = numAbs
				} else {
					// RLE4: Pillow uses floor division (byte[0] // 2), NOT ceil
					byteCount = numAbs / 2
				}
				if pos+byteCount > end {
					break
				}
				if bitsPerPixel == 8 {
					for i := 0; i < byteCount; i++ {
						dataBuf = append(dataBuf, int(b[pos+i]))
					}
				} else {
					// RLE4: emit both nibbles of each byte read
					for i := 0; i < byteCount; i++ {
						bv := int(b[pos+i])
						dataBuf = append(dataBuf, (bv>>4)&0xF)
						dataBuf = append(dataBuf, bv&0xF)
					}
				}
				x += numAbs
				pos += byteCount
				// Word-align: check if (pos - hdr.pixelDataOffset) % 2 != 0
				if (pos-hdr.pixelDataOffset)%2 != 0 {
					pos++ // skip padding byte
				}
			}
		}
	}

	// Detect RLE overrun: if loop exited before buffer is full, the stream is corrupt.
	if len(dataBuf) < xsize*ysize {
		return DecodedImage{}, newError(CorruptInput, Bmp, true,
			fmt.Sprintf("RLE stream ended with %d pixels, expected %d", len(dataBuf), xsize*ysize))
	}

	// Build output pixels.
	// Pillow's set_as_raw with direction=-1 reverses rows:
	// image row i = buffer row (ysize - 1 - i) for bottom-up.
	// For top-down (direction=+1), image row i = buffer row i.
	pixels := make([]byte, xsize*ysize*3)
	for bufRow := 0; bufRow < ysize; bufRow++ {
		imgRow := bufRow
		if !hdr.topDown {
			imgRow = ysize - 1 - bufRow
		}
		for col := 0; col < xsize; col++ {
			palIdx := dataBuf[bufRow*xsize+col]
			if palIdx >= entryCount {
				palIdx = entryCount - 1
			}
			rgb := palette[palIdx]
			dstIdx := (imgRow*xsize + col) * 3
			pixels[dstIdx] = rgb[0]   // R
			pixels[dstIdx+1] = rgb[1] // G
			pixels[dstIdx+2] = rgb[2] // B
		}
	}

	return DecodedImage{Width: hdr.width, Height: hdr.height, Data: pixels, Channels: RGB, Format: Bmp}, nil
}
