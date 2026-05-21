package imagedecode

// Decode auto-detects the format from magic bytes and returns the decoded image,
// or a *DecodeError on failure.
func Decode(b []byte) (DecodedImage, error) {
	f, ok := DetectFormat(b)
	if !ok {
		return DecodedImage{}, newError(UnsupportedFormat, 0, false, "")
	}
	switch f {
	case Bmp:
		return decodeBmp(b)
	default:
		return DecodedImage{}, newError(UnsupportedFormat, f, true, "")
	}
}

// DetectFormat returns the Format and true if the magic bytes match a known format.
func DetectFormat(b []byte) (Format, bool) {
	if len(b) < 2 {
		return 0, false
	}
	if b[0] == 0x42 && b[1] == 0x4D {
		return Bmp, true
	}
	return 0, false
}

// SupportedFormats returns the list of formats this port can decode.
func SupportedFormats() []Format {
	return []Format{Bmp}
}
