package imagedecode

// Channels is the number of color channels in a DecodedImage's pixel buffer.
type Channels int

const (
	RGB  Channels = 3
	RGBA Channels = 4
)

// BytesPerPixel returns 3 or 4.
func (c Channels) BytesPerPixel() int {
	return int(c)
}

// Format identifies an image format.
type Format int

const (
	Bmp Format = iota
	Png
	Gif
	Jpeg
	Webp
	Tiff
	Heic
	Emf
	Wmf
)

func (f Format) String() string {
	switch f {
	case Bmp:
		return "bmp"
	case Png:
		return "png"
	case Gif:
		return "gif"
	case Jpeg:
		return "jpeg"
	case Webp:
		return "webp"
	case Tiff:
		return "tiff"
	case Heic:
		return "heic"
	case Emf:
		return "emf"
	case Wmf:
		return "wmf"
	}
	return "unknown"
}

// DecodedImage is the result of a successful Decode call.
type DecodedImage struct {
	Width    int
	Height   int
	Data     []byte
	Channels Channels
	Format   Format
}
