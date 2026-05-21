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

**Resolution:** The HEIC goldens in `spec/decoded/heic/valid/` were regenerated using
libheif-js 1.17.1 output as the reference. All other format goldens (BMP, PNG, GIF,
JPEG, WebP, TIFF) remain anchored to system PIL/Pillow output.
