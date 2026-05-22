# rosetta-image-decode — Rust port

Byte-exact PIL-compatible image decoder library, Rust port (1.70+, edition 2021).

Decodes BMP, PNG, GIF (first frame), JPEG, WebP, TIFF, and HEIC to a raw RGB or RGBA byte buffer matching what `PIL.Image.open(...).tobytes()` produces.

## Quick start

```rust
use std::fs;
use rosetta_image_decode::{decode, detect_format, Channels, DecodeErrorKind};

let bytes = fs::read("photo.jpg")?;

if let Some(fmt) = detect_format(&bytes) {
    println!("detected: {:?}", fmt);
}

match decode(&bytes) {
    Ok(img) => {
        println!("{}x{} channels={:?} format={:?}",
            img.width, img.height, img.channels, img.format);
        // img.data is Vec<u8>, len = width * height * (3 if RGB, 4 if RGBA)
    }
    Err(e) => eprintln!("decode failed: {:?} ({})", e.kind, e.detail),
}
```

## Build + test

```
cargo build
cargo test --no-fail-fast              # 42 tests, all passing on Linux x86-64
```

Tests resolve fixtures and goldens from `../../spec/`. Run from this crate directory.

## API

| Symbol | Signature |
|---|---|
| `decode` | `fn decode(bytes: &[u8]) -> Result<DecodedImage, DecodeError>` |
| `detect_format` | `fn detect_format(bytes: &[u8]) -> Option<Format>` |
| `supported_formats` | `fn supported_formats() -> Vec<Format>` |

```rust
pub struct DecodedImage {
    pub width: usize,
    pub height: usize,
    pub data: Vec<u8>,          // row-major, length = width * height * (3 or 4)
    pub channels: Channels,
    pub format: Format,
}

pub enum Channels { Rgb, Rgba }
pub enum Format   { Bmp, Png, Gif, Jpeg, Webp, Tiff, Heic }

pub enum DecodeErrorKind {
    UnsupportedFormat,
    CorruptInput,
    Truncated,
    UnsupportedFeature,
}

pub struct DecodeError {
    pub kind: DecodeErrorKind,
    pub format: Option<Format>,
    pub detail: String,
}
```

## Dependencies

Crate dependencies (all pinned in `Cargo.toml`):

- `image = "0.25"` (features: `png`, `gif`, `tiff`) — pure-Rust PNG/GIF/TIFF
- `mozjpeg-sys = "2"` — compiles libjpeg-turbo from source. **No system libjpeg needed.**
- `libwebp-sys2 = "0.1.11"` (feature `0_5`) — compiles libwebp from source. **No system libwebp needed.**
- `libheif-rs = "2.7"` (feature `v1_17`) — FFI to **system libheif**, pinned to 1.17 ABI
- `thiserror = "1"`

System packages required for HEIC support on Ubuntu:

```
sudo apt install libheif-dev libheif-plugin-libde265 libde265-dev libde265-0 pkg-config
```

(macOS Homebrew: `brew install libheif`.)

The first build compiles libjpeg-turbo and libwebp from source — expect ~2 minutes the first time.

## Cargo dependency

Not on crates.io yet. Use a path dependency:

```toml
[dependencies]
rosetta-image-decode = { path = "../rosetta-image-decode/rust/rosetta-image-decode" }
```

## Format support

| Format | Status | Decoder backend |
|---|---|---|
| BMP | byte-exact | hand-written (Tier 1+2+3 minus BI_JPEG/BI_PNG) |
| PNG | byte-exact | `image` crate (png feature) |
| GIF | byte-exact, first frame only | `image` crate (gif feature) |
| JPEG | byte-exact | mozjpeg-sys (libjpeg-turbo, JDCT_ISLOW) |
| WebP | byte-exact | libwebp-sys2 |
| TIFF | byte-exact, baseline only (uncompressed/LZW/Deflate, 8-bit RGB+grayscale) | `image` crate (tiff feature) |
| HEIC | byte-exact, single still image only | libheif-rs / system libheif 1.17 |

## See also

- [USAGE.md](../../USAGE.md) — examples for all 5 ports + end-to-end hash example
- [STATUS.md](../../STATUS.md) — install matrix, Python parity story, gaps
- [`../../spec/SPEC.md`](../../spec/SPEC.md) — bit-level specification

## License

BSD-2-Clause.
