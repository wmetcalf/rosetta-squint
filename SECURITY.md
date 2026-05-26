# Security policy

`rosetta-squint` processes **untrusted image bytes** as a core use case
(perceptual-hash pipelines often run on user-supplied images). Hostile
inputs are the default assumption.

This top-level document is the entry point. The detailed per-half threat
models live at:

- **[`hash/SECURITY.md`](./hash/SECURITY.md)** — caller-trust model for the
  hash side (operates on already-decoded RGB/RGBA buffers; threats are
  caller-controlled `hash_size`, hex parsing, and buffer-shape mismatches).
- **[`decode/SECURITY.md`](./decode/SECURITY.md)** — the heavy side: format
  parsers in 5 languages, native FFI to libjpeg-turbo / libwebp / libheif /
  libtiff, MAX_PIXELS decompression-bomb guard, fuzz coverage table.

The merged `squint/` convenience layer adds path-API guards
(`MAX_FILE_SIZE = 256 MiB`, non-regular-file rejection) so callers passing
a `/dev/zero` or a 4 GB sparse file get a clean error rather than an OOM.

## Layered defenses

| Layer | Defense | Source of truth |
|---|---|---|
| Path API (squint) | `MAX_FILE_SIZE = 256 MiB`, `isRegularFile` check | per-port `decodeFile` |
| Bytes API (decode) | `MAX_PIXELS = 256 MP` checked **before** raster allocation | `decode/*/MAX_PIXELS` |
| Container parse (decode) | Header-byte sniffers for JPEG SOF, WebP VP8X, HEIC ispe, TIFF IFD, GIF LSD | dimension-sniff helpers in each port |
| Native FFI (decode) | setjmp/longjmp C shim for libjpeg, try/finally cleanup of libheif handles, `WebPGetFeatures` dim re-check | per-port FFI wrappers |
| Hash layer (hash) | `hash_size >= 2`, hex-length validation, buffer-shape validation, NaN-safe sorts | per-port hash entrypoints |
| Cross-port agreement | 70 (fixture × algo × size) byte-exact verifications across 6 ports | `tools/cross-squint-diff/diff_all_squint.py` |

## Fuzz coverage

- **Rust decode**: cargo-fuzz targets at `decode/rust/rosetta-squint-decode/fuzz/`
  — `decode_any`, `decode_with_prefix`, `detect_format`. Produced the
  `[0xFF, 0xD8]` truncated-SOI finding that motivated the in-tree libjpeg
  C shim.
- **Rust hash**: cargo-fuzz targets at `hash/rust/rosetta-squint-hash/fuzz/`
  — `hex_to_hash`, `hex_to_flathash`, `hex_to_multihash`.
- **Go decode**: native Go 1.18+ fuzz at `decode/go/imagedecode/fuzz_decode_test.go`
  — `FuzzDecodeAny`, `FuzzDecodeWithPrefix`. Same property contract as the
  Rust targets: `Decode()` MUST NOT panic on any input bytes.

JS, Java, and Swift do not have fuzz harnesses yet — this is a known gap.

## Dependency advisory automation

Three CI workflows run weekly dep-audit + govulncheck:

- `hash/.github/workflows/ci.yml` — `cargo audit`, `npm audit --audit-level=high`,
  `pip-audit`, `govulncheck`. Covers `hash/rust`, `hash/js`, `hash/python`,
  `hash/go` lockfiles.
- `decode/.github/workflows/ci.yml` — same matrix for the decode lockfiles.
- `.github/workflows/squint-ci.yml` — same matrix for the squint convenience-
  layer lockfiles, plus a `cross-squint-diff` job that verifies all 6 ports
  agree byte-exactly on every supported (algo × size) combination, plus a
  `max-file-size-guards` job that smoke-tests the path-API rejection of a
  300 MiB file and `/dev/zero` against each squint CLI.

## Reporting a vulnerability

Please email **william.metcalf@gmail.com**. Do not open a public issue for
security-sensitive reports.

For non-sensitive issues (e.g., a port disagrees on a fixture's hash, a
specific corrupt file causes a crash that's NOT exploitable), the GitHub
issue tracker is fine.

## Audit trail

This repository has been reviewed by:

- **Internal review** — 12 findings resolved (overflow, defensive copies,
  resource cleanup, ftyp brand whitelist).
- **Gemini review** — §3.2 Go HEIC cgo GC documented; §4.1 Python ctypes
  cross-platform `_load_libheif_xplat` added.
- **Raptor audit** — 6 findings; resolved or documented.
- **GPT-5.4 audit** — 6 findings; resolved or documented.
- **Claude Opus 4.7 audit + fresh-eyes follow-up** — 74+ findings total
  across 4 tiers (HIGH / MEDIUM / LOW / coverage gaps); see
  [`CHANGELOG.md`](./CHANGELOG.md) for the per-finding fix status. Raw
  audit reports are kept internal.

No external paid audit has been performed.
