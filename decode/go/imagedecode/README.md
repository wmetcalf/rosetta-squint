# rosetta-squint-decode — Go port

Byte-exact PIL-compatible image decoder library, Go port (1.22+, current module is on 1.25).

Decodes BMP, PNG, GIF (first frame), JPEG, WebP, TIFF, and HEIC to a raw RGB or RGBA byte buffer matching what `PIL.Image.open(...).tobytes()` produces.

## Quick start

```go
package main

import (
    "errors"
    "fmt"
    "log"
    "os"

    "github.com/wmetcalf/rosetta-squint/decode/go/imagedecode"
)

func main() {
    bytes, err := os.ReadFile("photo.jpg")
    if err != nil { log.Fatal(err) }

    if format, ok := imagedecode.DetectFormat(bytes); ok {
        fmt.Println("detected:", format)
    }

    img, err := imagedecode.Decode(bytes)
    if err != nil {
        var de *imagedecode.DecodeError
        if errors.As(err, &de) {
            fmt.Println("decode failed:", de.Kind, de.Detail)
        }
        return
    }
    fmt.Printf("%dx%d channels=%v format=%v\n",
        img.Width, img.Height, img.Channels, img.Format)
    // img.Data is []byte, len = Width * Height * (3 or 4)
}
```

## Build + test

```
cd go/imagedecode
go test -count=1 ./...           # ~38 tests, all passing on Linux x86-64
```

Tests resolve fixtures and goldens from `../../spec/`. Run from this module root.

## API

```go
func Decode(b []byte) (DecodedImage, error)
func DetectFormat(b []byte) (Format, bool)
func SupportedFormats() []Format

type DecodedImage struct {
    Width, Height int
    Data          []byte         // row-major, len = Width * Height * (3 or 4)
    Channels      Channels
    Format        Format
}

type Channels int                 // Rgb | Rgba
type Format   int                 // Bmp | Png | Gif | Jpeg | Webp | Tiff | Heic

type DecodeError struct {
    Kind        DecodeErrorKind
    Format      Format
    FormatKnown bool
    Detail      string
}

type DecodeErrorKind int          // UnsupportedFormat | CorruptInput | Truncated | UnsupportedFeature
```

## Dependencies

```
github.com/pixiv/go-libjpeg              // cgo binding to system libjpeg-turbo
github.com/chai2010/webp                  // cgo binding to system libwebp
github.com/strukturag/libheif v1.17.6     // cgo binding; pin must match system libheif ABI
golang.org/x/image/tiff                   // pure Go TIFF
```

PNG, GIF, and BMP are pure Go (stdlib `image/png`, `image/gif`, hand-written BMP). JPEG/WebP/HEIC are cgo to system libraries.

System packages required (Ubuntu):

```
sudo apt install \
    libjpeg-turbo8-dev libturbojpeg libturbojpeg0-dev \
    libwebp-dev libsharpyuv-dev \
    libheif-dev libheif-plugin-libde265 libde265-dev libde265-0 \
    pkg-config
```

macOS Homebrew:
```
brew install jpeg-turbo webp libheif pkg-config
```

## Format support

| Format | Status | Backend |
|---|---|---|
| BMP | byte-exact | hand-written |
| PNG | byte-exact | `image/png` (stdlib) |
| GIF | byte-exact, first frame only | `image/gif` (stdlib) — note: zeroes transparent palette RGB; we restore from raw color table |
| JPEG | byte-exact | pixiv/go-libjpeg cgo (libjpeg-turbo, JDCT_ISLOW) |
| WebP | byte-exact | chai2010/webp cgo |
| TIFF | byte-exact, baseline only (uncompressed/LZW/Deflate, 8-bit RGB+grayscale) | golang.org/x/image/tiff |
| HEIC | byte-exact, single still image | strukturag/libheif cgo |

## See also

- [USAGE.md](../../USAGE.md)
- [STATUS.md](../../STATUS.md)
- [`../../spec/SPEC.md`](../../spec/SPEC.md)

## License

BSD-2-Clause.
