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

A C++ `throw` on malformed input becomes a wasm trap — the host catches it and
reports a decode error (use a fresh instance per decode, or reset).

## Runtime support / shared memory

This is a `wasm32-wasi-threads` build, so it declares a **shared memory**
(libde265's libc++ needs `<mutex>`, which the no-pthread wasi sysroot lacks).
It runs single-threaded (no worker threads spawned), but the runtime must
accept shared memory + the atomics proposal. Verified byte-exact in:

| Port | Runtime | Status |
|------|---------|--------|
| Go | wazero (`experimental.CoreFeaturesThreads`) | ✅ byte-exact |
| JS | Node/V8 native | ✅ byte-exact |
| Rust | wasmtime (`Config::wasm_threads`) | ✅ byte-exact |
| Java | Chicory 1.7.5 | ✅ byte-exact |
| Swift | WasmKit | ⛔ blocked — WasmKit's threads/atomics support needs Swift 6.1; only 5.9 available |

**To unblock Swift (and simplify every port):** build a **non-shared-memory**
variant — patch libde265 to drop its `std::mutex` usage (the worker pool is
already disabled) and compile with the *non*-pthread wasi-sdk toolchain. The
result needs no threads/atomics feature in any runtime and runs in WasmKit on
Swift 5.9. That is the recommended next build iteration.
