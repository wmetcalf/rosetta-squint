package imagedecode

import (
	"errors"

	"github.com/wmetcalf/rosetta-squint/decode/go/imagedecode/internal/heicwasm"
)

// decodeHeic decodes HEIC via the bundled libheif+libde265 WASM module
// (internal/heicwasm), run in wazero — no cgo, no system libheif. The same
// WASM artifact is shared by every port so HEIC output is byte-identical
// across languages. See decode/wasm/ for the build and HEIC_REPRODUCIBILITY.md
// for why a system libheif binding could not be byte-exact across platforms.
func decodeHeic(b []byte) (DecodedImage, error) {
	// Sniff the container's primary-item ispe dimensions BEFORE decoding.
	// libheif reports dimensions from the HEVC bitstream, not the container's
	// ispe, so a patched ispe is only caught by this pre-check. Spec §3.1.
	if w, h, ok := sniffHeicDimensions(b); ok {
		if err := checkDimensions(w, h, Heic); err != nil {
			return DecodedImage{}, err
		}
	}

	res, err := heicwasm.Decode(b, MaxPixels)
	if err != nil {
		if errors.Is(err, heicwasm.ErrTooLarge) {
			return DecodedImage{}, newError(ImageTooLarge, Heic, true, err.Error())
		}
		return DecodedImage{}, newError(CorruptInput, Heic, true, err.Error())
	}

	channels := RGB
	if res.Channels == 4 {
		channels = RGBA
	}
	return DecodedImage{
		Width:    res.Width,
		Height:   res.Height,
		Data:     res.Data,
		Channels: channels,
		Format:   Heic,
	}, nil
}
