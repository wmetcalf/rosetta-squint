# Decoder Notes

## HEIC — libheif-js 1.17.1 vs system libheif 1.17.6

libheif-js@1.17.1 bundles libheif as a WASM binary (with libde265 for HEVC decoding).
The original goldens in `spec/decoded/heic/` were generated against system libheif 1.17.6
via `pillow-heif`. Comparing the two revealed ±1–2 byte-level pixel differences across
5 of 10 valid fixtures (all lossy-compressed files):

| Fixture | Differing bytes | Max delta |
|---|---|---|
| 32x32-rgba.heic | 196 | ±2 |
| 32x32-q50.heic | 140 | ±2 |
| 64x64-q90.heic | 144 | ±1 |
| larger-128x96.heic | 740 | ±2 |
| photo-96.heic | 175 | ±2 |

These differences arise from rounding divergences in the DCT/YCbCr→RGB conversion
path between the bundled WASM libde265/libheif and the system-compiled 1.17.6 build.
The lossless and small (16x16) fixtures matched exactly.

**Resolution:** HEIC goldens stay anchored to system libheif 1.17.6 (so Rust/Go/Java/Swift
ports linking to system libheif remain byte-exact). The JS port's Group 2 HEIC test uses a
±2 per-pixel tolerance instead of byte-exact comparison. This is the only port-specific
relaxation in the project — all other ports and formats are byte-exact.
