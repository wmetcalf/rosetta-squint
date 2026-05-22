# rosetta-squint

> "Squint at two images: do they look the same?" The Python `imagehash` library answers that question for one language. `rosetta-squint` answers it identically in **six** — Python, Rust, Go, Java, JavaScript/TypeScript, and Swift — for the same input bytes.

## What's in this repo

| Subdirectory | What it is |
|---|---|
| **`hash/`** | Byte-exact perceptual-hash algorithms (port of Python `imagehash` 4.3.2 + extensions). Takes an already-decoded pixel buffer in. Tagged via `hash-*-v0.1.0`. |
| **`decode/`** | Byte-exact image decoders for BMP, PNG, GIF, JPEG, WebP, TIFF, HEIC. Takes bytes in, produces an RGB/RGBA buffer out. Tagged via `decode-*-v0.1.0`. |

The two halves are independently useful — `hash/` works fine if you've already decoded your images via your language's native libs (matching the original `imagehash` + `Pillow` model) — but together they deliver something neither half can on its own:

> **Hash a file in Rust, hash the same bytes in Java, get the identical hex string.**

That cross-language byte-exact-from-source-bytes guarantee is what makes the two projects live in one repo.

## What you actually want to call

```python
# Python
import rosetta_squint as rs
print(rs.phash("photo.jpg"))                # "c3f8a1b27d0e4f96"
```

```rust
// Rust
let h = rosetta_squint::phash("photo.jpg", 8)?;
println!("{}", h);                          // "c3f8a1b27d0e4f96"
```

```go
// Go
h, _ := rosettasquint.PHash("photo.jpg", 8)
fmt.Println(h.ToHex())                      // "c3f8a1b27d0e4f96"
```

```java
// Java
ImageHash h = RosettaSquint.phash("photo.jpg", 8);
System.out.println(h);                      // "c3f8a1b27d0e4f96"
```

```ts
// JS / TS
const h = await rosettaSquint.phash("photo.jpg", 8);
console.log(h.toString());                  // "c3f8a1b27d0e4f96"
```

```swift
// Swift
let h = try RosettaSquint.phash(at: "photo.jpg", hashSize: 8)
print(h)                                    // "c3f8a1b27d0e4f96"
```

**Same hex string in all six.** This is delivered by chaining `decode/` (byte-exact decode → RGB) and `hash/` (byte-exact hash) under one entry point per language. See [`hash/USAGE.md`](./hash/USAGE.md) and [`decode/USAGE.md`](./decode/USAGE.md) for the layered APIs underneath.

## Why is this hard

Look at what each language ships as its default JPEG decoder:

| Lang | Default decoder | Notes |
|---|---|---|
| Python | `Pillow` (libjpeg-turbo) | the upstream reference |
| Rust | `image` crate (jpeg-decoder) | pure Rust, different IDCT |
| Go | `image/jpeg` stdlib | pure Go, different IDCT |
| Java | `javax.imageio` | pure Java |
| JS | `jpeg-js` / `sharp` / etc. | varies |
| Swift | `CoreImage` / pure decoders | platform-dependent |

Each of these decodes the **same JPEG file** into a *slightly different* RGB byte buffer due to IDCT precision, chroma upsampling rounding, and color-space conversion order. Drop those buffers into a perceptual hash and you get **different hex strings in different languages** for the same input. Storing hashes from Python and looking them up from Go silently breaks.

`decode/` solves this by linking every port to the **same C libraries** (libjpeg-turbo, libwebp, libtiff, libheif) via FFI — or, for formats where the native implementation is already byte-exact (BMP, PNG via libpng-equivalent), validating that against frozen goldens. `hash/` solves the other half: every port implements `phash`/`dhash`/`whash`/`colorhash`/`crop_resistant_hash` byte-exact against Python `imagehash` 4.3.2 goldens.

The combination, exposed as a single top-level call per language, is `rosetta-squint`.

## History

This repo was merged from two previously-split sibling projects on 2026-05-22:

- `rosetta-image-hash` → `hash/` (153 commits, 9 tags renamed with `hash-` prefix)
- `rosetta-image-decode` → `decode/` (117 commits, 10 tags renamed with `decode-` prefix)

Full git history preserved via `git-filter-repo` + `git merge --allow-unrelated-histories`. No `git push` has happened yet — the repo is local.

## Status

Both halves are at clean release-candidate states. See:
- [`hash/STATUS.md`](./hash/STATUS.md) — 6 ports, ~1500 tests, byte-exact across 8 algorithms
- [`decode/STATUS.md`](./decode/STATUS.md) — 5 ports, ~212 tests, byte-exact across 7 formats
- [`hash/SECURITY.md`](./hash/SECURITY.md) + [`decode/SECURITY.md`](./decode/SECURITY.md)

## Performance

End-to-end CLI cost per hash (process startup + decode + algorithm + print), measured on a 384×512 RGB photograph (`hash/spec/fixtures/peppers.png`), median of 10 iterations on Linux x86-64. Reproducible via `make bench` (per-algorithm details in [`tools/bench/`](./tools/bench/)).

| Algorithm | Rust | Swift | Go | Python | JS (Node) | Java |
|---|---:|---:|---:|---:|---:|---:|
| `phash` @ 8 | **9.8 ms** | 21.6 ms | 28.2 ms | 274 ms | 146 ms | 188 ms |
| `dhash` @ 8 | **8.4 ms** | 19.3 ms | 27.5 ms | 157 ms | 133 ms | 157 ms |
| `average_hash` @ 8 | **9.3 ms** | 20.9 ms | 25.2 ms | 154 ms | 151 ms | 173 ms |
| `colorhash` @ 3 | **9.9 ms** | 22.7 ms | 29.5 ms | 175 ms | 163 ms | 183 ms |
| `whash_haar` @ 8 | **22.4 ms** | 39.9 ms | 42.8 ms | 173 ms | 224 ms | 210 ms |
| `whash_db4` @ 8 | **21.9 ms** | 47.2 ms | 56.4 ms | 148 ms | 245 ms | 203 ms |
| `crop_resistant_hash` | **23.8 ms** | 74.5 ms | 58.1 ms | 300 ms | 371 ms | 243 ms |

**The numbers are end-to-end CLI invocation costs.** For one-shot use (e.g. a CI script that hashes one screenshot per build), this is what you'd actually pay. For workloads that amortise startup — long-running services, batch processing thousands of images — the JIT/VM ports (Python, JS, Java) end up dramatically faster than the table suggests, because most of their cost is paid once at process launch (Python imports `numpy`/`scipy`/`PIL`/`PyWavelets`; Java boots the JVM; Node initialises the mozjpeg WASM module).

For steady-state per-hash latency, use each language's native bench harness — `cargo bench`, `go test -bench`, JMH, `vitest bench`, XCTest `measure`, `pytest-benchmark`.

**Three takeaways:**

1. **Rust wins every algorithm.** No surprise — native + libjpeg-turbo via mozjpeg-sys, no managed-runtime overhead, in-tree C shim for the JPEG decode path (added after a fuzz finding).
2. **Native ports cluster.** Swift, Go, and Rust are within 2-3× of each other across algorithms. The wavelet algorithms (`whash_haar`, `whash_db4`, `crop_resistant_hash`) widen the gap somewhat — those benefit more from Rust's loop optimisations.
3. **Managed ports are startup-bound.** Python's `~150 ms` minimum is dominated by importing `numpy + scipy + PyWavelets + Pillow + imagehash`. Java's `~150 ms` is JVM cold-start. JS's `~130 ms` is Node bootstrap + WASM compile. The actual hash computation in each is fast; the CLI invocation model just pays it on every call.

Choose your port by deployment model, not algorithm speed:
- **CI step or one-off CLI invocation** → native (Rust / Go / Swift) saves real wall time.
- **Long-running service** → any port performs adequately; pick by ecosystem fit.
- **Browser** → JS (the Node CLI startup cost doesn't apply; WASM loads once per page).

## Layout

```
rosetta-squint/
├── hash/                           # rosetta-image-hash, now under hash/
│   ├── python/rosetta_imagehash/
│   ├── rust/rosetta-image-hash/
│   ├── go/imagehash/
│   ├── java/
│   ├── js/rosetta-image-hash/
│   ├── swift/RosettaImageHash/
│   └── spec/
├── decode/                         # rosetta-image-decode, now under decode/
│   ├── rust/rosetta-image-decode/
│   ├── go/imagedecode/
│   ├── java/rosetta-image-decode/
│   ├── js/rosetta-image-decode/
│   ├── swift/RosettaImageDecode/
│   ├── tools/cross-port-diff/
│   └── spec/
└── README.md                       # this file
```

## License

BSD-2-Clause throughout.
