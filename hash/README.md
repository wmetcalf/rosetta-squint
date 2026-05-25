# rosetta-image-hash

Byte-exact ports of the Python `imagehash` library (v4.3.2) to five other languages.

## Goal

Every language port produces the **same hex string** as Python `imagehash` for the
same input image, for the same algorithm and size. This means you can hash an image
in Java, store the hex, and compare it byte-for-byte against a hex produced by the
Python, Go, Rust, JS, or Swift port — no library involved.

## Status

| Port | Status |
|---|---|
| Python (reference) | `imagehash==4.3.2` upstream (re-exported via `rosetta_image_hash`) |
| Java | ✅ shipped (Maven, Java 17) |
| Go | ✅ shipped (Go 1.21, pure stdlib) |
| Rust | ✅ shipped (Cargo, edition 2021) |
| JS/TS | ✅ shipped (npm, Node 18+, browser via `dist/browser/`) |
| Swift | ✅ shipped (SwiftPM, Swift 5.9, Linux + macOS) |

All 10 algorithms cross-port byte-exact at `hash_size = 2, 8, 16, 32, 64` (per-algorithm exceptions documented in [`STATUS.md`](./STATUS.md)). See [`STATUS.md`](./STATUS.md) for the full per-port × per-algorithm matrix and [`USAGE.md`](./USAGE.md) for API examples.

## Algorithms

`average_hash`, `phash`, `phash_simple`, `dhash`, `dhash_vertical`, `whash_haar`,
`whash_db4`, `whash_db4_robust`, `colorhash`, `crop_resistant_hash`,
plus `hex_to_hash` / `hex_to_flathash` / `hex_to_multihash` / `old_hex_to_hash` for parity with upstream's hex round-trip helpers.

`whash_db4_robust` is a rosetta-specific addition (snap-to-threshold tie-break before median + threshold) for cross-port determinism on pathological inputs. See [`spec/SPEC.md`](./spec/SPEC.md) §whash_db4_robust.

## Layout

- `/spec/` — bit-level rules (`SPEC.md`), fixture corpus, canonical decoded buffers,
  golden hex values, Group-1 unit-test reference vectors, JSON Schema. Every port
  consumes this directory to validate parity.
- `/python/`, `/java/`, `/go/`, `/rust/`, `/js/`, `/swift/` — per-port implementations.

## Working on a port

```
git clone https://github.com/wmetcalf/rosetta-squint
cd rosetta-squint/hash/spec && pip install -r requirements.txt && python regenerate.py --check   # sanity
cd ../<your-port> && <your test runner>
```

If `regenerate.py --check` fails, the committed goldens drifted from what Python
produces. Re-run `python regenerate.py` to refresh, commit, retry.

CI runs the full per-port test matrix on every push/PR via [`../.github/workflows/hash-ci.yml`](../.github/workflows/hash-ci.yml).

## License

BSD-2-Clause, matching upstream `JohannesBuchner/imagehash`.
