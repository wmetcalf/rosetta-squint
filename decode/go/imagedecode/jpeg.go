package imagedecode

import (
	"bytes"

	libjpeg "github.com/pixiv/go-libjpeg/jpeg"
)

func decodeJpeg(b []byte) (DecodedImage, error) {
	// Check for CMYK/YCCK before invoking the decoder.
	if isCmyk(b) {
		return DecodedImage{}, newError(UnsupportedFeature, Jpeg, true, "CMYK color space")
	}

	// DecodeIntoRGB forces out_color_space=JCS_RGB in libjpeg-turbo,
	// so YCbCr→RGB conversion is done by libjpeg (matching Rust/JS goldens).
	// Grayscale is also expanded to 3-channel RGB by libjpeg.
	img, err := libjpeg.DecodeIntoRGB(bytes.NewReader(b), &libjpeg.DecoderOptions{
		DCTMethod:              libjpeg.DCTISlow,
		DisableFancyUpsampling: false,
		DisableBlockSmoothing:  false,
	})
	if err != nil {
		return DecodedImage{}, newError(CorruptInput, Jpeg, true, "jpeg.DecodeIntoRGB failed: "+err.Error())
	}

	bnds := img.Bounds()
	width := bnds.Dx()
	height := bnds.Dy()

	// rgb.Image.Pix is stored in R,G,B order with stride 3*width (no padding for our case,
	// but we copy row-by-row to be safe with any non-zero Min bounds).
	out := make([]byte, width*height*3)
	idx := 0
	for y := bnds.Min.Y; y < bnds.Max.Y; y++ {
		rowStart := (y-bnds.Min.Y)*img.Stride + 0
		for x := bnds.Min.X; x < bnds.Max.X; x++ {
			offs := rowStart + (x-bnds.Min.X)*3
			out[idx] = img.Pix[offs]
			out[idx+1] = img.Pix[offs+1]
			out[idx+2] = img.Pix[offs+2]
			idx += 3
		}
	}

	return DecodedImage{Width: width, Height: height, Data: out, Channels: RGB, Format: Jpeg}, nil
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
