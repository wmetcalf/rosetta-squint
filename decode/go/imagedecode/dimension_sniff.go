package imagedecode

// Pre-decode dimension sniffers for formats whose native library refuses
// dimensions that exceed its own internal limits before our MAX_PIXELS
// check has a chance to run.
//
// Concretely:
//   - libwebp's WebPGetFeatures returns VP8_STATUS_BITSTREAM_ERROR for
//     VP8X-declared canvas dimensions that fail libwebp's internal validation.
//   - libheif (via the strukturag cgo binding) returns dimensions from the
//     underlying HEVC bitstream rather than the container's ispe box, so even
//     a patched ispe is ignored by handle.GetWidth/GetHeight.
//
// By peeking at the file's declared dimensions ourselves before invoking the
// native library, we ensure that "header says it's too large" produces a
// clean imageTooLarge error instead of corruptInput. Spec §3.1 requires this
// ordering.

// sniffWebpDimensions reads canvas dimensions from a WebP file's VP8X chunk
// header. VP8X stores width-1 and height-1 as little-endian 24-bit integers
// at offsets 24 and 27 from the start of the file. Returns ok=false for
// non-VP8X WebPs (VP8/VP8L); those rarely exceed libwebp's 14-bit per-side
// limit so the existing post-WebPGetFeatures check covers them.
func sniffWebpDimensions(b []byte) (int, int, bool) {
	if len(b) < 30 {
		return 0, 0, false
	}
	if string(b[0:4]) != "RIFF" || string(b[8:12]) != "WEBP" {
		return 0, 0, false
	}
	if string(b[12:16]) != "VP8X" {
		return 0, 0, false
	}
	wMinus1 := uint32(b[24]) | uint32(b[25])<<8 | uint32(b[26])<<16
	hMinus1 := uint32(b[27]) | uint32(b[28])<<8 | uint32(b[29])<<16
	return int(wMinus1) + 1, int(hMinus1) + 1, true
}

// sniffHeicDimensions scans an HEIF/HEIC byte stream for the first "ispe"
// (Image Spatial Extents) fourcc and reads the 4-byte big-endian width and
// height that follow the 4-byte version+flags field. The box normally lives
// inside meta -> iprp -> ipco -> ispe but we just scan linearly for the
// fourcc. Capped at 1 MiB of prefix to keep the scan bounded for adversarial
// inputs.
func sniffHeicDimensions(b []byte) (int, int, bool) {
	if len(b) < 30 {
		return 0, 0, false
	}
	scanLimit := len(b) - 16
	if scanLimit > 1024*1024 {
		scanLimit = 1024 * 1024
	}
	for i := 0; i < scanLimit; i++ {
		if b[i] == 'i' && b[i+1] == 's' && b[i+2] == 'p' && b[i+3] == 'e' {
			wOff := i + 4 + 4 // skip "ispe" type + version+flags
			if wOff+8 > len(b) {
				return 0, 0, false
			}
			width := uint32(b[wOff])<<24 | uint32(b[wOff+1])<<16 |
				uint32(b[wOff+2])<<8 | uint32(b[wOff+3])
			height := uint32(b[wOff+4])<<24 | uint32(b[wOff+5])<<16 |
				uint32(b[wOff+6])<<8 | uint32(b[wOff+7])
			if width == 0 || height == 0 {
				return 0, 0, false
			}
			return int(width), int(height), true
		}
	}
	return 0, 0, false
}
