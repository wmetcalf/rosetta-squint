# rosetta-image-hash — Rust port

Byte-exact port of Python `imagehash==4.3.2` algorithms to Rust 1.83+ (edition 2021).

The hex string produced here equals the hex Python `imagehash` produces for the same image, algorithm, and `hash_size`.

## Quick start

```rust
use image::ImageReader;
use rosetta_image_hash::{phash, hex_to_hash};

let img = ImageReader::open("photo.png")?.decode()?;
let h = phash(&img, 8)?;
println!("{}", h.to_hex());                      // "c3f8a1b27d0e4f96"

// Hamming distance against another hash
let other = phash(&ImageReader::open("other.png")?.decode()?, 8)?;
let distance = h.subtract(&other)?;

// Round-trip from a stored hex
let restored = hex_to_hash(&h.to_hex())?;
assert_eq!(restored.to_hex(), h.to_hex());
```

## Build + test

```
cargo build
cargo test                  # 31 tests, all passing on Linux x86-64
```

Tests resolve fixtures and goldens from `../../spec/`. Run `cargo` from this directory so the relative path holds.

## API

All algorithms take `&image::DynamicImage` and return `Result<Hash, ImageHashError>`:

| Function | Signature |
|---|---|
| `average_hash` | `(img, hash_size: usize) -> Result<Hash, _>` |
| `dhash` | `(img, hash_size: usize) -> Result<Hash, _>` |
| `phash` | `(img, hash_size: usize) -> Result<Hash, _>` |
| `phash_with_factor` | `(img, hash_size: usize, highfreq_factor: usize) -> Result<Hash, _>` |
| `whash_haar` | `(img, hash_size: usize) -> Result<Hash, _>` — `hash_size` must be power of 2 |
| `colorhash` | `(img, binbits: usize) -> Result<Hash, _>` |
| `colorhash_bin_encode` | `(v: usize, binbits: usize) -> Vec<bool>` |
| `hex_to_hash` | `(hex: &str) -> Result<Hash, _>` — restores square hashes |
| `hex_to_flathash` | `(hex: &str, hash_size: usize) -> Result<Hash, _>` — restores `colorhash`-shaped hashes |

`Hash` exposes `to_hex() -> String`, `subtract(&Hash) -> Result<usize, _>` (Hamming distance), and standard `Debug`/`PartialEq`/`Eq`.

`ImageHashError` is a `thiserror`-derived enum with variants like `InvalidHashSize`, `InvalidBinbits`, `NonPowerOfTwo`, `InvalidHex`, `ShapeMismatch`.

## Cargo dependency

Not on crates.io yet. Use a path dependency for now:

```toml
[dependencies]
rosetta-image-hash = { path = "../rosetta-image-hash/rust/rosetta-image-hash" }
image = { version = "0.25", default-features = false, features = ["png"] }
```

Crate runtime deps: `image` 0.25 (PNG only), `thiserror` 2.

## See also

- [USAGE.md](../../USAGE.md) — examples for all 5 ports
- [STATUS.md](../../STATUS.md) — what's implemented, Python parity story
- [`../../spec/SPEC.md`](../../spec/SPEC.md) — bit-level pipeline specification

## License

BSD-2-Clause.
