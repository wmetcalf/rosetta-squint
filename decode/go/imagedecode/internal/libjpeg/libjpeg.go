// Package libjpeg provides byte-exact JPEG decoding via system libjpeg-turbo
// (TurboJPEG API). Replaces the unmaintained github.com/pixiv/go-libjpeg.
package libjpeg

/*
#cgo pkg-config: libturbojpeg
#include <stdlib.h>
#include <turbojpeg.h>
*/
import "C"
import (
	"fmt"
	"unsafe"
)

// Decoded holds the result of a JPEG decode.
type Decoded struct {
	Width    int
	Height   int
	HasAlpha bool   // libjpeg-turbo doesn't report alpha; always false for JPEG
	Pixels   []byte // interleaved RGB, len = Width*Height*3
}

// Header returns dimensions without full decode. Useful for MAX_PIXELS check.
func Header(jpegBytes []byte) (width, height int, err error) {
	if len(jpegBytes) == 0 {
		return 0, 0, fmt.Errorf("libjpeg: empty input")
	}
	h := C.tjInitDecompress()
	if h == nil {
		return 0, 0, fmt.Errorf("libjpeg: tjInitDecompress failed")
	}
	defer C.tjDestroy(h)

	var w, hgt, subsamp, colorspace C.int
	rc := C.tjDecompressHeader3(h,
		(*C.uchar)(unsafe.Pointer(&jpegBytes[0])),
		C.ulong(len(jpegBytes)),
		&w, &hgt, &subsamp, &colorspace)
	if rc != 0 {
		return 0, 0, fmt.Errorf("libjpeg: tjDecompressHeader3 failed: %s", C.GoString(C.tjGetErrorStr2(h)))
	}
	return int(w), int(hgt), nil
}

// DecodeRGB decodes JPEG bytes to interleaved RGB (no alpha, 3 bytes per pixel).
// Uses TJFLAG_ACCURATEDCT (JDCT_ISLOW) to match PIL output byte-exact.
func DecodeRGB(jpegBytes []byte) (*Decoded, error) {
	if len(jpegBytes) == 0 {
		return nil, fmt.Errorf("libjpeg: empty input")
	}
	h := C.tjInitDecompress()
	if h == nil {
		return nil, fmt.Errorf("libjpeg: tjInitDecompress failed")
	}
	defer C.tjDestroy(h)

	var w, hgt, subsamp, colorspace C.int
	if rc := C.tjDecompressHeader3(h,
		(*C.uchar)(unsafe.Pointer(&jpegBytes[0])),
		C.ulong(len(jpegBytes)),
		&w, &hgt, &subsamp, &colorspace); rc != 0 {
		return nil, fmt.Errorf("libjpeg: tjDecompressHeader3 failed: %s", C.GoString(C.tjGetErrorStr2(h)))
	}
	width, height := int(w), int(hgt)
	out := make([]byte, width*height*3)
	rc := C.tjDecompress2(h,
		(*C.uchar)(unsafe.Pointer(&jpegBytes[0])),
		C.ulong(len(jpegBytes)),
		(*C.uchar)(unsafe.Pointer(&out[0])),
		C.int(width),
		C.int(width*3), // pitch
		C.int(height),
		C.TJPF_RGB,
		C.TJFLAG_ACCURATEDCT)
	if rc != 0 {
		return nil, fmt.Errorf("libjpeg: tjDecompress2 failed: %s", C.GoString(C.tjGetErrorStr2(h)))
	}
	return &Decoded{Width: width, Height: height, Pixels: out}, nil
}
