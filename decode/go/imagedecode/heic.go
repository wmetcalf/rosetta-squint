package imagedecode

import (
	"github.com/strukturag/libheif/go/heif"
)

func decodeHeic(b []byte) (DecodedImage, error) {
	ctx, err := heif.NewContext()
	if err != nil {
		return DecodedImage{}, newError(CorruptInput, Heic, true, "heif.NewContext: "+err.Error())
	}

	if err := ctx.ReadFromMemory(b); err != nil {
		return DecodedImage{}, newError(CorruptInput, Heic, true, "ReadFromMemory: "+err.Error())
	}

	handle, err := ctx.GetPrimaryImageHandle()
	if err != nil {
		return DecodedImage{}, newError(CorruptInput, Heic, true, "GetPrimaryImageHandle: "+err.Error())
	}

	if err := checkDimensions(handle.GetWidth(), handle.GetHeight(), Heic); err != nil {
		return DecodedImage{}, err
	}

	hasAlpha := handle.HasAlphaChannel()
	var chroma heif.Chroma
	if hasAlpha {
		chroma = heif.ChromaInterleavedRGBA
	} else {
		chroma = heif.ChromaInterleavedRGB
	}

	img, err := handle.DecodeImage(heif.ColorspaceRGB, chroma, nil)
	if err != nil {
		return DecodedImage{}, newError(CorruptInput, Heic, true, "DecodeImage: "+err.Error())
	}

	plane, err := img.GetPlane(heif.ChannelInterleaved)
	if err != nil {
		return DecodedImage{}, newError(CorruptInput, Heic, true, "GetPlane: "+err.Error())
	}

	width := img.GetWidth(heif.ChannelInterleaved)
	height := img.GetHeight(heif.ChannelInterleaved)
	stride := plane.Stride
	bpp := 3
	channels := RGB
	if hasAlpha {
		bpp = 4
		channels = RGBA
	}

	out := make([]byte, width*height*bpp)
	src := plane.Plane
	for y := 0; y < height; y++ {
		srcRow := y * stride
		copy(out[y*width*bpp:(y+1)*width*bpp], src[srcRow:srcRow+width*bpp])
	}

	return DecodedImage{Width: width, Height: height, Data: out, Channels: channels, Format: Heic}, nil
}
