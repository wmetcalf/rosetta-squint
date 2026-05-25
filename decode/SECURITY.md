# Security

Image decoders are a notorious CVE hotspot. This document covers the security posture of `rosetta-image-decode`, known exposures, and what to do about them.

## Threat model

The library decodes **untrusted byte streams** from arbitrary sources into raw RGB/RGBA buffers. Hostile inputs are the default assumption. Specific concerns:

1. **Decompression bombs** — small files claiming huge dimensions that drive the underlying decoder to allocate gigabytes.
2. **Memory corruption in C libraries** — libjpeg-turbo, libwebp, libtiff, libheif have all shipped buffer-overflow CVEs.
3. **Integer overflow in pixel buffer sizing** — `width * height * channels` can wrap in 32-bit arithmetic.
4. **Resource exhaustion** — pathologically constructed files that exercise worst-case codepaths.

## Defenses in this library

### `MAX_PIXELS` cap (256 MP)

Every port checks header-declared `width × height` against `MAX_PIXELS = 268_435_456` (256 × 1024 × 1024 = ~256 mega-pixels) before invoking the underlying decoder. Inputs exceeding the cap throw `imageTooLarge`. See `spec/SPEC.md` §3.1.

For comparison: PIL's default `Image.MAX_IMAGE_PIXELS` is ~89.5 MP. We allow more headroom (large legitimate photos exist) while still bounding worst-case allocation to ~1 GB (RGBA, before any downstream processing).

The cap is intentionally not configurable in v1 — a single hardcoded constant per port keeps cross-port behavior identical.

### Overflow-safe size arithmetic

`width × height × channels` is computed as 64-bit (Rust `usize`, Java `Math.multiplyExact(long, long)`, Go `int64`, Swift `Int` which is 64-bit on supported platforms, JS `Number` ≤ 2^53). No port relies on 32-bit native int multiplication that could silently wrap.

### Strict ftyp brand whitelist for HEIC

HEIC detection accepts only `{heic, heix, mif1, msf1, hevc, hevx}`. Multi-image/sequence brands (`heim`, `heis`, `hevm`, `hevs`) and `avif` are rejected as `unsupportedFormat`. v1 only supports single still images.

### Format detection before allocation

`detectFormat()` runs entirely on a small magic-byte prefix before any size-proportional allocation. Unrecognized files are rejected with `unsupportedFormat` before they reach a decoder.

## Known exposures

### Per-language native library versions

The Rust, Go, Java, and Swift ports all FFI into native C libraries. The version of those libraries is **not pinned by this library** — it's whatever the host system provides (or whatever the language's binding ships).

| Port | JPEG | WebP | TIFF | HEIC |
|---|---|---|---|---|
| Rust | mozjpeg-sys 2.x (vendored) | libwebp-sys2 0.1.x (vendored) | image crate 0.25 (pure Rust) | system libheif |
| Go | system libjpeg-turbo | system libwebp | golang.org/x/image/tiff (pure Go) | system libheif |
| Java | system libturbojpeg | sejda webp-imageio (bundles native libwebp) | TwelveMonkeys (pure Java) | system libheif via JNA |
| JS | mozjpeg WASM (@jsquash/jpeg) | libwebp WASM (@jsquash/webp) | utif2 (pure JS) | libheif WASM (libheif-js) |
| Swift | system libturbojpeg | system libwebp | system libtiff | system libheif |

### Minimum recommended versions

| Library | Minimum | Known CVEs fixed in or after |
|---|---|---|
| libjpeg-turbo | 2.1.5.1+ / 3.0.x | CVE-2023-2804 buffer overflow |
| libwebp | 1.3.2+ | CVE-2023-4863 heap buffer overflow (Sept 2023, exploited in the wild) |
| libtiff | 4.5.1+ | CVE-2023-3576, CVE-2023-3618 |
| libheif | 1.17.6-1ubuntu4.3+ (Ubuntu) or 1.18.1+ (other distros) | CVE-2024-25269 NULL pointer deref |
| libde265 | 1.0.12+ | CVE-2023-43887 (referenced from libheif decode path) |

**Ubuntu 24.04** ships patched versions of all of these (libheif 1.17.6 has the security patches backported as `-1ubuntu4.3`). Other distributions vary — check `apt show libheif1` / `pkg-config --modversion libheif` before deploying.

### JS port specifically

`@jsquash/jpeg`, `@jsquash/webp`, and `libheif-js` bundle their own WASM-compiled C libraries. The version pinned in `package.json` determines which library version is used. Audit `package.json` against the table above and run `npm audit` regularly.

Bundled libheif version in `libheif-js@1.17.1` diverges from system libheif 1.17.6 by ±1–2 px per pixel on lossy fixtures — this is a known parity issue, not a security one. See `js/rosetta-image-decode/DECODER_NOTES.md`.

### Java WebP — was sejda, now libwebp JNA wrapper

**Resolved** (commit 8daa21d). The Java WebP decoder previously depended on `org.sejda.imageio:webp-imageio:0.1.6` (2019), which bundled its own outdated native libwebp inside the JAR — bypassing system security patches including the CVE-2023-4863 fix. Replaced with a hand-rolled JNA wrapper in `io.rosetta.imagedecode.internal.libwebp` that calls system libwebp directly (mirrors the libheif JNA wrapper pattern).

### Go JPEG — was pixiv/go-libjpeg, now in-tree cgo wrapper

**Resolved** (commit 116d756). The Go JPEG decoder previously depended on `github.com/pixiv/go-libjpeg` (last commit August 2019, 6 years stale). For an image decoder processing untrusted input, an unmaintained binding is unacceptable regardless of how thin it is. Replaced with a hand-rolled in-tree cgo wrapper in `go/imagedecode/internal/libjpeg` that links to system libjpeg-turbo via TurboJPEG API. `TJFLAG_ACCURATEDCT` preserves byte-exact parity with PIL/JDCT_ISLOW.

### JS GIF — was omggif, now in-tree pure-TS decoder

**Resolved** (commit d2ebd53). The JS GIF decoder previously depended on `omggif` (last commit July 2019, 6 years stale). Replaced with a hand-rolled pure-TS decoder at `js/rosetta-image-decode/src/internal/gif-decoder.ts` (~612 LOC, ported from the Swift port's validated GIF89a decoder). Includes LZW decompression, 4-pass interlacing, and the transparent-palette-RGB-preservation semantics required for PIL parity.

### Go HEIC cgo — GC latency on sustained throughput

The Go HEIC decoder uses `github.com/strukturag/libheif/go/heif`, which handles C-pointer lifetime via `runtime.SetFinalizer`. The finalizer calls `heif_context_free` only when the Go GC marks the wrapping object as dead, but Go's GC has no visibility into the C-heap size held by libheif/libde265 (decoded image planes, intermediate DPB buffers, etc.).

Under sustained high-throughput HEIC decoding, the Go process's RSS can grow well past what active-Go-heap accounting suggests, because finalizers run on a delayed schedule. The library is still correct (memory is reclaimed eventually); the issue is *when*.

**Recommendation for production deployments processing > 100 HEIC/sec:**
- Call `runtime.GC()` explicitly at controlled intervals to flush pending finalizers
- Or process HEIC in a subprocess + recycle the process periodically
- Or set `GOGC` lower (default 100; try 50 or 25) to make GC more aggressive

This is a property of `cgo` + native-library binding generally, not specific to HEIC. The Go JPEG and WebP paths have the same characteristic but their per-image C-heap footprint is much smaller.

## Reporting a vulnerability

If you find a security issue, please email william.metcalf@gmail.com — do not open a public issue.

## Audit trail

This library has had one internal security review covering:
- decompression-bomb defense (resolved: MAX_PIXELS cap)
- 32-bit integer overflow on Java size arithmetic (resolved: multiplyExact)
- defensive copy semantics on `DecodedImage.data()` (Java: zero-copy access added)
- resource cleanup on error paths (verified correct in JNA HEIC wrapper)
- ftyp brand whitelist narrowness (documented as intentional in spec)

External / fresh-eyes audits — Raptor, GPT-5.4, Claude (Opus 4.7) — landed 51+
findings (HIGH / MEDIUM / LOW) resolved or deferred-with-rationale; see
[`/CHANGELOG.md`](../CHANGELOG.md) for the per-finding fix status. Raw
audit reports are kept internal.

## Fuzz coverage

Two native fuzz harnesses run the full decoder pipeline against adversarial input:

| Port | Harness | Run |
|---|---|---|
| Rust | `rust/rosetta-image-decode/fuzz/` via [cargo-fuzz](https://rust-fuzz.github.io/book/cargo-fuzz.html) | `cd rust/rosetta-image-decode && cargo +nightly fuzz run decode_any` |
| Go | `go/imagedecode/fuzz_decode_test.go` via native Go 1.18 fuzz | `cd go/imagedecode && go test -run='^$' -fuzz=FuzzDecodeAny -fuzztime=60s` |

The Rust harness produced the original `[0xFF, 0xD8]` truncated-SOI panic finding
that motivated the in-tree libjpeg C shim. The Go harness covers the parallel
Go-side parsers (cgo + native BMP/GIF) for the same property: `Decode()` never
panics, regardless of input.

JS/Java/Swift do not currently have fuzz harnesses. The cross-port-diff tests
exercise the happy paths byte-exactly, but adversarial-input fuzz coverage
remains a gap for those three ports.
