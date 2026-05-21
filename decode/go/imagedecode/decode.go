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
	case Png:
		return decodePng(b)
	case Gif:
		return decodeGif(b)
	case Jpeg:
		return decodeJpeg(b)
	case Webp:
		return decodeWebp(b)
	case Tiff:
		return decodeTiff(b)
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
	if len(b) >= 8 &&
		b[0] == 0x89 && b[1] == 0x50 && b[2] == 0x4E && b[3] == 0x47 &&
		b[4] == 0x0D && b[5] == 0x0A && b[6] == 0x1A && b[7] == 0x0A {
		return Png, true
	}
	if len(b) >= 6 &&
		b[0] == 0x47 && b[1] == 0x49 && b[2] == 0x46 && b[3] == 0x38 &&
		(b[4] == 0x37 || b[4] == 0x39) && b[5] == 0x61 {
		return Gif, true
	}
	if len(b) >= 2 && b[0] == 0xFF && b[1] == 0xD8 {
		return Jpeg, true
	}
	if len(b) >= 12 &&
		b[0] == 0x52 && b[1] == 0x49 && b[2] == 0x46 && b[3] == 0x46 &&
		b[8] == 0x57 && b[9] == 0x45 && b[10] == 0x42 && b[11] == 0x50 {
		return Webp, true
	}
	// TIFF little-endian: II (0x49 0x49) + magic 42 (0x2A 0x00)
	if len(b) >= 4 && b[0] == 0x49 && b[1] == 0x49 && b[2] == 0x2A && b[3] == 0x00 {
		return Tiff, true
	}
	// TIFF big-endian: MM (0x4D 0x4D) + magic 42 (0x00 0x2A)
	if len(b) >= 4 && b[0] == 0x4D && b[1] == 0x4D && b[2] == 0x00 && b[3] == 0x2A {
		return Tiff, true
	}
	return 0, false
}

// SupportedFormats returns the list of formats this port can decode.
func SupportedFormats() []Format {
	return []Format{Bmp, Png, Gif, Jpeg, Webp, Tiff}
}
