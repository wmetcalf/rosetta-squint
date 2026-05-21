package imagedecode

import (
	"bytes"
	"image"
	"image/color"
	_ "image/png" // register PNG decoder
	"image/png"
)

func decodePng(b []byte) (DecodedImage, error) {
	img, err := png.Decode(bytes.NewReader(b))
	if err != nil {
		return DecodedImage{}, newError(CorruptInput, Png, true, "png.Decode failed: "+err.Error())
	}
	bnds := img.Bounds()
	width := bnds.Dx()
	height := bnds.Dy()

	// Determine if the image has an alpha channel.
	//
	// Go's image/png decoder uses these color models:
	//   RGBA / RGBA64   — opaque RGB (color type 2 or 3, no tRNS with alpha < 255)
	//   NRGBA / NRGBA64 — RGB+A or Gray+A with straight (non-premultiplied) alpha
	//   Gray / Gray16   — opaque grayscale
	//   color.Palette   — paletted; pixel type is RGBA (opaque) or NRGBA (tRNS present)
	//
	// Only NRGBA / NRGBA64 (and Palette images whose pixels expand to NRGBA) have alpha.
	// We detect this from the color model, with a per-pixel fallback for Palette images.
	hasAlpha := false
	cm := img.ColorModel()
	switch cm {
	case color.NRGBAModel, color.NRGBA64Model:
		hasAlpha = true
	default:
		// For paletted images check the type of an actual pixel.
		// When tRNS is present, individual pixels are color.NRGBA; otherwise color.RGBA.
		if _, isPaletted := img.(*image.Paletted); isPaletted {
			// Sample the first pixel (image is always at least 1×1 for valid PNGs).
			pv := img.At(bnds.Min.X, bnds.Min.Y)
			if _, ok := pv.(color.NRGBA); ok {
				hasAlpha = true
			}
		}
	}

	channelCount := 3
	if hasAlpha {
		channelCount = 4
	}

	out := make([]byte, width*height*channelCount)
	idx := 0
	for y := bnds.Min.Y; y < bnds.Max.Y; y++ {
		for x := bnds.Min.X; x < bnds.Max.X; x++ {
			pv := img.At(x, y)
			var r, g, bl, a byte
			// Use type assertions for NRGBA types to avoid premultiplied-alpha corruption.
			//
			// Special case for Gray16: PIL reads 16-bit grayscale PNGs into mode "I"
			// (32-bit int) and converts to 8-bit via min(value, 255) rather than >> 8.
			// Go's color.Gray16.Y holds the raw 16-bit PNG value, so we replicate that.
			//
			// For all other opaque models, .RGBA() returns pre-scaled 16-bit values and
			// >> 8 gives the correct 8-bit value (a=0xFFFF, so no premultiplied issue).
			switch v := pv.(type) {
			case color.NRGBA:
				r, g, bl, a = v.R, v.G, v.B, v.A
			case color.NRGBA64:
				r, g, bl, a = byte(v.R>>8), byte(v.G>>8), byte(v.B>>8), byte(v.A>>8)
			case color.Gray16:
				// Replicate PIL's I->L clip: min(Y, 255), then expand to RGB.
				gray := v.Y
				if gray > 255 {
					gray = 255
				}
				r, g, bl, a = byte(gray), byte(gray), byte(gray), 255
			default:
				rv, gv, bv, av := pv.RGBA()
				r, g, bl, a = byte(rv>>8), byte(gv>>8), byte(bv>>8), byte(av>>8)
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

	channels := RGB
	if hasAlpha {
		channels = RGBA
	}
	return DecodedImage{Width: width, Height: height, Data: out, Channels: channels, Format: Png}, nil
}
