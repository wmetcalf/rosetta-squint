#!/usr/bin/env python3
"""Decode an image with PIL/Pillow and emit raw bytes in the spec/SPEC.md §2
wire format (12-byte header + row-major pixels) to stdout.

For HEIC, uses the ctypes-around-system-libheif wrapper instead of pillow-heif
to match the 5 ports' reference. See spec/regen_heic_via_system.py for the
ctypes wrapper.

Usage:
    decode_pil.py <fixture.path>

Exit codes:
    0 — success, bytes on stdout
    1 — decode error, message on stderr
    2 — harness error (file not found, etc.)
"""
from __future__ import annotations

import struct
import sys
from pathlib import Path

from PIL import Image


def emit(width: int, height: int, channels: int, pixels: bytes) -> None:
    """Write spec/SPEC.md §2 binary format to stdout."""
    if len(pixels) != width * height * channels:
        print(
            f"pixel buffer length {len(pixels)} != {width}*{height}*{channels}",
            file=sys.stderr,
        )
        sys.exit(1)
    out = sys.stdout.buffer
    out.write(struct.pack("<II", width, height))
    out.write(bytes([channels, 0, 0, 0]))
    out.write(pixels)
    out.flush()


def decode_heic_via_system_libheif(path: Path) -> tuple[int, int, int, bytes]:
    """Use ctypes around system libheif 1.17.6 (NOT pillow-heif's bundled
    1.21.2, which differs by ±1 px). This is the same wrapper that produced
    spec/decoded/heic/valid/*.bin goldens — see SPEC.md §16."""
    import ctypes
    import ctypes.util
    import sys

    # Cross-platform libheif loading (Gemini review §4.1).
    if sys.platform == "darwin":
        candidates = ["libheif.dylib", "libheif.1.dylib"]
    elif sys.platform == "win32":
        candidates = ["libheif.dll", "libheif-1.dll"]
    else:
        candidates = ["libheif.so.1", "libheif.so"]
    found = ctypes.util.find_library("heif")
    if found:
        candidates.append(found)
    lib = None
    for name in candidates:
        try:
            lib = ctypes.CDLL(name)
            break
        except OSError:
            continue
    if lib is None:
        raise OSError(
            f"libheif not found. Tried: {', '.join(candidates)}. "
            f"Install via your package manager."
        )
    # Minimal C API surface; see /usr/include/libheif/heif.h
    lib.heif_context_alloc.restype = ctypes.c_void_p
    lib.heif_context_free.argtypes = [ctypes.c_void_p]
    lib.heif_context_read_from_memory_without_copy.argtypes = [
        ctypes.c_void_p, ctypes.c_char_p, ctypes.c_size_t, ctypes.c_void_p
    ]
    lib.heif_context_read_from_memory_without_copy.restype = ctypes.c_int64
    lib.heif_context_get_primary_image_handle.argtypes = [
        ctypes.c_void_p, ctypes.POINTER(ctypes.c_void_p)
    ]
    lib.heif_context_get_primary_image_handle.restype = ctypes.c_int64
    lib.heif_image_handle_release.argtypes = [ctypes.c_void_p]
    lib.heif_image_handle_get_width.argtypes = [ctypes.c_void_p]
    lib.heif_image_handle_get_width.restype = ctypes.c_int
    lib.heif_image_handle_get_height.argtypes = [ctypes.c_void_p]
    lib.heif_image_handle_get_height.restype = ctypes.c_int
    lib.heif_image_handle_has_alpha_channel.argtypes = [ctypes.c_void_p]
    lib.heif_image_handle_has_alpha_channel.restype = ctypes.c_int
    lib.heif_decode_image.argtypes = [
        ctypes.c_void_p, ctypes.POINTER(ctypes.c_void_p),
        ctypes.c_int, ctypes.c_int, ctypes.c_void_p
    ]
    lib.heif_decode_image.restype = ctypes.c_int64
    lib.heif_image_release.argtypes = [ctypes.c_void_p]
    lib.heif_image_get_plane_readonly.argtypes = [
        ctypes.c_void_p, ctypes.c_int, ctypes.POINTER(ctypes.c_int)
    ]
    lib.heif_image_get_plane_readonly.restype = ctypes.c_void_p

    HEIF_COLORSPACE_RGB = 1
    HEIF_CHROMA_INTERLEAVED_RGB = 10
    HEIF_CHROMA_INTERLEAVED_RGBA = 11
    HEIF_CHANNEL_INTERLEAVED = 10

    data = path.read_bytes()
    ctx = lib.heif_context_alloc()
    if not ctx:
        raise RuntimeError("heif_context_alloc failed")
    try:
        # The error struct is returned by value; we lose details but get the
        # exit code via a sentinel: the function still wrote handle on success.
        # Simplification: any non-null handle indicates success.
        handle_ref = ctypes.c_void_p()
        lib.heif_context_read_from_memory_without_copy(ctx, data, len(data), None)
        lib.heif_context_get_primary_image_handle(ctx, ctypes.byref(handle_ref))
        handle = handle_ref.value
        if not handle:
            raise RuntimeError("heif_context_get_primary_image_handle failed")
        try:
            width = lib.heif_image_handle_get_width(handle)
            height = lib.heif_image_handle_get_height(handle)
            has_alpha = lib.heif_image_handle_has_alpha_channel(handle) != 0
            chroma = (
                HEIF_CHROMA_INTERLEAVED_RGBA if has_alpha else HEIF_CHROMA_INTERLEAVED_RGB
            )
            channels = 4 if has_alpha else 3
            img_ref = ctypes.c_void_p()
            lib.heif_decode_image(handle, ctypes.byref(img_ref), HEIF_COLORSPACE_RGB, chroma, None)
            img = img_ref.value
            if not img:
                raise RuntimeError("heif_decode_image failed")
            try:
                stride = ctypes.c_int(0)
                plane = lib.heif_image_get_plane_readonly(
                    img, HEIF_CHANNEL_INTERLEAVED, ctypes.byref(stride)
                )
                if not plane:
                    raise RuntimeError("heif_image_get_plane_readonly null")
                pixels = bytearray()
                row_bytes = width * channels
                for y in range(height):
                    row_addr = plane + y * stride.value
                    row = ctypes.string_at(row_addr, row_bytes)
                    pixels.extend(row)
                return width, height, channels, bytes(pixels)
            finally:
                lib.heif_image_release(img)
        finally:
            lib.heif_image_handle_release(handle)
    finally:
        lib.heif_context_free(ctx)


def main() -> int:
    if len(sys.argv) != 2:
        print("usage: decode_pil.py <fixture>", file=sys.stderr)
        return 2
    path = Path(sys.argv[1])
    if not path.exists():
        print(f"not found: {path}", file=sys.stderr)
        return 2

    # HEIC bypasses PIL/pillow-heif — use system libheif via ctypes to match
    # the 5 ports' reference (libheif 1.17.6, not pillow-heif's 1.21.2).
    if path.suffix.lower() in (".heic", ".heif"):
        try:
            w, h, ch, px = decode_heic_via_system_libheif(path)
            emit(w, h, ch, px)
            return 0
        except Exception as e:
            print(f"heic decode failed: {e}", file=sys.stderr)
            return 1

    # All other formats: PIL/Pillow.
    try:
        img = Image.open(path)
        mode = "RGBA" if "A" in img.mode else "RGB"
        img = img.convert(mode)
        channels = 4 if mode == "RGBA" else 3
        emit(img.width, img.height, channels, img.tobytes())
        return 0
    except Exception as e:
        print(f"PIL decode failed: {e}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
