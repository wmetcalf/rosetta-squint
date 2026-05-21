package imagedecode

import (
	"github.com/chai2010/webp"
)

func decodeWebp(b []byte) (DecodedImage, error) {
	// Use libwebp's WebPGetFeatures (via GetInfo) to detect alpha.
	// This is authoritative: it reads the bitstream features directly.
	_, _, hasAlpha, err := webp.GetInfo(b)
	if err != nil {
		// GetInfo failed — likely corrupt or truncated header.
		return DecodedImage{}, newError(CorruptInput, Webp, true, "webp.GetInfo failed: "+err.Error())
	}

	if hasAlpha {
		// DecodeRGBA returns *image.RGBA whose Pix is packed RGBA bytes from libwebp
		// (WebPDecodeRGBA). No premultiplication — straight alpha.
		img, err := webp.DecodeRGBA(b)
		if err != nil {
			return DecodedImage{}, newError(CorruptInput, Webp, true, "webp.DecodeRGBA failed: "+err.Error())
		}
		bnds := img.Bounds()
		width := bnds.Dx()
		height := bnds.Dy()

		// Copy pixel rows from img.Pix (stride may be > 4*width for padded images).
		out := make([]byte, width*height*4)
		idx := 0
		for y := bnds.Min.Y; y < bnds.Max.Y; y++ {
			rowStart := img.PixOffset(bnds.Min.X, y)
			copy(out[idx:idx+width*4], img.Pix[rowStart:rowStart+width*4])
			idx += width * 4
		}
		return DecodedImage{Width: width, Height: height, Data: out, Channels: RGBA, Format: Webp}, nil
	}

	// DecodeRGB returns *RGBImage whose XPix is packed RGB bytes from libwebp
	// (WebPDecodeRGB). Stride is 3*width for square decode.
	img, err := webp.DecodeRGB(b)
	if err != nil {
		return DecodedImage{}, newError(CorruptInput, Webp, true, "webp.DecodeRGB failed: "+err.Error())
	}
	bnds := img.Bounds()
	width := bnds.Dx()
	height := bnds.Dy()

	// Copy pixel rows from img.XPix (stride may differ from 3*width).
	out := make([]byte, width*height*3)
	idx := 0
	for y := bnds.Min.Y; y < bnds.Max.Y; y++ {
		rowStart := img.PixOffset(bnds.Min.X, y)
		copy(out[idx:idx+width*3], img.XPix[rowStart:rowStart+width*3])
		idx += width * 3
	}
	return DecodedImage{Width: width, Height: height, Data: out, Channels: RGB, Format: Webp}, nil
}
