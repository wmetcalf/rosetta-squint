"""Implementation of the rosetta_squint convenience API.

For most formats we use PIL.Image.open() because that's what upstream
`imagehash` itself uses, so we match imagehash's behavior exactly. For
HEIC specifically, we decode via a ctypes wrapper around system libheif
(NOT pillow-heif, which bundles libheif 1.21.2 and diverges ±1 px from
the system libheif 1.17.6 that the 5 native ports link to).
"""

from __future__ import annotations

import io
from pathlib import Path
from typing import Union

import imagehash
import rosetta_imagehash as rih
from PIL import Image

PathOrBytes = Union[str, Path, bytes, bytearray, memoryview]


# ─── Decode helpers ──────────────────────────────────────────────────────────


def _is_heic(path_or_first_bytes) -> bool:
    """Detect HEIC by ftyp box brand at offset 4..12 with brand in the
    HEIC-family set. Mirrors what the 5 native ports do."""
    if isinstance(path_or_first_bytes, (bytes, bytearray, memoryview)):
        b = bytes(path_or_first_bytes[:12])
    else:
        with open(path_or_first_bytes, "rb") as f:
            b = f.read(12)
    if len(b) < 12 or b[4:8] != b"ftyp":
        return False
    brand = b[8:12]
    return brand in (b"heic", b"heix", b"mif1", b"msf1", b"hevc", b"hevx")


def _decode_heic_via_system_libheif(data: bytes) -> Image.Image:
    """Decode HEIC bytes using ctypes around system libheif so the result
    matches the 5 native ports (which all link to system libheif via
    FFI). pillow-heif would bundle libheif 1.21.2 and diverge from
    system libheif 1.17.6 by ±1 px on lossy fixtures."""
    import ctypes

    lib = ctypes.CDLL("libheif.so.1")
    lib.heif_context_alloc.restype = ctypes.c_void_p
    lib.heif_context_free.argtypes = [ctypes.c_void_p]
    lib.heif_context_read_from_memory_without_copy.argtypes = [
        ctypes.c_void_p,
        ctypes.c_char_p,
        ctypes.c_size_t,
        ctypes.c_void_p,
    ]
    lib.heif_context_read_from_memory_without_copy.restype = ctypes.c_int64
    lib.heif_context_get_primary_image_handle.argtypes = [
        ctypes.c_void_p,
        ctypes.POINTER(ctypes.c_void_p),
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
        ctypes.c_void_p,
        ctypes.POINTER(ctypes.c_void_p),
        ctypes.c_int,
        ctypes.c_int,
        ctypes.c_void_p,
    ]
    lib.heif_decode_image.restype = ctypes.c_int64
    lib.heif_image_release.argtypes = [ctypes.c_void_p]
    lib.heif_image_get_plane_readonly.argtypes = [
        ctypes.c_void_p,
        ctypes.c_int,
        ctypes.POINTER(ctypes.c_int),
    ]
    lib.heif_image_get_plane_readonly.restype = ctypes.c_void_p

    HEIF_COLORSPACE_RGB = 1
    HEIF_CHROMA_INTERLEAVED_RGB = 10
    HEIF_CHROMA_INTERLEAVED_RGBA = 11
    HEIF_CHANNEL_INTERLEAVED = 10

    ctx = lib.heif_context_alloc()
    if not ctx:
        raise RuntimeError("heif_context_alloc failed")
    try:
        lib.heif_context_read_from_memory_without_copy(ctx, data, len(data), None)
        handle_ref = ctypes.c_void_p()
        lib.heif_context_get_primary_image_handle(ctx, ctypes.byref(handle_ref))
        if not handle_ref.value:
            raise RuntimeError("heif_context_get_primary_image_handle failed")
        handle = handle_ref.value
        try:
            width = lib.heif_image_handle_get_width(handle)
            height = lib.heif_image_handle_get_height(handle)
            has_alpha = lib.heif_image_handle_has_alpha_channel(handle) != 0
            chroma = (
                HEIF_CHROMA_INTERLEAVED_RGBA
                if has_alpha
                else HEIF_CHROMA_INTERLEAVED_RGB
            )
            mode = "RGBA" if has_alpha else "RGB"
            channels = 4 if has_alpha else 3

            img_ref = ctypes.c_void_p()
            lib.heif_decode_image(
                handle, ctypes.byref(img_ref), HEIF_COLORSPACE_RGB, chroma, None
            )
            if not img_ref.value:
                raise RuntimeError("heif_decode_image failed")
            img = img_ref.value
            try:
                stride = ctypes.c_int(0)
                plane = lib.heif_image_get_plane_readonly(
                    img, HEIF_CHANNEL_INTERLEAVED, ctypes.byref(stride)
                )
                if not plane:
                    raise RuntimeError("null plane")
                row_bytes = width * channels
                pixels = bytearray()
                for y in range(height):
                    row = ctypes.string_at(plane + y * stride.value, row_bytes)
                    pixels.extend(row)
                return Image.frombytes(mode, (width, height), bytes(pixels))
            finally:
                lib.heif_image_release(img)
        finally:
            lib.heif_image_handle_release(handle)
    finally:
        lib.heif_context_free(ctx)


def decode_file(path: Union[str, Path]) -> Image.Image:
    """Decode a file at `path` into a PIL.Image suitable for hashing.
    HEIC uses the system-libheif ctypes wrapper; everything else uses
    PIL.Image.open."""
    if _is_heic(path):
        return _decode_heic_via_system_libheif(Path(path).read_bytes())
    return Image.open(path)


def decode_bytes(data: bytes) -> Image.Image:
    """Decode raw image bytes into a PIL.Image. HEIC bytes use the
    ctypes wrapper around system libheif; everything else goes through
    PIL.Image.open."""
    if isinstance(data, (bytearray, memoryview)):
        data = bytes(data)
    if _is_heic(data):
        return _decode_heic_via_system_libheif(data)
    return Image.open(io.BytesIO(data))


# ─── Convenience hash functions ──────────────────────────────────────────────
# Each algorithm gets a (path, size) and a (_bytes, size) variant.


def average_hash(path: Union[str, Path], hash_size: int = 8) -> imagehash.ImageHash:
    return rih.average_hash(decode_file(path), hash_size=hash_size)


def average_hash_bytes(data: bytes, hash_size: int = 8) -> imagehash.ImageHash:
    return rih.average_hash(decode_bytes(data), hash_size=hash_size)


def phash(
    path: Union[str, Path],
    hash_size: int = 8,
    highfreq_factor: int = 4,
) -> imagehash.ImageHash:
    return rih.phash(
        decode_file(path), hash_size=hash_size, highfreq_factor=highfreq_factor
    )


def phash_bytes(
    data: bytes, hash_size: int = 8, highfreq_factor: int = 4
) -> imagehash.ImageHash:
    return rih.phash(
        decode_bytes(data), hash_size=hash_size, highfreq_factor=highfreq_factor
    )


def phash_simple(
    path: Union[str, Path],
    hash_size: int = 8,
    highfreq_factor: int = 4,
) -> imagehash.ImageHash:
    return rih.phash_simple(
        decode_file(path), hash_size=hash_size, highfreq_factor=highfreq_factor
    )


def phash_simple_bytes(
    data: bytes, hash_size: int = 8, highfreq_factor: int = 4
) -> imagehash.ImageHash:
    return rih.phash_simple(
        decode_bytes(data), hash_size=hash_size, highfreq_factor=highfreq_factor
    )


def dhash(path: Union[str, Path], hash_size: int = 8) -> imagehash.ImageHash:
    return rih.dhash(decode_file(path), hash_size=hash_size)


def dhash_bytes(data: bytes, hash_size: int = 8) -> imagehash.ImageHash:
    return rih.dhash(decode_bytes(data), hash_size=hash_size)


def dhash_vertical(
    path: Union[str, Path], hash_size: int = 8
) -> imagehash.ImageHash:
    return rih.dhash_vertical(decode_file(path), hash_size=hash_size)


def dhash_vertical_bytes(data: bytes, hash_size: int = 8) -> imagehash.ImageHash:
    return rih.dhash_vertical(decode_bytes(data), hash_size=hash_size)


def whash_haar(
    path: Union[str, Path], hash_size: int = 8
) -> imagehash.ImageHash:
    return rih.whash(
        decode_file(path),
        hash_size=hash_size,
        mode="haar",
        remove_max_haar_ll=True,
    )


def whash_haar_bytes(data: bytes, hash_size: int = 8) -> imagehash.ImageHash:
    return rih.whash(
        decode_bytes(data),
        hash_size=hash_size,
        mode="haar",
        remove_max_haar_ll=True,
    )


def whash_db4(path: Union[str, Path], hash_size: int = 8) -> imagehash.ImageHash:
    return rih.whash(
        decode_file(path),
        hash_size=hash_size,
        mode="db4",
        remove_max_haar_ll=True,
    )


def whash_db4_bytes(data: bytes, hash_size: int = 8) -> imagehash.ImageHash:
    return rih.whash(
        decode_bytes(data),
        hash_size=hash_size,
        mode="db4",
        remove_max_haar_ll=True,
    )


def whash_db4_robust(
    path: Union[str, Path], hash_size: int = 8
) -> imagehash.ImageHash:
    return rih.whash_db4_robust(decode_file(path), hash_size=hash_size)


def whash_db4_robust_bytes(
    data: bytes, hash_size: int = 8
) -> imagehash.ImageHash:
    return rih.whash_db4_robust(decode_bytes(data), hash_size=hash_size)


def colorhash(path: Union[str, Path], binbits: int = 3) -> imagehash.ImageHash:
    return rih.colorhash(decode_file(path), binbits=binbits)


def colorhash_bytes(data: bytes, binbits: int = 3) -> imagehash.ImageHash:
    return rih.colorhash(decode_bytes(data), binbits=binbits)


def crop_resistant_hash(path: Union[str, Path]) -> imagehash.ImageMultiHash:
    return rih.crop_resistant_hash(decode_file(path))


def crop_resistant_hash_bytes(data: bytes) -> imagehash.ImageMultiHash:
    return rih.crop_resistant_hash(decode_bytes(data))
