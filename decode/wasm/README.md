# Shared HEIC decoder WASM

`libheif_decode.wasm` — a portable WASI reactor module bundling **libheif 1.21.2
+ libde265 1.0.15**, decoding HEIC → YCbCr 4:2:0 planes. One artifact, used by
every language port via a wasm runtime, so all ports produce **bit-identical**
HEIC pixels → identical perceptual hashes.

## Validated (2026-06-01)

- **Bit-identical to native libheif 1.21.2** (pillow-heif 1.3.0) on all 10 HEIC
  fixtures, every Y/Cb/Cr plane.
- **Deterministic across runtimes** — same wasm gives bit-identical planes in
  wazero (pure-Go) and V8 (Node).
- Security-clean: libheif 1.21.2 includes the CVE-2025-68431 fix.

## Why a custom build (the key finding)

libheif's emscripten path (`plugins/decoder_libde265.cc`) **disables the HEVC
deblocking + SAO in-loop filters** "to speed up decoding from JavaScript". Those
filters change pixel output — and that, not SIMD rounding, was the *entire*
source of the ±1–2 px native-vs-`libheif-js` divergence. This build adds a
`__wasi__` branch that keeps the filters **ON** (and runs single-threaded, no
worker threads), so output matches a normal native libheif decode exactly.

## Rebuild

```
./build.sh [workdir]     # needs wasi-sdk-25, cmake, ninja, git, network
```

See `build.sh` for the full recipe; `patches/` holds the two source patches
(libde265 threads-optional, libheif filters-on/synchronous); `shim/` holds the
wasi-libc gap stubs (`mkstemp`, no-exception `__cxa_*` → trap).

## ABI

WASI reactor. Imports: `wasi_snapshot_preview1` (standard) + a `wasi.thread-spawn`
stub (never called — single-threaded). Exports (named): the libheif C API subset
needed for decode + `malloc`/`free`. Drive it as:

```
_initialize()                                            # once, runs ctors
ctx = heif_context_alloc()
heif_context_set_max_decoding_threads(ctx, 0)            # belt-and-suspenders
heif_context_read_from_memory(err, ctx, ptr, len, 0)
heif_context_get_primary_image_handle(err, ctx, &handle)
heif_decode_image(err, handle, &img, 0/*YCbCr*/, 1/*chroma_420*/, 0)
# per channel Y(0)/Cb(1)/Cr(2): heif_image_get_plane_readonly(img, ch, &stride),
#   heif_image_get_width/height(img, ch); de-stride to packed planes.
```

Each port then does the shared integer YCbCr→RGB + 4:2:0 upsampling (next step).
A C++ `throw` on malformed input becomes a wasm trap — the host catches it and
reports a decode error (use a fresh instance per decode, or reset).
