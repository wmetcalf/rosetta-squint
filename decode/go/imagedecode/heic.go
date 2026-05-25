package imagedecode

import (
	"fmt"

	"github.com/strukturag/libheif/go/heif"
)

func decodeHeic(b []byte) (DecodedImage, error) {
	// Sniff the container's primary-item ispe dimensions BEFORE invoking
	// libheif. libheif returns dimensions from the underlying HEVC bitstream
	// rather than the container's ispe, so a patched ispe is never visible
	// via handle.GetWidth/GetHeight — without this pre-check the file decodes
	// at its HEVC dimensions and the imageTooLarge guard never fires.
	// Spec §3.1.
	if w, h, ok := sniffHeicDimensions(b); ok {
		if err := checkDimensions(w, h, Heic); err != nil {
			return DecodedImage{}, err
		}
	}

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
	// Validate post-decode plane dimensions match the handle dimensions we
	// capacity-checked above. A mismatch (e.g. a corrupt input that causes
	// libheif to return a smaller plane than advertised) would otherwise
	// cause the row-copy below to OOB-read src or under-fill out.
	handleWidth := handle.GetWidth()
	handleHeight := handle.GetHeight()
	if width != handleWidth || height != handleHeight {
		return DecodedImage{}, newError(CorruptInput, Heic, true,
			fmt.Sprintf("plane dimensions %dx%d do not match handle dimensions %dx%d",
				width, height, handleWidth, handleHeight))
	}
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
