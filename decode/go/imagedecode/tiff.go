package imagedecode

import (
	"bytes"
	"image/color"

	"golang.org/x/image/tiff"
)

func decodeTiff(b []byte) (DecodedImage, error) {
	// Sniff dimensions without full decode to guard against decompression bombs.
	cfg, err := tiff.DecodeConfig(bytes.NewReader(b))
	if err != nil {
		return DecodedImage{}, newError(CorruptInput, Tiff, true, "tiff.DecodeConfig failed: "+err.Error())
	}
	if err := checkDimensions(cfg.Width, cfg.Height, Tiff); err != nil {
		return DecodedImage{}, err
	}

	img, err := tiff.Decode(bytes.NewReader(b))
	if err != nil {
		return DecodedImage{}, newError(CorruptInput, Tiff, true, "tiff.Decode failed: "+err.Error())
	}
	bnds := img.Bounds()
	width := bnds.Dx()
	height := bnds.Dy()

	// Determine alpha: NRGBA/NRGBA64 models carry straight alpha.
	hasAlpha := false
	cm := img.ColorModel()
	switch cm {
	case color.NRGBAModel, color.NRGBA64Model:
		hasAlpha = true
	}

	channelCount := 3
	channels := RGB
	if hasAlpha {
		channelCount = 4
		channels = RGBA
	}

	out := make([]byte, width*height*channelCount)
	idx := 0
	for y := bnds.Min.Y; y < bnds.Max.Y; y++ {
		for x := bnds.Min.X; x < bnds.Max.X; x++ {
			pv := img.At(x, y)
			var r, g, bl, a byte
			switch v := pv.(type) {
			case color.NRGBA:
				r, g, bl, a = v.R, v.G, v.B, v.A
			case color.NRGBA64:
				r, g, bl, a = byte(v.R>>8), byte(v.G>>8), byte(v.B>>8), byte(v.A>>8)
			case color.Gray:
				r, g, bl, a = v.Y, v.Y, v.Y, 255
			case color.Gray16:
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

	return DecodedImage{Width: width, Height: height, Data: out, Channels: channels, Format: Tiff}, nil
}
