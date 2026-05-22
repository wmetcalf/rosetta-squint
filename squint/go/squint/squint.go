// Package squint provides a single high-level API for byte-exact perceptual
// image hashing across 6 languages. Each function takes either a file path
// (`PHash`) or raw bytes (`PHashBytes`), internally decodes via
// rosetta-image-decode, and hashes via rosetta-image-hash.
package squint

import (
	"fmt"
	"image"
	"image/color"
	"os"

	"github.com/wmetcalf/rosetta-image-decode/go/imagedecode"
	"github.com/wmetcalf/rosetta-image-hash/go/imagehash"
)

// Hash and ImageMultiHash are re-exported from imagehash for ergonomics.
type Hash = imagehash.Hash
type ImageMultiHash = imagehash.ImageMultiHash

// decodedToImage converts a rosetta-image-decode DecodedImage into a Go
// image.Image (concrete type *image.NRGBA — both RGB and RGBA inputs are
// represented as 4-channel NRGBA with opaque alpha for RGB inputs).
func decodedToImage(d imagedecode.DecodedImage) image.Image {
	img := image.NewNRGBA(image.Rect(0, 0, d.Width, d.Height))
	bpp := 3
	if d.Channels == imagedecode.RGBA {
		bpp = 4
	}
	for y := 0; y < d.Height; y++ {
		for x := 0; x < d.Width; x++ {
			i := (y*d.Width + x) * bpp
			var c color.NRGBA
			if bpp == 3 {
				c = color.NRGBA{R: d.Data[i], G: d.Data[i+1], B: d.Data[i+2], A: 255}
			} else {
				c = color.NRGBA{R: d.Data[i], G: d.Data[i+1], B: d.Data[i+2], A: d.Data[i+3]}
			}
			img.SetNRGBA(x, y, c)
		}
	}
	return img
}

// DecodeFile reads a file and returns a decoded image.Image suitable for
// passing to the imagehash functions directly.
func DecodeFile(path string) (image.Image, error) {
	b, err := os.ReadFile(path)
	if err != nil {
		return nil, fmt.Errorf("read %s: %w", path, err)
	}
	return DecodeBytes(b)
}

// DecodeBytes decodes raw image bytes (any supported format) and returns the
// image as a Go image.Image. Format is auto-detected.
func DecodeBytes(b []byte) (image.Image, error) {
	d, err := imagedecode.Decode(b)
	if err != nil {
		return nil, err
	}
	return decodedToImage(d), nil
}

// PHash decodes the file at path and computes phash with the given hashSize.
func PHash(path string, hashSize int) (Hash, error) {
	img, err := DecodeFile(path)
	if err != nil {
		return Hash{}, err
	}
	return imagehash.PHash(img, hashSize)
}

// PHashBytes is the bytes-input version of PHash.
func PHashBytes(b []byte, hashSize int) (Hash, error) {
	img, err := DecodeBytes(b)
	if err != nil {
		return Hash{}, err
	}
	return imagehash.PHash(img, hashSize)
}

// AverageHash decodes the file at path and computes average hash with the given hashSize.
func AverageHash(path string, hashSize int) (Hash, error) {
	img, err := DecodeFile(path)
	if err != nil {
		return Hash{}, err
	}
	return imagehash.AverageHash(img, hashSize)
}

// AverageHashBytes is the bytes-input version of AverageHash.
func AverageHashBytes(b []byte, hashSize int) (Hash, error) {
	img, err := DecodeBytes(b)
	if err != nil {
		return Hash{}, err
	}
	return imagehash.AverageHash(img, hashSize)
}

// DHash decodes the file at path and computes dhash with the given hashSize.
func DHash(path string, hashSize int) (Hash, error) {
	img, err := DecodeFile(path)
	if err != nil {
		return Hash{}, err
	}
	return imagehash.DHash(img, hashSize)
}

// DHashBytes is the bytes-input version of DHash.
func DHashBytes(b []byte, hashSize int) (Hash, error) {
	img, err := DecodeBytes(b)
	if err != nil {
		return Hash{}, err
	}
	return imagehash.DHash(img, hashSize)
}

// DHashVertical decodes the file at path and computes dhash_vertical with the given hashSize.
func DHashVertical(path string, hashSize int) (Hash, error) {
	img, err := DecodeFile(path)
	if err != nil {
		return Hash{}, err
	}
	return imagehash.DHashVertical(img, hashSize)
}

// DHashVerticalBytes is the bytes-input version of DHashVertical.
func DHashVerticalBytes(b []byte, hashSize int) (Hash, error) {
	img, err := DecodeBytes(b)
	if err != nil {
		return Hash{}, err
	}
	return imagehash.DHashVertical(img, hashSize)
}

// PHashSimple decodes the file at path and computes phash_simple with the given hashSize.
func PHashSimple(path string, hashSize int) (Hash, error) {
	img, err := DecodeFile(path)
	if err != nil {
		return Hash{}, err
	}
	return imagehash.PHashSimple(img, hashSize)
}

// PHashSimpleBytes is the bytes-input version of PHashSimple.
func PHashSimpleBytes(b []byte, hashSize int) (Hash, error) {
	img, err := DecodeBytes(b)
	if err != nil {
		return Hash{}, err
	}
	return imagehash.PHashSimple(img, hashSize)
}

// WHashHaar decodes the file at path and computes whash (Haar wavelet) with the given hashSize.
// hashSize must be a power of 2.
func WHashHaar(path string, hashSize int) (Hash, error) {
	img, err := DecodeFile(path)
	if err != nil {
		return Hash{}, err
	}
	return imagehash.WHashHaar(img, hashSize)
}

// WHashHaarBytes is the bytes-input version of WHashHaar.
func WHashHaarBytes(b []byte, hashSize int) (Hash, error) {
	img, err := DecodeBytes(b)
	if err != nil {
		return Hash{}, err
	}
	return imagehash.WHashHaar(img, hashSize)
}

// WHashDb4 decodes the file at path and computes whash (Daubechies-4 wavelet) with the given hashSize.
// hashSize must be a power of 2.
func WHashDb4(path string, hashSize int) (Hash, error) {
	img, err := DecodeFile(path)
	if err != nil {
		return Hash{}, err
	}
	return imagehash.WHashDb4(img, hashSize)
}

// WHashDb4Bytes is the bytes-input version of WHashDb4.
func WHashDb4Bytes(b []byte, hashSize int) (Hash, error) {
	img, err := DecodeBytes(b)
	if err != nil {
		return Hash{}, err
	}
	return imagehash.WHashDb4(img, hashSize)
}

// WHashDb4Robust decodes the file at path and computes the cross-port-stable
// db4 wavelet hash with the given hashSize. hashSize must be a power of 2.
func WHashDb4Robust(path string, hashSize int) (Hash, error) {
	img, err := DecodeFile(path)
	if err != nil {
		return Hash{}, err
	}
	return imagehash.WHashDb4Robust(img, hashSize)
}

// WHashDb4RobustBytes is the bytes-input version of WHashDb4Robust.
func WHashDb4RobustBytes(b []byte, hashSize int) (Hash, error) {
	img, err := DecodeBytes(b)
	if err != nil {
		return Hash{}, err
	}
	return imagehash.WHashDb4Robust(img, hashSize)
}

// ColorHash decodes the file at path and computes colorhash with the given binbits.
func ColorHash(path string, binbits int) (Hash, error) {
	img, err := DecodeFile(path)
	if err != nil {
		return Hash{}, err
	}
	return imagehash.ColorHash(img, binbits)
}

// ColorHashBytes is the bytes-input version of ColorHash.
func ColorHashBytes(b []byte, binbits int) (Hash, error) {
	img, err := DecodeBytes(b)
	if err != nil {
		return Hash{}, err
	}
	return imagehash.ColorHash(img, binbits)
}

// CropResistantHash decodes the file at path and computes the crop-resistant
// multi-hash using default parameters.
func CropResistantHash(path string) (ImageMultiHash, error) {
	img, err := DecodeFile(path)
	if err != nil {
		return ImageMultiHash{}, err
	}
	return imagehash.CropResistantHash(img)
}

// CropResistantHashBytes is the bytes-input version of CropResistantHash.
func CropResistantHashBytes(b []byte) (ImageMultiHash, error) {
	img, err := DecodeBytes(b)
	if err != nil {
		return ImageMultiHash{}, err
	}
	return imagehash.CropResistantHash(img)
}
