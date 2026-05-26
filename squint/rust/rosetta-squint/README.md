# rosetta-squint

Cross-language byte-exact perceptual image hashing — decode + hash in one call. Umbrella crate combining [`rosetta-squint-decode`](https://crates.io/crates/rosetta-squint-decode) (PIL-compatible image decoders) + [`rosetta-squint-hash`](https://crates.io/crates/rosetta-squint-hash) (perceptual-hash algorithms), exposing a single top-level `rosetta_squint::phash(path)` etc. API that produces the **same hex string** as the Python, Go, Java, JS/TS, and Swift ports of [rosetta-squint](https://github.com/wmetcalf/rosetta-squint) for the same input bytes.

## Quick start

```rust
use rosetta_squint::phash;

fn main() -> Result<(), Box<dyn std::error::Error>> {
    let h = phash("photo.jpg", 8)?;
    println!("{}", h);  // "c3f8a1b27d0e4f96" — same hex in every language
    Ok(())
}
```

`Cargo.toml`:
```toml
[dependencies]
rosetta-squint = "1"
```

See [the project repository](https://github.com/wmetcalf/rosetta-squint) for the full cross-language API table, supported formats (BMP, PNG, GIF, JPEG, WebP, TIFF, HEIC), supported algorithms (`average_hash`, `phash`, `phash_simple`, `dhash`, `dhash_vertical`, `whash_haar`, `whash_db4`, `whash_db4_robust`, `colorhash`, `crop_resistant_hash`), and security/CVE posture.

## License

BSD-2-Clause, matching upstream `JohannesBuchner/imagehash`.
