# Changelog

All notable changes to rosetta-squint go here. Format: [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased] — pre-1.0 hardening pass

This release rolls up the work from three external/fresh-eyes security audits
(Raptor, GPT-5.4, Claude Opus 4.7) on the merged `hash/` + `decode/` + `squint/`
codebase. The raw audit reports are kept internal; this changelog lists the
per-finding fix status.

### Breaking changes

The hardening surfaced four contract violations whose fixes change public-API
shape. Callers consuming any of the affected ports as a library should plan a
migration:

- **Swift `ImageMultiHash`** lost `Hashable` conformance. The previous
  conformance was unsound — `==` calls similarity-threshold `matches()` which
  is non-reflexive/non-transitive/non-symmetric, so `a == b` did not imply
  `a.hashValue == b.hashValue`. Storing an `ImageMultiHash` in a `Set` or as a
  `Dictionary` key produced undefined behavior. `Hashable` is gone; use
  `segmentHashes` element-wise comparison for true identity, `matches()` for
  similarity.
- **Swift `ImageMultiHash.init(segmentHashes:)`** now `throws` —
  `ImageHashError.shapeMismatch` is raised on empty input. The previous
  behavior trapped via `precondition`. Callers must add `try`.
- **Swift `ImageMultiHash.bestMatch(_:)`** now `throws` on empty input
  (previously trapped via `precondition`).
- **Go `BestMatch(others []ImageMultiHash)`** now returns
  `(ImageMultiHash, error)` instead of `ImageMultiHash`. Empty `others`
  produces a typed error instead of a runtime panic.
- **JS `new ImageMultiHash(segmentHashes)`** throws `ImageHashError("ShapeMismatch")`
  on empty input. Previously the empty case slipped through and downstream
  `.bitCount()` access threw a less-helpful `TypeError`.
- **Java `ImageMultiHash.equals` / `hashCode`** Javadoc now documents the
  non-symmetric similarity-based equality and warns against use as a
  `HashMap`/`HashSet` key. No behavioral change — the contract violation
  always existed; the docs now call it out.

### Cross-port behavior changes (deliberate spec changes)

- **`SNAP_EPS = 1e-10`** snap-to-threshold tie-break added to `phash`,
  `phash_simple`, `whash_db4`, and `whash_db4_robust` across all 6 ports.
  The per-bit comparison is now `v > threshold + SNAP_EPS` (deterministic
  bit 0 on tie) rather than strict `v > threshold`. Eliminates
  cross-port FP-noise divergence at large hash sizes (32, 64). Hashes
  computed by upstream `imagehash` for inputs with near-median DCT
  coefficients may differ by ≤ 1 bit from rosetta_squint_hash hashes; the
  cross-port goldens are the new canonical reference. See SPEC.md
  §"Threshold tie-break" for the full rationale.

### Security hardening

#### Decompression-bomb defense

- **`MAX_PIXELS = 256MP`** guard now runs BEFORE the underlying decoder
  allocates the raster, in 7 port × format combos that previously had the
  check after decode: Rust GIF/TIFF, Java GIF/TIFF, JS JPEG/WebP/HEIC.
  Plus Rust/Go/Swift HEIC + Rust/Go/Swift WebP got byte-level header
  sniffers as the native libraries' internal limits (libwebp's 4 G area
  cap, libheif's 32768 per-side cap) would otherwise pre-empt the check
  with `corruptInput`. All 5 missing `too-large.*` fixtures (GIF/HEIC/
  JPEG/TIFF/WebP) ship as Group-4 regression tests.

#### File-input hardening (squint path APIs)

- **`MAX_FILE_SIZE = 256 MiB`** cap added to all 6 squint ports' `decodeFile`
  entry points. Reads larger than the cap are rejected before the bytes hit
  RAM.
- **Non-regular file rejection**: `/dev/zero`, FIFOs, sockets, and block
  devices are now rejected before any read attempt (`S_ISREG`/`isRegularFile`/
  `is_file()`/`isFile()`/`typeRegular`/`IsRegular()` per port). Previously,
  these would either block forever (FIFO without writer) or stream infinite
  bytes into memory (`/dev/zero` until OOM-kill), defeating the size cap
  entirely.

#### FFI bridge hardening

- **Python HEIC**: `libheif`-returning-`struct heif_error`-by-value functions
  now use the proper `HeifError` ctypes Structure instead of `ctypes.c_int64`,
  with `_check_heif()` raising `RuntimeError` on `err.code != 0`. Previously
  the error code, subcode, and message pointer were silently dropped.
- **Python HEIC handle leak window**: `_check_heif` now runs INSIDE the
  `try`/`finally` so a failure after the C call populates the out-pointer
  still releases the handle.
- **Python HEIC stride trust**: libheif-reported `stride` is now validated
  against `width * channels` and `width <= 0` / `height <= 0` are rejected
  before the row-copy loop.
- **JS HEIC**: explicit `decoder.free()` + `img.free()` cleanup in `try/finally`
  instead of waiting on Emscripten GC. Pinned `libheif-js` to exact `1.17.1`
  (was `^1.17.1`) — the private `$$.ptr` access path is version-fragile.
  Defensive `try/catch` around the `$$.ptr` read falls back to
  `hasAlpha = false` if the Emscripten internal layout changes.
- **Java `JPEGDecoder`**: narrowed `catch (Exception e)` to `catch (IOException e)`
  so RuntimeExceptions propagate instead of being silently mapped to
  `corruptInput`.
- **Rust WebP**: returned `width`/`height` from `WebPDecodeRGBA`/`WebPDecodeRGB`
  are now validated against the `WebPGetFeatures` probe before constructing
  the slice — catches an inconsistent libwebp buffer that would otherwise
  produce an OOB read.
- **Rust/Go/Swift HEIC**: post-decode plane dimensions are now validated
  against the handle dimensions before iterating, catching a corrupt input
  that yields a smaller-than-handle plane.

#### Algorithm correctness

- **`crop_resistant_hash` rounding**: Rust, Java, Swift now use banker's
  rounding (round-half-to-even) for segment-bounding-box coordinate scaling,
  matching Python/Go/JS. Previously the three diverged at `.5` ambiguity
  points (e.g., 150×150 inputs produced 3-way port divergence). Spec §3.4
  explicitly mandates banker's rounding.
- **Java `BitPack.unpackFlat`** now validates `hex.length() * 4 >= 14 * secondAxis`
  before parsing. Previously a truncated colorhash hex silently zero-extended
  via `BigInteger.testBit` returning `false` for indices past the bit length.
- **Swift `findAllSegments`** breaks when `findRegionExact` returns empty —
  defense-in-depth against an otherwise-unreachable infinite loop on
  pathological inputs.
- **Rust `f64::total_cmp`** replaces `partial_cmp(b).unwrap()` in `phash`
  and `whash` sorts. Previously NaN coefficients (theoretically reachable
  via crafted malformed pixel buffers) panicked.
- **Swift `hexToBits`** now rejects uppercase hex characters; spec mandates
  lowercase. Other 4 ports already rejected.

#### Buffer-shape validation

- **JS `RgbImage` / Swift `RGBImage`** now have a `validateRgbImage()` /
  `RGBImage.validate()` helper called at every hash function entrypoint.
  Catches `data.length != width * height * channels` mismatches at the API
  boundary instead of silently corrupting hashes (JS) or trapping (Swift).

#### BMP

- **`biClrUsed` clamp**: Java, Go, JS, Swift BMP decoders now clamp
  attacker-controlled `biClrUsed` to the bit-depth maximum (`2`, `16`, `256`
  for 1/4/8-bit). Previously a hostile BMP with `biClrUsed = 0x40000000`
  could request a 12 GB palette allocation.

#### GIF

- **JS / Swift per-frame dimension check**: in-tree GIF decoders now enforce
  `MAX_PIXELS` against frame width/height (not just canvas), and reject
  frames that extend beyond the canvas. Previously a hostile GIF image
  descriptor declaring 65535×65535 within a 16×16 canvas could drive a
  4 GB LZW output allocation.

#### Swift TIFF

- **TIFFClientOpen in-memory bridge**: Swift TIFF decoder now reads from a
  `[UInt8]` buffer via `TIFFClientOpen` instead of writing the input to
  `/tmp/tiff-<uuid>.tif`. Removes the disk-IO requirement (works under
  sandboxing/read-only-fs), eliminates the temp-file leak on SIGKILL, and
  matches the in-memory pattern of the other 4 ports.

#### Go JPEG

- **CMYK SOF marker coverage**: the pre-decode 4-component check now
  covers all 13 SOF marker variants (was 5). A lossless or arithmetic
  4-component JPEG no longer bypasses the rosetta-side CMYK check.

### CI / coverage

- **Top-level `squint-ci.yml`** workflow runs per-port squint tests,
  cross-squint-diff (regression mode), MAX_FILE_SIZE smoke tests against a
  300 MiB sparse file and `/dev/zero`, and dep-audit for the squint Python /
  Rust / JS / Go manifests with a weekly cron.
- **Per-half `ci.yml` dep-audit**: `cargo audit`, `npm audit --audit-level=high`,
  `pip-audit`, and `govulncheck` (Go) on the hash and decode lockfiles, also
  with a weekly cron.
- **Go fuzz harness** at `decode/go/imagedecode/fuzz_decode_test.go` —
  `FuzzDecodeAny` and `FuzzDecodeWithPrefix` mirror the Rust cargo-fuzz
  targets. Run: `go test -run='^$' -fuzz=FuzzDecodeAny -fuzztime=60s`.
- **Rust hash-side fuzz harness** at `hash/rust/rosetta-squint-hash/fuzz/`
  with three targets: `hex_to_hash`, `hex_to_flathash`, `hex_to_multihash`.
- **Python squint HEIC tests** in `squint/python/tests/test_squint.py`:
  7 new tests cover the ctypes-libheif bridge (happy path, RGBA path,
  lossless, malformed-input rejection, AVIF rejection).
- **Cross-squint-diff grid** extended with `hash_size = 2` for all algorithms
  — 8 new (algo × size) combinations × 2 fixtures = 16 new cross-port
  byte-exact checks. Total: 35 (algo × size) combinations × 2 fixtures = 70
  byte-exact verifications across 6 ports.
- **Hash boundary goldens**: `hash_size = 2` added to all algorithms. Sizes
  32 + 64 deferred for `phash`, `phash_simple`, `whash_db4`, `whash_db4_robust`,
  `crop_resistant_hash` per the per-algorithm divergence table in SPEC.md
  §"Boundary hash sizes".

### Tooling

- **Cross-port-diff harnesses** (both decode and squint sides) now count any
  port-level error as a fixture failure (was silent skip).
- **Bench harness** (`tools/bench/bench.py`): `startup_ms` calculation clamped
  to ≥ 0 to handle measurement noise on very fast cold-start ports.
- **Java classpath probe**: `tools/cross-squint-diff/diff_all_squint.py` and
  `tools/bench/bench.py` now honor `TURBOJPEG_JAR_PATH` / `TURBOJPEG_LIB_PATH`
  env vars and fall back through Debian/Ubuntu/RHEL/macOS-homebrew default
  locations. Previously hardcoded to Debian/x86-64.
- **JS browser entry**: `cropResistantHashBytes(bytes, limitSegments?)` now
  accepts the `limitSegments` argument the node entry has accepted all along.
- **Rust + Go `crop_resistant_hash`**: added the `limit_segments` parameter
  that Java/JS/Swift have. Stable sort by length descending matches Python.
- **Per-port `colorhash`**: `binbits <= 30` upper bound added in all 5
  native ports — prevents shift overflow for absurd binbits.
- **Per-port `1 << binbits` shift guard**: same.
- **JS hash functions**: now reject non-integer `hashSize` / `binbits` early
  (was silently treated as floor int).
- **Hash `unpack_square` perf nit**: linear sqrt loop replaced with
  `usize::isqrt` (Rust 1.84+), `math/bits` equivalent (Go), `Math.sqrt`
  (JS), `Double.squareRoot` (Swift).

### Removed

- The Swift dead `findRegion` function (lines 77-133 of pre-fix
  `FindSegments.swift`).
- JS `postinstall` symlink hack in `squint/js/rosetta-squint/package.json`.
  Replaced by direct `@jsquash/*` + `libheif-js` deps and a hoist-tolerant
  WASM URL resolver.

### Documentation

- Consolidated security/correctness audit (Raptor + GPT-5.4 + Claude Opus
  4.7 reviews + Claude fresh-eyes follow-up) — see [`SECURITY.md`](./SECURITY.md)
  for the per-reviewer breakdown (12 internal + 6 Raptor + 6 GPT-5.4 + 74+
  Claude findings across HIGH/MEDIUM/LOW/coverage-gap tiers), resolved or
  documented in this changelog. Raw audit reports are kept internal.
- `decode/SECURITY.md` updated: Go WebP via `chai2010/webp` is documented
  as bundling libwebp 1.4.0 source (not system libwebp); the original table
  was wrong about this. Fuzz coverage table added.
- `hash/spec/SPEC.md`: new "Boundary hash sizes" section documenting the
  size 2 win + size 32/64 deferred per-algorithm divergence pattern.
- `README.md` performance section: cold-start table with exact reproducible
  `make bench` command (one-shot CLI cost — startup + decode + hash + print).
