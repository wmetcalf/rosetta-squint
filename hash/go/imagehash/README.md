# rosetta-image-hash — Go port

Byte-exact port of Python `imagehash==4.3.2` algorithms to Go 1.22+.

The hex string produced here equals the hex Python `imagehash` produces for the same image, algorithm, and `hashSize`.

## Quick start

```go
package main

import (
    "fmt"
    "image"
    _ "image/png"
    "os"

    "github.com/wmetcalf/rosetta-squint/hash/go/imagehash"
)

func main() {
    f, _ := os.Open("photo.png")
    defer f.Close()
    img, _, _ := image.Decode(f)

    h, err := imagehash.PHash(img, 8)
    if err != nil { panic(err) }
    fmt.Println(h.ToHex())                            // "c3f8a1b27d0e4f96"

    // Hamming distance
    other, _ := imagehash.PHash(otherImage, 8)
    distance, _ := h.Subtract(other)
    _ = distance

    // Round-trip from stored hex
    restored, _ := imagehash.HexToHash(h.ToHex())
    fmt.Println(restored.Equals(h))                   // true
}
```

Input is `image.Image` from the stdlib. Any concrete type works (`*image.NRGBA`, `*image.RGBA`, `*image.Paletted`, `*image.Gray`, ...) — non-RGB inputs are normalized internally via `image/draw`, composited on opaque black, matching PIL `convert('RGB')`.

## Build + test

```
cd go/imagehash
go test ./...               # 53 tests, all passing on Linux x86-64
```

Tests resolve fixtures and goldens from `../../spec/`. Run `go test` from this directory.

## API

| Function | Signature |
|---|---|
| `AverageHash` | `(img image.Image, hashSize int) (Hash, error)` |
| `DHash` | `(img image.Image, hashSize int) (Hash, error)` |
| `PHash` | `(img image.Image, hashSize int) (Hash, error)` |
| `PHashWithFactor` | `(img image.Image, hashSize, highfreqFactor int) (Hash, error)` |
| `WHashHaar` | `(img image.Image, hashSize int) (Hash, error)` — `hashSize` must be power of 2 |
| `ColorHash` | `(img image.Image, binbits int) (Hash, error)` |
| `ColorhashBinEncode` | `(v, binbits int) []bool` |
| `HexToHash` | `(hex string) (Hash, error)` |
| `HexToFlathash` | `(hex string, hashSize int) (Hash, error)` |

`Hash` is a struct with methods `ToHex() string`, `Subtract(other Hash) (int, error)`, `Equals(other Hash) bool`. Construct directly via `NewHash(bits [][]bool) (Hash, error)` if needed.

## Test groups

| Group | Purpose |
|---|---|
| 1 | Per-kernel unit tests in `internal/*` packages against `../../spec/*_cases.json` and `lanczos_cases/*.bin`. |
| 2 | Each algorithm × fixture × size from `goldens.json`, using pre-decoded RGB buffers from `decoded/*.rgb.bin`. |
| 3 | Same as Group 2 but loads PNG via `image/png.Decode` (end-to-end). Decoder exemptions documented in `DECODER_NOTES.md`. |
| 4 | Hex round-trip on every Group-2 hash. |
| 5 | Hamming distance + error semantics. |

## Parity guarantee

Every test in Groups 1–4 asserts byte-exact equality with Python `imagehash 4.3.2`. Any Group-3 failure that passes Group 2 is a PNG decoder discrepancy; see `DECODER_NOTES.md` for documented exemptions.

## Dependencies

Standard library only. No `go get` needed beyond what's already in `go.mod`.

## See also

- [USAGE.md](../../USAGE.md) — examples for all 6 ports
- [STATUS.md](../../STATUS.md)
- [`../../spec/SPEC.md`](../../spec/SPEC.md)

## License

BSD-2-Clause.
