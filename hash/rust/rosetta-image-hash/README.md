# rosetta-image-hash — Rust port

Byte-exact port of Python `imagehash==4.3.2` algorithms to Rust 1.95 (edition 2021).

## Build + test

```
cd ~/rosetta-image-hash/rust/rosetta-image-hash
cargo test
```

Tests resolve fixtures and goldens from `../../spec/`. Run `cargo` from this directory so the relative path holds.

## v1 algorithms

`average_hash`, `dhash`, `phash`, `whash_haar`, `colorhash`, plus `hex_to_hash` and `hex_to_flathash`. All take `&image::DynamicImage` and return `Result<Hash, ImageHashError>`.

## Dependencies

- Runtime: `image` (PNG decoder only, no JPEG/GIF/TIFF), `thiserror` (error enum derive)
- Dev-only: `serde`, `serde_json` (test code only)

## License

BSD-2-Clause.
