# rosetta-image-decode (Go)

Byte-exact PIL-compatible image decoder library, Go port.

## Build + test

    go test ./...

Tests resolve fixtures and goldens from `../../spec/` (relative to this module).
Run `go test` from this module root.

## v1 Formats

- BMP (Tier 1+2+3 minus BI_JPEG/BI_PNG)
