# HEIC cross-port reproducibility — Phase 0 findings & decision

**Date:** 2026-06-01 · **Status:** shared WASM decoder BUILT & validated (`../wasm/`).

## UPDATE — root cause corrected, clean WASM built & bit-identical

The Phase 0 "native vs WASM diverges ±1–2, even in the YCbCr planes" finding
below was measured against **`libheif-js`**, whose emscripten build
(`plugins/decoder_libde265.cc`) **disables the HEVC deblocking + SAO in-loop
filters** "to speed up decoding from JavaScript". *That* — not SIMD rounding — is
the entire divergence. HEVC reconstruction is bit-exact, so with the filters
left ON the builds agree.

Proven by building a clean WASI module (`decode/wasm/`, libheif 1.21.2 +
libde265 1.0.15, filters ON, single-threaded) and measuring:
- **Bit-identical to native libheif 1.21.2** (pillow-heif 1.3.0) on all 10
  fixtures, every Y/Cb/Cr plane.
- **Bit-identical across runtimes** (wazero pure-Go vs V8).

So the corrected picture: byte-exact HEIC across ports needs **one libheif
version + filters ON everywhere**. The shared WASM (now built) delivers exactly
that and matches native — so native ports could even keep using native libheif
at the same version, while the JS port must stop using filter-disabled
`libheif-js`. The option table below still holds (one shared artifact is the
clean way to guarantee uniform version+settings); only the *reason* changes from
"SIMD diverges" to "libheif-js disables filters".

Original Phase 0 write-up follows (kept for the record; read item 3 with the
correction above).

---

## Problem

rosetta-squint's value is **identical perceptual hashes across every language
port** for the same input. For HEIC this currently does not hold byte-exactly:
the native ports (Go/Rust/Java/Swift) link the OS's `libheif` and only agree
because Linux x86 CI gives them all the *same* build; the JS port (`libheif-js`,
WASM) decodes ±1–2 px differently and is checked against a **±2 tolerance**, not
byte-exact (`decode/js/.../tests/group2-heic.test.ts`). The HEIC golden itself is
generated from **system libheif 1.17.6** (`SPEC.md §16`), so it drifts with the OS
— which is exactly what broke macOS CI when Homebrew moved libheif to 1.22.0.

**Requirement driving this work:** cross-port byte-exact **`phash`** on HEIC.

## What Phase 0 measured (all at libheif 1.18.2 unless noted)

Experiments: pillow-heif 0.20.0 (native libheif 1.18.2 wheel) vs `libheif-js`
1.18.2 (`/wasm-bundle`, WASM), decoding the 10 valid HEIC fixtures.

1. **Native vs WASM RGB differ.** 5/10 fixtures differ, max Δ2 per channel.
   Matches the project's existing documented ±2 JS tolerance.

2. **The ±2 drift changes `phash`.** Hashing the divergent decodes with the
   squint algorithms (via `imagehash`):

   | hash | result on ±2-divergent fixtures |
   |------|---------------------------------|
   | average_hash, dhash, dhash_vertical, phash_simple, whash | identical |
   | **phash** (DCT, size 8) | **differs, up to 16/64 bits** |
   | **phash** (DCT, size 16) | **differs, up to 34/256 bits** |

   ⇒ byte-exact HEIC decode is required *iff* cross-port `phash` is a hard goal.
   The other five hash families are already reproducible within tolerance.

3. **The divergence is in the HEVC decode, not color conversion.** Decoding to
   raw **YCbCr 4:2:0** (before any YCbCr→RGB step) still differs ±1 between
   native and WASM at the *same* libde265 1.0.15:

   | fixture | Y diff | Cb diff | Cr diff |
   |---------|--------|---------|---------|
   | 32x32-q50 | 32 bytes (Δ1) | 8 (Δ1) | 0 |
   | 64x64-q90 | 48 (Δ1) | 0 | 0 |
   | larger-128x96 | 110 (Δ1) | 24 (Δ1) | 42 (Δ1) |
   | photo-96 | 43 (Δ1) | 8 (Δ1) | 0 |
   | (lossless / small fixtures) | 0 | 0 | 0 |

   Cause: libde265's native x86 SIMD paths round differently than the WASM build.
   ⇒ "move YCbCr→RGB into shared port code" (a tempting cheap fix) does **not**
   work — the YCbCr input is already divergent. Independent native builds across
   architectures (x86 vs ARM) diverge for the same reason.

4. **The same WASM is bit-identical across runtimes.** Driving `libheif-js`'s
   wasm directly (raw C API → YCbCr) from **wazero** (pure-Go) vs **V8** (Node):

   | plane (32x32-q50) | wazero vs V8 (same wasm) | wazero vs native x86 |
   |-------------------|--------------------------|----------------------|
   | Y | **identical** | differ 32 bytes (Δ1) |
   | Cb | **identical** | differ 8 bytes (Δ1) |
   | Cr | **identical** | identical |

   ⇒ cross-runtime determinism holds for the integer HEVC decode (as the WASM
   spec guarantees). The wasm is the arch-independent common denominator.

## Options considered

| Option | Byte-exact phash? | Verdict |
|--------|-------------------|---------|
| (a) Keep ±2 tolerance, document `phash`-on-HEIC as best-effort, steer users to dhash/whash/etc. | No | Cheapest; **rejected** because phash byte-exactness is a hard requirement |
| (b) Pin one libheif *version* everywhere, mixed native/WASM mechanisms | No | Proven false — native vs WASM diverge at the same version (finding 1, 3) |
| (c) Bit-exact YCbCr from libde265 + shared port-code color conversion | No | Proven false — YCbCr itself diverges (finding 3) |
| (d) Same native libheif build everywhere | Only per-arch | x86 vs ARM SIMD diverge; not cross-platform |
| **(e) One shared WASM decoder in every port** | **Yes** | **Chosen** — only path that holds across heterogeneous ports/arches; proven viable (finding 4) |

## Decision: shared-core WASM for HEIC (option e)

```
one pinned libheif + libde265 WASM (1.21.2, security-clean — also fixes
CVE-2025-68431)                                  [arch-independent, deterministic]
        │  driven by a wasm runtime in each port
        ▼
decode to YCbCr 4:2:0      [integer → bit-identical across runtimes, finding 4]
        ▼
shared integer YCbCr→RGB + 4:2:0 upsampling, reimplemented identically per port
(same pattern as BMP/GIF)                         [deterministic]
        ▼
bit-identical RGB  →  bit-identical phash across all ports
```

Then regenerate the HEIC goldens from this pipeline and flip the JS test from
±2-tolerance to byte-exact. `formats.json` HEIC `reference_lib` becomes the
pinned WASM, not system libheif.

Runtimes (mostly dependency-pure): wazero (Go), chicory (Java), WasmKit (Swift),
wasmtime (Rust), wasmtime-py (Python), native (JS).

## Build plan / open work

1. **Build a clean WASI/standalone libheif+libde265 WASM** at 1.21.2 with a
   minimal documented decode export (`decode(bytes) → {w,h,planes}`), via
   `wasi-sdk` or emscripten `STANDALONE_WASM`. **Do not** reuse `libheif-js`'s
   minified emscripten module — Phase 0 showed driving it needs ctors + reverse-
   engineered minified exports + emscripten import stubs (got only 1/10 fixtures
   through a throwaway harness; that one matched V8 bit-for-bit). This build is
   the gating deliverable and needs the wasm toolchain.
2. Shared integer YCbCr→RGB + 4:2:0 upsampling spec + per-port impls.
3. Wire the wasm runtime into each port; decode HEIC via the shared module.
4. Regenerate HEIC goldens from the canonical pipeline; update `formats.json`.
5. Flip JS HEIC test to byte-exact; remove the ±2 tolerance.
6. Verify cross-port `phash` byte-identity on the full corpus.

## Reproducing the Phase 0 experiments

Throwaway scripts lived in `/tmp/heic-phase0` (ephemeral): `decode_native.py` /
`decode_wasm.js` (RGB), `ycbcr_native.py` / `ycbcr_wasm.js` (YCbCr planes), and
`wazero-spike/main.go` (wazero driver). Pin pairs: pillow-heif 0.20.0 = libheif
1.18.2 native; `libheif-js@1.18.2` `/wasm-bundle` = libheif 1.18.2 WASM.
