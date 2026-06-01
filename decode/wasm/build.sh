#!/usr/bin/env bash
# Build a portable, byte-exact libheif+libde265 WASM decoder for rosetta-squint.
#
# Produces libheif_decode.wasm: a WASI reactor module that decodes HEIC to
# YCbCr 4:2:0 planes, BIT-IDENTICAL to a native libheif 1.21.2 decode and
# deterministic across wasm runtimes (verified in wazero and V8). One artifact,
# every port -> identical HEIC pixels -> identical perceptual hash.
#
# Why a custom build (not libheif-js): libheif's emscripten path
# (plugins/decoder_libde265.cc) DISABLES the HEVC deblocking + SAO in-loop
# filters "to speed up decoding from JavaScript". That changes pixel output and
# is the entire source of the ±1-2 px native-vs-libheif-js divergence. This
# build keeps the filters ON (see the patch) so output matches native libheif.
#
# Requirements: wasi-sdk-25 (clang 19), cmake >= 3.16, ninja, git, network.
set -euo pipefail

ROOT="${1:-$HOME/heic-wasm}"          # work dir
WASI="$ROOT/wasi-sdk"                 # wasi-sdk-25 unpacked here
LIBHEIF_TAG=v1.21.2                   # security-clean (fixes CVE-2025-68431)
LIBDE265_TAG=v1.0.15
HERE="$(cd "$(dirname "$0")" && pwd)" # decode/wasm (for patches/shim)

mkdir -p "$ROOT"; cd "$ROOT"

# 0. toolchain
[ -x "$WASI/bin/clang" ] || {
  curl -sL https://github.com/WebAssembly/wasi-sdk/releases/download/wasi-sdk-25/wasi-sdk-25.0-x86_64-linux.tar.gz | tar xz
  mv wasi-sdk-25.0-x86_64-linux wasi-sdk
}

# 1. sources
[ -d libde265 ] || git clone -q --depth 1 --branch "$LIBDE265_TAG" https://github.com/strukturag/libde265.git
[ -d libheif ]  || git clone -q --depth 1 --branch "$LIBHEIF_TAG"  https://github.com/strukturag/libheif.git

# 2. patches
#  - libde265: Threads is provided by wasi-libc, not a separate lib (drop find_package REQUIRED + Threads::Threads link)
#  - libheif:  add a __wasi__ branch to decoder_libde265.cc -> synchronous decode, filters ON
git -C libde265 apply "$HERE/patches/libde265-threads-optional.patch" 2>/dev/null || \
  ( sed -i 's/find_package(Threads REQUIRED)/message(STATUS "Threads via wasi-libc")/' libde265/CMakeLists.txt
    sed -i 's/target_link_libraries(de265 PRIVATE Threads::Threads)/# Threads in wasi-libc/' libde265/libde265/CMakeLists.txt )
git -C libheif apply "$HERE/patches/libheif-decoder_libde265-filters-threads.patch" 2>/dev/null || true

EMU="-D_WASI_EMULATED_SIGNAL -D_WASI_EMULATED_MMAN -D_WASI_EMULATED_PROCESS_CLOCKS -D_WASI_EMULATED_GETPID"
TC="$WASI/share/cmake/wasi-sdk-pthread.cmake"   # pthread variant: libc++ <mutex> needs it (run single-threaded)

# 3. libde265 (decoder library only)
cmake -S libde265 -B build-de265 -G Ninja -DCMAKE_TOOLCHAIN_FILE="$TC" \
  -DCMAKE_POLICY_VERSION_MINIMUM=3.5 -DBUILD_SHARED_LIBS=OFF \
  -DENABLE_SDL=OFF -DENABLE_DECODER=OFF -DENABLE_ENCODER=OFF \
  -DCMAKE_C_FLAGS="$EMU" -DCMAKE_CXX_FLAGS="$EMU" -DCMAKE_BUILD_TYPE=Release
cmake --build build-de265 --target de265 -j"$(nproc)"

# stage libde265 for libheif's FindLIBDE265 (incl. the generated version header)
mkdir -p de265-stage/lib de265-stage/include/libde265
find build-de265 -name de265-version.h -exec cp {} libde265/libde265/ \;
cp libde265/libde265/*.h de265-stage/include/libde265/
cp build-de265/libde265/libde265.a de265-stage/lib/

# 4. libheif (decode-only, libde265 built-in static, no plugins/threads/other codecs)
cmake -S libheif -B build-heif -G Ninja -DCMAKE_TOOLCHAIN_FILE="$TC" \
  -DCMAKE_POLICY_VERSION_MINIMUM=3.5 -DBUILD_SHARED_LIBS=OFF \
  -DLIBDE265_INCLUDE_DIR="$ROOT/de265-stage/include" -DLIBDE265_LIBRARY="$ROOT/de265-stage/lib/libde265.a" \
  -DWITH_LIBDE265=ON -DENABLE_PLUGIN_LOADING=OFF -DWITH_LIBDE265_PLUGIN=OFF \
  -DWITH_AOM_DECODER=OFF -DWITH_AOM_ENCODER=OFF -DWITH_DAV1D=OFF -DWITH_FFMPEG_DECODER=OFF \
  -DWITH_X265=OFF -DWITH_SvtEnc=OFF -DWITH_RAV1E=OFF -DWITH_KVAZAAR=OFF \
  -DWITH_JPEG_DECODER=OFF -DWITH_JPEG_ENCODER=OFF -DWITH_OpenJPEG_DECODER=OFF -DWITH_OpenJPEG_ENCODER=OFF \
  -DWITH_OPENJPH_ENCODER=OFF -DWITH_OPENJPH_DECODER=OFF -DWITH_UVG266=OFF -DWITH_VVDEC=OFF -DWITH_VVENC=OFF \
  -DWITH_UNCOMPRESSED_CODEC=OFF -DWITH_LIBSHARPYUV=OFF -DWITH_HEADER_COMPRESSION=OFF \
  -DWITH_EXAMPLES=OFF -DBUILD_TESTING=OFF -DWITH_GDK_PIXBUF=OFF -DENABLE_MULTITHREADING_SUPPORT=OFF \
  -DCMAKE_C_FLAGS="$EMU -include $HERE/shim/heif_shim.h" \
  -DCMAKE_CXX_FLAGS="$EMU -include $HERE/shim/heif_shim.h" -DCMAKE_BUILD_TYPE=Release
cmake --build build-heif --target heif -j"$(nproc)"

# 5. shim (mkstemp + no-exception __cxa stubs) and final reactor link with named exports
"$WASI/bin/clang" --target=wasm32-wasi-threads --sysroot="$WASI/share/wasi-sysroot" -pthread -O2 \
  -c "$HERE/shim/heif_shim.c" -o heif_shim.o
EXPORTS="heif_context_alloc heif_context_free heif_context_read_from_memory \
  heif_context_get_primary_image_handle heif_decode_image heif_image_get_plane_readonly \
  heif_image_get_width heif_image_get_height heif_image_has_channel heif_image_get_chroma_format \
  heif_image_handle_has_alpha_channel heif_image_handle_get_width heif_image_handle_get_height \
  heif_image_release heif_image_handle_release heif_context_set_max_decoding_threads malloc free"
EXPFLAGS=""; for e in $EXPORTS; do EXPFLAGS="$EXPFLAGS -Wl,--export=$e"; done
"$WASI/bin/clang++" --target=wasm32-wasi-threads --sysroot="$WASI/share/wasi-sysroot" -pthread \
  -mexec-model=reactor -O2 -Wl,--initial-memory=67108864 -Wl,--max-memory=1073741824 \
  -Wl,--whole-archive build-heif/libheif/libheif.a de265-stage/lib/libde265.a -Wl,--no-whole-archive \
  heif_shim.o -lwasi-emulated-signal -lwasi-emulated-mman -lwasi-emulated-process-clocks -lwasi-emulated-getpid \
  $EXPFLAGS -o libheif_decode.wasm
echo "built: $ROOT/libheif_decode.wasm"

# Caller drives: _initialize() once, then per image:
#   ctx=heif_context_alloc(); heif_context_set_max_decoding_threads(ctx,0);
#   heif_context_read_from_memory(err,ctx,ptr,len,0);
#   heif_context_get_primary_image_handle(err,ctx,&h);
#   heif_decode_image(err,h,&img, 0/*YCbCr*/, 1/*chroma420*/, 0);
#   per channel Y/Cb/Cr: heif_image_get_plane_readonly(img,ch,&stride), _get_width/_get_height.
# Imports needed: wasi_snapshot_preview1 (standard) + a "wasi"."thread-spawn" stub (never called).
