# rosetta-image-decode (Rust)

Byte-exact PIL-compatible image decoder library, Rust port.

## Build + test

    cargo test

Tests resolve fixtures and goldens from `../../spec/` (relative to this crate).

## v1 Formats

- BMP (Tier 1+2+3 minus BI_JPEG/BI_PNG)
