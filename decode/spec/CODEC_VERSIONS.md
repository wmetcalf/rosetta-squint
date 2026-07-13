# rosetta-squint — CODEC VERSIONS manifest (SKETCH)

> Status: **draft / sketch.** This documents the *current* reality and the
> *target* invariant. Cells marked ⚠️ `SYSTEM` are not yet repo-controlled and
> are the work items. Versions marked `TODO` need to be extracted and pinned.

## Why this file exists

rosetta-squint guarantees **byte-exact-identical** decoded pixels (and therefore
identical perceptual hashes) across every language port. That guarantee holds
only if every port decodes with the **same codec implementation at the same
version** — and that version must be **controlled by the repo, not the OS**.

A decoder is **PINNED** (safe) when its version is fixed by the repo:
a lockfile dependency, vendored/static-built C source, a WASM bundle, or a
language stdlib tied to a pinned toolchain. It is ⚠️ **SYSTEM** (drift risk) when
it resolves a shared library from the OS at build/runtime (apt/brew/dnf,
pkg-config dynamic link, `Native.load`, `ctypes`). SYSTEM decoders silently
change output when the OS updates — breaking reproducibility **for end users**,
not just CI. See `SPEC.md` for the byte-level format specs; this file is the
*version-control* companion to `formats.json`.

**Reference / golden generator:** the Python port (`spec/regenerate.py`) using
**Pillow 12.3.0**, whose manylinux wheels bundle their own
libjpeg-turbo / libpng / libwebp / libtiff / zlib. Every other port must match
Pillow's bytes. **HEIC is the exception** — its golden is produced by
`spec/synth_heic.py` / `regenerate.py` against **system libheif 1.17.6**, so the
HEIC reference *itself* drifts with the OS. Fixing that is the top work item.

---

## Canonical codec versions (source of truth)

The version every port must converge on, per codec. PNG/GIF/BMP have no shared C
codec (pure/stdlib everywhere) so they carry no drift risk and are omitted here.

| Codec          | Canonical version            | Anchored to                        | Notes |
|----------------|------------------------------|------------------------------------|-------|
| `libjpeg-turbo`| `TODO` (Pillow 12.3.0 bundle)| JPEG golden (Pillow)               | Rust/JS use **mozjpeg**, a libjpeg-turbo fork; decode path is identical for baseline JPEG, hence they match. |
| `libwebp`      | `1.4.0` (proposed)           | WebP golden (Pillow) — verify match| Go vendors `libwebp-1.4.0`; confirm Pillow 12.3.0 + jsquash bundle the same. |
| `libtiff`      | `TODO` (Pillow 12.3.0 bundle)| TIFF golden (Pillow)               | Only Swift links real libtiff; Go/Rust/JS/Java **reimplement** TIFF and must byte-match libtiff. |
| `libheif`      | `1.17.6` (current, ⚠️ system)| HEIC golden (**system** libheif)   | The one codec whose golden is OS-generated. Target: pin to one repo-controlled libheif everywhere (incl. the golden). |

> Extract the `TODO` bundle versions:
> ```
> python -c "import PIL.features as f; f.pilinfo()"        # libjpeg-turbo / libtiff / libwebp bundled in Pillow 12.3.0
> # Rust mozjpeg-sys vendored version: see ~/.cargo/registry/.../mozjpeg-sys-2.2.3/mozjpeg/
> # JS bundled versions: node_modules/@jsquash/{jpeg,webp}/package.json + upstream codec pin
> # JS libheif: node_modules/libheif-js → bundled libheif build version
> ```

---

## Per-port × per-codec status (current reality)

Legend: ✅ PINNED (repo-controlled) · ⚠️ SYSTEM (OS-controlled, drift risk).
Python is the reference (golden generator), shown for completeness.

| Format | Go | Rust | JS | Python (ref) | Java | Swift |
|--------|----|----|----|--------------|------|-------|
| **BMP**  | ✅ pure | ✅ pure | ✅ pure-TS | ✅ Pillow 12.3.0 | ✅ pure | ✅ pure |
| **GIF**  | ✅ `image/gif` (stdlib) | ✅ `gif 0.14.2` | ✅ pure-TS | ✅ Pillow 12.3.0 | ✅ pure | ✅ pure |
| **PNG**  | ✅ `image/png` (stdlib) | ✅ `png 0.18.1` | ✅ `pngjs 7.0.0` | ✅ Pillow 12.3.0 | ✅ `javax.imageio` (JDK 21) | ✅ `swift-png 4.3.0` |
| **JPEG** | ⚠️ system `libturbojpeg` | ✅ `mozjpeg-sys 2.2.3` (vendored) | ✅ `@jsquash/jpeg 1.6.0` (WASM) | ✅ Pillow (bundled libjpeg-turbo) | ⚠️ `turbojpeg 2.1.5` → system | ⚠️ `Cjpeg` → system `libturbojpeg` |
| **TIFF** | ✅ `x/image/tiff v0.41.0` | ✅ `tiff 0.11.3` | ✅ `utif2 4.1.0` | ✅ Pillow (bundled libtiff) | ✅ `twelvemonkeys imageio-tiff 3.10.1` | ⚠️ `Ctiff` → system `libtiff-4` |
| **WebP** | ✅ `chai2010/webp 1.4.0` (vendored libwebp-1.4.0) | ⚠️ `libwebp-sys2 0.1.11` → system | ✅ `@jsquash/webp 1.5.0` (WASM) | ✅ Pillow (bundled libwebp) | ⚠️ JNA → system `libwebp` | ⚠️ `Cwebp` → system `libwebp` |
| **HEIC** | ⚠️ `strukturag/libheif 1.17.6` → system | ⚠️ `libheif-rs 2.7.0` → system | ✅ `libheif-js 1.17.1` (WASM) | ⚠️ `ctypes` → system `libheif.so.1` | ⚠️ JNA → system `libheif` | ⚠️ `Cheif` → system `libheif` |

### Drift-risk inventory (the ⚠️ work items)

| Codec | SYSTEM in ports | Already PINNED in | Suggested fix |
|-------|-----------------|-------------------|---------------|
| `libheif` (HEIC) | Go, Rust, Python(ref), Java, Swift — **5/6 + golden** | JS (WASM 1.17.1) | **Shared core** — one repo-controlled libheif (vendored static or the JS WASM) used everywhere incl. the golden generator. Highest priority: the golden itself is OS-bound. |
| `libwebp` (WebP) | Rust, Java, Swift | Go (vendored 1.4.0), JS (WASM), Python | Vendor/static-build libwebp 1.4.0 in Rust/Java/Swift (Go already shows the pattern). |
| `libturbojpeg` (JPEG) | Go, Java, Swift | Rust (mozjpeg vendored), JS (WASM), Python | Vendor/static-build a pinned libjpeg-turbo, or move to mozjpeg like Rust/JS. |
| `libtiff` (TIFF) | Swift only | Go/Rust/JS/Java (pure reimpl.), Python | Swift → pure-Swift TIFF or vendored libtiff at the pinned version. |

### Known "works today but fragile" mismatches

- **JS HEIC pins libheif `1.17.1`, reference is system `1.17.6`.** Decodes
  identically for the current fixtures, but they are not the *same* build.
- **Rust/JS JPEG use mozjpeg; reference is libjpeg-turbo (Pillow).** Baseline
  decode is identical (mozjpeg's changes are encode-side), so they match — but
  this is a property to *test*, not assume.
- **TIFF: Go/Rust/JS/Java reimplement TIFF and must byte-match Pillow's
  libtiff.** Currently green on the fixture set; a libtiff bump on the golden
  side could expose divergence the pure ports can't follow.

---

## Intended CI guard (turn silent drift into a loud failure)

A machine-readable `codec_versions.json` (companion to `formats.json`) plus a
per-port assertion that the *runtime* codec version equals the manifest:

```jsonc
// codec_versions.json (proposed)
{
  "schema_version": 1,
  "codecs": {
    "libheif":       { "canonical": "1.17.6", "runtime_probe": "heif_get_version()" },
    "libwebp":       { "canonical": "1.4.0",  "runtime_probe": "WebPGetDecoderVersion()" },
    "libtiff":       { "canonical": "TODO",   "runtime_probe": "TIFFGetVersion()" },
    "libjpeg-turbo": { "canonical": "TODO",   "runtime_probe": "compile-time LIBJPEG_TURBO_VERSION" }
  }
}
```

Runtime version probes that exist:
- **libheif** → `heif_get_version()` / `heif_get_version_number()`
- **libwebp** → `WebPGetDecoderVersion()` (returns packed `0xMMmmpp`)
- **libtiff** → `TIFFGetVersion()` (version string)
- **libjpeg-turbo** → no clean runtime API; assert the compile-time
  `LIBJPEG_TURBO_VERSION` / the vendored pin instead.

Each port's test suite prints its linked codec versions and fails if any differ
from `codec_versions.json`. Result: an OS bump that changes a system codec
becomes an explicit **"libwebp 1.4.0 expected, 1.5.1 found"** failure instead of
a mystery byte-divergence three layers down.

## The tradeoff this manifest commits us to

Pinning codecs for reproducibility means **opting out of automatic OS security
patches**. Once a codec is vendored/pinned, a CVE fix does *not* arrive via
`apt upgrade` — it requires a deliberate manifest bump → regenerate goldens →
run the cross-version hash-stability check → ship. That is the correct tradeoff
for a determinism library, but it requires a standing process to watch upstream
codec CVEs (libheif especially) and pull bumps in promptly. See the libheif
GO-2026-5032 / CVE-2025-68431 history as the motivating example.

---

## Next steps (roadmap, not yet done)

1. Fill every `TODO` with the exact bundled version (commands above).
2. Land `codec_versions.json` + the per-port version-assert CI guard.
3. Add the cross-version **hash-stability** test (decode fixtures under
   codec vN vs vN+1, assert identical squint hashes) so codec bumps are safe.
4. Convert ⚠️ cells to PINNED, easiest-first:
   TIFF/Swift → WebP/{Rust,Java,Swift} → JPEG/{Go,Java,Swift} → **HEIC (shared core)**.
5. Make the **HEIC golden** repo-controlled (stop generating it from system libheif).
