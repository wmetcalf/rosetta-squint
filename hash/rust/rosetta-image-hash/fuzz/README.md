# rosetta-image-hash fuzz targets

This directory contains [cargo-fuzz](https://rust-fuzz.github.io/book/cargo-fuzz.html)
targets for the hash-side hex parsers.

Requires nightly Rust for libFuzzer:

```sh
cargo +nightly fuzz <subcommand> ...
```

## Targets

### `hex_to_hash`

Fuzzes `hex_to_hash(&str)` with arbitrary UTF-8 strings. The function must
return `Ok(_)` for square-shaped lowercase-hex input and `Err(InvalidHex)`
otherwise — never panic, never OOM from unchecked sqrt-based shape arithmetic.

```sh
cargo +nightly fuzz run hex_to_hash
```

### `hex_to_flathash`

Fuzzes `hex_to_flathash(&str, hash_size)` with random `(hex, hash_size)` pairs.
The first byte of the fuzzer input becomes `hash_size` (covering 0..=255),
remainder becomes the hex. Exercises the `14 * secondAxis` overflow path,
length-mismatch handling, and the recently-added bit-length check.

```sh
cargo +nightly fuzz run hex_to_flathash
```

### `hex_to_multihash`

Fuzzes `hex_to_multihash(&str)` with arbitrary comma-separated-or-not strings.
Catches empty-segment, mixed-valid-invalid, and trailing-comma edge cases
that the spec corpus doesn't explicitly cover.

```sh
cargo +nightly fuzz run hex_to_multihash
```

## Property under test

Same as `decode/rust/rosetta-image-decode/fuzz`: parsers must NEVER panic on
arbitrary input. Invalid input must always surface as a typed `ImageHashError`,
not a panic or OOM.

## Corpus

Cargo-fuzz starts from an empty corpus. The fuzzer discovers interesting
inputs via coverage feedback. For reproducible failures, persist findings
under `fuzz/corpus/<target>/`.
