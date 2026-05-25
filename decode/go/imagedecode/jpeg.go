package imagedecode

import (
	"github.com/wmetcalf/rosetta-image-decode/go/imagedecode/internal/libjpeg"
)

func decodeJpeg(b []byte) (DecodedImage, error) {
	// Check for CMYK/YCCK before invoking the decoder.
	if isCmyk(b) {
		return DecodedImage{}, newError(UnsupportedFeature, Jpeg, true, "CMYK color space")
	}

	// Use libjpeg.Header to get dimensions and guard against decompression bombs.
	width, height, err := libjpeg.Header(b)
	if err != nil {
		return DecodedImage{}, newError(CorruptInput, Jpeg, true, "libjpeg header: "+err.Error())
	}
	if err := checkDimensions(width, height, Jpeg); err != nil {
		return DecodedImage{}, err
	}

	// DecodeRGB uses TJFLAG_ACCURATEDCT (JDCT_ISLOW) to match PIL output byte-exact.
	// libjpeg-turbo forces YCbCr→RGB conversion and expands grayscale to 3-channel RGB.
	decoded, err := libjpeg.DecodeRGB(b)
	if err != nil {
		return DecodedImage{}, newError(CorruptInput, Jpeg, true, "libjpeg decode: "+err.Error())
	}
	return DecodedImage{
		Width:    decoded.Width,
		Height:   decoded.Height,
		Data:     decoded.Pixels,
		Channels: RGB,
		Format:   Jpeg,
	}, nil
}

// jpegDimensions scans JPEG markers to find the first SOF (Start Of Frame) marker
// and returns the image width and height. Returns (0, 0, false) if not found.
// SOF layout: FF Cn [length 2 bytes] [precision 1 byte] [height 2 bytes] [width 2 bytes] ...
//
// Currently unused; kept for parity with the other ports' JPEG helpers and
// available for future limit/dimension-sniff use.
//nolint:unused
func jpegDimensions(b []byte) (width, height int, ok bool) {
	for i := 0; i+1 < len(b); i++ {
		if b[i] != 0xFF {
			continue
		}
		mk := b[i+1]
		switch mk {
		case 0xC0, 0xC1, 0xC2, 0xC9, 0xCA:
			// SOF marker: precision(1) height(2) width(2) at i+4..i+8
			if i+8 < len(b) {
				h := int(b[i+4])<<8 | int(b[i+5])
				w := int(b[i+6])<<8 | int(b[i+7])
				return w, h, true
			}
		case 0x00, 0xFF:
			// stuffed byte or padding; continue
		case 0xD8, 0xD9:
			// SOI / EOI — no length field
		default:
			if i+3 < len(b) {
				length := int(b[i+2])<<8 | int(b[i+3])
				i += 1 + length
			}
		}
	}
	return 0, 0, false
}

// isCmyk scans JPEG markers to find the first SOF (Start Of Frame) marker
// and returns true if the image has 4 components (CMYK or YCCK).
func isCmyk(b []byte) bool {
	// JPEG markers are 0xFF followed by a marker byte.
	// SOF markers: C0 (baseline), C1 (extended sequential), C2 (progressive),
	//              C9 (arithmetic sequential), CA (arithmetic progressive).
	// SOF layout: FF Cn [length 2 bytes] [precision 1 byte] [height 2 bytes] [width 2 bytes] [num_components 1 byte]
	// num_components is at offset +9 from the start of the FF byte.
	for i := 0; i+1 < len(b); i++ {
		if b[i] != 0xFF {
			continue
		}
		mk := b[i+1]
		switch mk {
		case 0xC0, 0xC1, 0xC2, 0xC9, 0xCA:
			// SOF marker found; num_components is at i+9
			if i+9 < len(b) {
				return b[i+9] == 4
			}
		case 0x00, 0xFF:
			// 0x00 = stuffed byte (not a real marker), 0xFF = padding; skip one byte
			// (the outer loop increments i too, so we land on next byte correctly)
		case 0xD8, 0xD9:
			// SOI / EOI — no length field; continue scanning
		default:
			// All other markers have a 2-byte length field after the marker byte.
			// Skip past this marker: i+1 (marker byte) + 1 + length = i+1+1+length
			if i+3 < len(b) {
				length := int(b[i+2])<<8 | int(b[i+3])
				i += 1 + length // outer loop adds 1 more
			}
		}
	}
	return false
}
