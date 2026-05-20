# rosetta-image-hash — Go port

Byte-exact port of Python `imagehash==4.3.2` algorithms to Go 1.22.

## Build + test

```
cd ~/rosetta-image-hash/go/imagehash
go test ./...
```

Tests resolve fixtures and goldens from `../../spec/`. Run `go test` from this directory so the relative path holds.

## v1 algorithms

`AverageHash`, `DHash`, `PHash`, `WHashHaar`, `ColorHash`, plus `HexToHash` and `HexToFlathash`. All take `image.Image` (any concrete type — NRGBA, RGBA, Paletted, Gray, etc.) and return `(Hash, error)`. Non-`*image.NRGBA` inputs are normalized internally via `image/draw` (composited on opaque black, matching PIL `convert('RGB')`).

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

Standard library only.

## License

BSD-2-Clause.
