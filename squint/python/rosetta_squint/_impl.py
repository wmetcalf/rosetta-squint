"""Implementation of the rosetta_squint convenience API.

For most formats we use PIL.Image.open() because that's what upstream
`imagehash` itself uses, so we match imagehash's behavior exactly. For
HEIC specifically, we decode via a ctypes wrapper around system libheif
(NOT pillow-heif, which bundles libheif 1.21.2 and diverges ±1 px from
the system libheif 1.17.6 that the 5 native ports link to).
"""

from __future__ import annotations

import ctypes
import ctypes.util
import io
import os
import stat
import sys
from pathlib import Path
from typing import Union

import imagehash
import rosetta_squint_hash as rih
from PIL import Image


# Reject path-based decode of files that are too large or are non-regular
# (e.g., /dev/zero, named pipes, character devices) BEFORE reading bytes.
# Callers that genuinely need to process images larger than this threshold
# should decode via rosetta-squint-decode directly after explicit validation.
MAX_FILE_SIZE = 256 * 1024 * 1024  # 256 MiB


def _load_libheif_xplat() -> ctypes.CDLL:
    """Cross-platform libheif loader.

    Linux:   libheif.so.1
    macOS:   libheif.dylib (Homebrew unversioned) or libheif.1.dylib
    Windows: libheif.dll / libheif-1.dll
    Other:   ctypes.util.find_library fallback

    Raises OSError with a clear message if no candidate loads.
    """
    if sys.platform == "darwin":
        candidates = ["libheif.dylib", "libheif.1.dylib"]
    elif sys.platform == "win32":
        candidates = ["libheif.dll", "libheif-1.dll"]
    else:
        candidates = ["libheif.so.1", "libheif.so"]
    # ctypes.util.find_library lets us pick up homebrew/macports/other paths
    found = ctypes.util.find_library("heif")
    if found:
        candidates.append(found)
    for name in candidates:
        try:
            return ctypes.CDLL(name)
        except OSError:
            continue
    raise OSError(
        f"libheif not found. Tried: {', '.join(candidates)}. "
        f"Install via your package manager (apt install libheif-dev, "
        f"brew install libheif, etc.)."
    )

PathOrBytes = Union[str, Path, bytes, bytearray, memoryview]


class HeifError(ctypes.Structure):
    """C struct heif_error { int32 code; int32 subcode; const char* message; }.

    libheif's fallible functions return this 12-byte struct by value.
    Declaring restype = ctypes.c_int64 (the previous behaviour) only reads
    8 of those bytes and reinterprets them as an integer, which silently
    drops the message pointer and ignores the subcode.
    """
    _fields_ = (
        ("code", ctypes.c_int),
        ("subcode", ctypes.c_int),
        ("message", ctypes.c_char_p),
    )


def _check_heif(err: "HeifError", op: str) -> None:
    """Raise RuntimeError when a libheif error struct indicates failure."""
    if err.code != 0:
        msg = err.message.decode("utf-8", "replace") if err.message else ""
        raise RuntimeError(
            f"libheif {op} failed: code={err.code} subcode={err.subcode} msg={msg}"
        )


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
    lib = _load_libheif_xplat()
    lib.heif_context_alloc.restype = ctypes.c_void_p
    lib.heif_context_free.argtypes = [ctypes.c_void_p]
    lib.heif_context_read_from_memory_without_copy.argtypes = [
        ctypes.c_void_p,
        ctypes.c_char_p,
        ctypes.c_size_t,
        ctypes.c_void_p,
    ]
    lib.heif_context_read_from_memory_without_copy.restype = HeifError
    lib.heif_context_get_primary_image_handle.argtypes = [
        ctypes.c_void_p,
        ctypes.POINTER(ctypes.c_void_p),
    ]
    lib.heif_context_get_primary_image_handle.restype = HeifError
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
    lib.heif_decode_image.restype = HeifError
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
        err = lib.heif_context_read_from_memory_without_copy(
            ctx, data, len(data), None
        )
        _check_heif(err, "heif_context_read_from_memory_without_copy")
        handle_ref = ctypes.c_void_p()
        err = lib.heif_context_get_primary_image_handle(
            ctx, ctypes.byref(handle_ref)
        )
        # Register the cleanup BEFORE checking the error so that any
        # partial handle libheif may have written into handle_ref is
        # released even if _check_heif raises.
        try:
            _check_heif(err, "heif_context_get_primary_image_handle")
            if not handle_ref.value:
                raise RuntimeError("heif_context_get_primary_image_handle failed")
            handle = handle_ref.value
            width = lib.heif_image_handle_get_width(handle)
            height = lib.heif_image_handle_get_height(handle)
            if width <= 0 or height <= 0:
                raise RuntimeError(
                    f"invalid HEIC dimensions {width}x{height}"
                )
            has_alpha = lib.heif_image_handle_has_alpha_channel(handle) != 0
            chroma = (
                HEIF_CHROMA_INTERLEAVED_RGBA
                if has_alpha
                else HEIF_CHROMA_INTERLEAVED_RGB
            )
            mode = "RGBA" if has_alpha else "RGB"
            channels = 4 if has_alpha else 3

            img_ref = ctypes.c_void_p()
            err = lib.heif_decode_image(
                handle, ctypes.byref(img_ref), HEIF_COLORSPACE_RGB, chroma, None
            )
            # Same pattern: register cleanup BEFORE the error check so the
            # image is released even if _check_heif raises.
            try:
                _check_heif(err, "heif_decode_image")
                if not img_ref.value:
                    raise RuntimeError("heif_decode_image failed")
                img = img_ref.value
                stride = ctypes.c_int(0)
                plane = lib.heif_image_get_plane_readonly(
                    img, HEIF_CHANNEL_INTERLEAVED, ctypes.byref(stride)
                )
                if not plane:
                    raise RuntimeError("null plane")
                row_bytes = width * channels
                if stride.value < row_bytes:
                    raise RuntimeError(
                        f"libheif returned stride {stride.value} smaller than "
                        f"required {row_bytes} (width={width} channels={channels})"
                    )
                pixels = bytearray()
                for y in range(height):
                    row = ctypes.string_at(plane + y * stride.value, row_bytes)
                    pixels.extend(row)
                return Image.frombytes(mode, (width, height), bytes(pixels))
            finally:
                if img_ref.value:
                    lib.heif_image_release(img_ref.value)
        finally:
            if handle_ref.value:
                lib.heif_image_handle_release(handle_ref.value)
    finally:
        lib.heif_context_free(ctx)


def _read_path_safely(path: Union[str, Path]) -> bytes:
    """Open `path`, validate stat against the same fd that we read from
    (closing a TOCTOU window), enforce symlink + regular-file + size
    guards, and return the bytes. Mirrors the fd-based stat-and-read
    pattern used in the other 5 squint ports.

    A naive `os.stat(path)` then `open(path).read()` has a classic TOCTOU
    window: an attacker with write access along the path can swap the
    target between the two syscalls (file → symlink to /dev/zero, file →
    larger file, regular file → FIFO). Holding the same fd across stat
    and read defeats this — fstat reports the inode the read will actually
    consume.

    The open itself uses ``O_NOFOLLOW`` so that a symlink at ``path``
    causes the open to fail rather than silently resolving to whatever
    the symlink currently points at — closing a separate TOCTOU window
    on the symlink target itself. Callers who genuinely want symlink
    resolution must do it explicitly (e.g. ``Path(path).resolve()``)
    before calling this function. Windows has no ``O_NOFOLLOW`` flag;
    fall back to an ``lstat`` check.
    """
    if sys.platform == "win32":
        # Windows: pre-check with os.lstat. There's a narrow race
        # between the lstat and the open below, but Windows lacks
        # O_NOFOLLOW and the alternative (reparse-point flags) would
        # require ctypes wrapping of CreateFileW.
        try:
            st_link = os.lstat(path)
        except OSError as e:
            raise OSError(e.errno, f"lstat failed for {path}: {e.strerror}") from e
        if stat.S_ISLNK(st_link.st_mode):
            raise ValueError(f"symlink not allowed: {path}")
        fd = os.open(path, os.O_RDONLY | os.O_BINARY)  # type: ignore[attr-defined]
    else:
        try:
            fd = os.open(path, os.O_RDONLY | os.O_NOFOLLOW)
        except OSError as e:
            # ELOOP (40 on Linux, 62 on macOS) is what O_NOFOLLOW raises
            # when the final path component is a symlink. Translate to a
            # clearer error so callers can distinguish symlink rejection
            # from a generic "not a regular file" or I/O error.
            import errno as _errno
            if e.errno == _errno.ELOOP:
                raise ValueError(f"symlink not allowed: {path}") from e
            raise
    # Wrap the bare fd in a Python file object so close-on-GC is automatic.
    # If `os.fdopen` itself raises (e.g., MemoryError on a stressed system),
    # the fd would leak — close it explicitly to be safe.
    try:
        f = os.fdopen(fd, "rb")
    except BaseException:
        os.close(fd)
        raise
    try:
        st = os.fstat(f.fileno())
        if not stat.S_ISREG(st.st_mode):
            raise RuntimeError(f"not a regular file: {path}")
        if st.st_size > MAX_FILE_SIZE:
            raise RuntimeError(
                f"input file too large: {st.st_size} bytes "
                f"(max {MAX_FILE_SIZE} bytes / 256 MiB). For images above this "
                f"threshold, decode via rosetta-squint-decode directly after "
                f"explicit validation."
            )
        # Read up to MAX_FILE_SIZE+1 so we detect "file grew between fstat
        # and read" (e.g. concurrent writer appending). The +1 absence is
        # the contract: if we got more than MAX_FILE_SIZE bytes, reject.
        data = f.read(MAX_FILE_SIZE + 1)
        if len(data) > MAX_FILE_SIZE:
            raise RuntimeError(
                f"input file too large: {len(data)} bytes "
                f"(max {MAX_FILE_SIZE} bytes / 256 MiB). For images above this "
                f"threshold, decode via rosetta-squint-decode directly after "
                f"explicit validation."
            )
        return data
    finally:
        f.close()


def decode_file(path: Union[str, Path]) -> Image.Image:
    """Decode a file at `path` into a PIL.Image suitable for hashing.
    HEIC uses the system-libheif ctypes wrapper; everything else uses
    PIL.Image.open.

    Refuses symlinks (via ``O_NOFOLLOW`` on POSIX / ``lstat`` on Windows),
    non-regular files (FIFOs, /dev/zero, character devices, etc.) and
    files larger than MAX_FILE_SIZE BEFORE reading bytes. The
    regular-file and size checks run against the same fd as the read,
    closing the obvious TOCTOU window. Callers who genuinely want symlink
    resolution must do it explicitly (e.g. ``Path(path).resolve()``)
    before calling this function.
    """
    data = _read_path_safely(path)
    if _is_heic(data):
        return _decode_heic_via_system_libheif(data)
    return Image.open(io.BytesIO(data))


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
    # rih.whash_db4 is the port-local snap-applying override (NOT
    # rih.whash(mode='db4'), which forwards to upstream imagehash without
    # the snap-to-threshold tie-break). See spec/SPEC.md §"Threshold
    # tie-break".
    return rih.whash_db4(decode_file(path), hash_size=hash_size)


def whash_db4_bytes(data: bytes, hash_size: int = 8) -> imagehash.ImageHash:
    return rih.whash_db4(decode_bytes(data), hash_size=hash_size)


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
