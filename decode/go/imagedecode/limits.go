package imagedecode

import "fmt"

// MaxPixels is the maximum number of pixels (width * height) allowed for any
// decoded image. Images claiming dimensions that exceed this limit are rejected
// before the underlying decoder is invoked, preventing decompression-bomb OOMs.
// Value: 256 * 1024 * 1024 = 268_435_456.
const MaxPixels int64 = 256 * 1024 * 1024

// checkDimensions returns an ImageTooLarge error if width*height exceeds MaxPixels,
// or a CorruptInput error if either dimension is non-positive.
// Use int64 arithmetic to avoid overflow on 32-bit platforms.
func checkDimensions(width, height int, format Format) error {
	if width <= 0 || height <= 0 {
		return newError(CorruptInput, format, true,
			fmt.Sprintf("non-positive dimensions %dx%d", width, height))
	}
	pixels := int64(width) * int64(height)
	if pixels > MaxPixels {
		return newError(ImageTooLarge, format, true,
			fmt.Sprintf("declared dimensions %dx%d = %d pixels exceeds MAX_PIXELS = %d",
				width, height, pixels, MaxPixels))
	}
	return nil
}
