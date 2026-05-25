"""Synthesize ``too-large.*`` error fixtures for the 5 formats missing them.

Spec §3.1 requires every format to ship at least one fixture that triggers the
``imageTooLarge`` error path. BMP and PNG already have one; GIF/HEIC/JPEG/TIFF/WebP
do not. Each fixture below declares dimensions far above ``MAX_PIXELS = 256MP``
(typically 65535×65535 = 4.3GP) with a minimal payload.

Strategy: for formats whose native libraries refuse to parse a header-only fixture
(libjpeg, libwebp, libheif), we take a known-good small fixture and patch the
header's dimension fields. The library parses the (patched) header, returns the
oversized dimensions, and the ``MAX_PIXELS`` guard fires before any raster
allocation. Synthesized headers from scratch work for GIF and TIFF because their
dimension fields live at fixed offsets and the libraries don't perform a
container/bitstream sanity check before exposing dimensions.

Run:

    python synth_too_large.py

Idempotent: re-running produces identical bytes.
"""
from __future__ import annotations

import struct
from pathlib import Path

OUT_ROOT = Path(__file__).parent / "fixtures"
VALID = OUT_ROOT  # base for ``<fmt>/valid/`` source files


def write_gif() -> Path:
    """Minimal GIF89a with LSD declaring 65535×65535 canvas (~4.3 GP).

    GIF's Logical Screen Descriptor sits at a fixed offset (6..10). All five
    ports read it before invoking the underlying GIF decoder, so a header-only
    file is enough to drive the size check.
    """
    out = OUT_ROOT / "gif" / "invalid" / "too-large.gif"
    data = bytearray()
    data += b"GIF89a"              # magic (6 bytes)
    data += struct.pack("<HH", 65535, 65535)  # LSD width, height (LE u16)
    data += b"\x00"                # packed (no GCT, no BG, no aspect)
    data += b"\x00"                # bg color index
    data += b"\x00"                # pixel aspect ratio
    data += b"\x3B"                # trailer
    out.write_bytes(bytes(data))
    return out


def write_tiff() -> Path:
    """Minimal little-endian TIFF declaring 65535×65535 via IFD tags.

    TIFF's IFD entries (ImageWidth=0x0100, ImageLength=0x0101) live at a
    known offset; libtiff / image::tiff / Go's tiff package all parse the
    IFD before decoding strips. We include StripOffsets (0x0111),
    StripByteCounts (0x0117), and RowsPerStrip (0x0116) because stricter
    parsers reject IFDs without them as ``corruptInput`` *before* exposing
    ImageWidth/Length — which would defeat the spec-mandated ``imageTooLarge``
    classification. The strip pointer is junk (offset 0, length 0), but the
    dimension sniffer fires first so the decoder never reaches the strip.
    """
    out = OUT_ROOT / "tiff" / "invalid" / "too-large.tif"
    # 5 IFD entries × 12 bytes = 60 bytes after the entry-count word.
    # Plus 2-byte entry-count + 4-byte next-IFD-offset = 66 bytes after
    # the 8-byte file header.
    data = bytearray()
    data += b"II"                                       # little-endian byte order
    data += struct.pack("<H", 42)                       # magic
    data += struct.pack("<I", 8)                        # offset to first IFD = 8
    data += struct.pack("<H", 5)                        # 5 entries
    data += struct.pack("<HHII", 0x0100, 4, 1, 65535)   # ImageWidth (LONG)
    data += struct.pack("<HHII", 0x0101, 4, 1, 65535)   # ImageLength (LONG)
    data += struct.pack("<HHII", 0x0111, 4, 1, 0)       # StripOffsets (LONG) = 0
    data += struct.pack("<HHII", 0x0116, 4, 1, 65535)   # RowsPerStrip (LONG) = full height
    data += struct.pack("<HHII", 0x0117, 4, 1, 0)       # StripByteCounts (LONG) = 0
    data += struct.pack("<I", 0)                        # next IFD offset = 0
    out.write_bytes(bytes(data))
    return out


def write_jpeg() -> Path:
    """Patch the SOF0 dimension fields of a valid 8×8 JPEG to declare 65535×65535.

    libjpeg's ``jpeg_read_header`` parses markers in stream order; the SOF0
    marker populates ``cinfo.image_width / image_height`` before any pixel
    allocation. By keeping the rest of the bitstream intact (valid DQT/DHT/SOS
    referring to the original 8×8 image), the underlying decoder accepts the
    file as well-formed up to the point of the size check — at which point
    the rosetta-side ``MAX_PIXELS`` guard fires with ``imageTooLarge``.
    """
    src = VALID / "jpeg" / "valid" / "8x8-quality-95.jpg"
    out = OUT_ROOT / "jpeg" / "invalid" / "too-large.jpg"
    data = bytearray(src.read_bytes())
    # Find SOF0 (0xFFC0). JPEG markers are byte-aligned 0xFF NN sequences;
    # SOF0 lives near the front, before the first SOS marker.
    sof_offset = -1
    i = 2  # skip SOI
    while i + 1 < len(data):
        if data[i] != 0xFF:
            break
        marker = data[i + 1]
        # SOI (D8), EOI (D9), TEM (01), RST0..7 (D0..D7) have no length field
        if marker in (0xD9, 0x01) or 0xD0 <= marker <= 0xD7:
            i += 2
            continue
        if marker in (0xC0, 0xC1, 0xC2, 0xC3, 0xC5, 0xC6, 0xC7,
                      0xC9, 0xCA, 0xCB, 0xCD, 0xCE, 0xCF):
            sof_offset = i
            break
        if i + 3 >= len(data):
            break
        seg_len = (data[i + 2] << 8) | data[i + 3]
        i += 2 + seg_len
    if sof_offset < 0:
        raise RuntimeError(f"no SOF marker found in {src}")
    # libjpeg-turbo enforces JPEG_MAX_DIMENSION = 65500 in jpeg_read_header
    # (any side bigger than that errors out before our rosetta-side dim check
    # has a chance to fire). Pick dimensions that exceed MAX_PIXELS = 256M but
    # stay under that per-side cap: 20000 × 20000 = 400M > 256M. ✓
    # SOF segment: marker(2) + length(2) + precision(1) + height(2 BE) + width(2 BE)
    data[sof_offset + 5:sof_offset + 7] = struct.pack(">H", 20000)
    data[sof_offset + 7:sof_offset + 9] = struct.pack(">H", 20000)
    out.write_bytes(bytes(data))
    return out


def write_webp() -> Path:
    """Patch a VP8X WebP's declared canvas size to ~16M × 16M.

    VP8X is the WebP "extended" container variant; canvas dimensions live as
    24-bit ``width - 1`` and ``height - 1`` LE fields in the VP8X chunk
    payload. libwebp's ``WebPGetFeatures`` returns these values directly, so
    patching them is sufficient to drive the size check without invalidating
    the bitstream the following chunks describe.
    """
    src = VALID / "webp" / "valid" / "16x16-rgba-lossy-q90.webp"
    out = OUT_ROOT / "webp" / "invalid" / "too-large.webp"
    data = bytearray(src.read_bytes())
    # RIFF header (12 bytes: "RIFF" + size + "WEBP"), then chunk fourcc at 12..16.
    if bytes(data[12:16]) != b"VP8X":
        raise RuntimeError(f"{src} is not a VP8X WebP; cannot patch dimensions")
    # VP8X payload: chunk header is 8 bytes (fourcc + size), so payload starts at 20.
    # Layout: 4 bytes flags + 3 bytes (width-1) LE + 3 bytes (height-1) LE.
    # libwebp enforces canvas_width * canvas_height < (1 << 32) in ParseVP8X
    # — go above that and it returns BITSTREAM_ERROR before we see the dims.
    # Pick 30000 × 9000 = 270M canvas pixels: comfortably above MAX_PIXELS
    # (256M) and comfortably below libwebp's 4G area cap.
    w_minus1 = 30000 - 1
    h_minus1 = 9000 - 1
    data[24] = w_minus1 & 0xFF
    data[25] = (w_minus1 >> 8) & 0xFF
    data[26] = (w_minus1 >> 16) & 0xFF
    data[27] = h_minus1 & 0xFF
    data[28] = (h_minus1 >> 8) & 0xFF
    data[29] = (h_minus1 >> 16) & 0xFF
    out.write_bytes(bytes(data))
    return out


def write_heic() -> Path:
    """Patch the existing 16×16 HEIC fixture's ``ispe`` box to declare 65535×65535.

    ``ispe`` (Image Spatial Extents) is a fixed-format box: 4 bytes
    version+flags, then 4 bytes width and 4 bytes height (big-endian u32).
    libheif's ``HeifContext`` + ``primary_image_handle`` return the ispe
    dimensions before invoking the HEVC decoder, so patching here drives the
    size check without invalidating the iloc/idat hierarchy.

    .. warning:: Multi-image HEIC limitation.
        For HEIC files containing multiple items (`hvc1` + thumbnails, image
        sequences, derived images, …) ``ipco`` contains an ``ispe`` per item
        in container order. ``bytes_in.find(b"ispe")`` returns the FIRST one,
        which is not necessarily the primary item's (the primary is the
        ``pitm``-referenced item; its ``ispe`` may appear later in the box
        stream). For the current single-image ``16x16.heic`` source this is
        unambiguous. To synthesize a too-large fixture from a multi-image
        HEIC, walk the box hierarchy (``meta`` → ``iprp`` → ``ipco`` plus
        ``pitm`` for the primary item id and ``ipma`` for that item's
        property indices) and pick the correct ispe.
    """
    src = VALID / "heic" / "valid" / "16x16.heic"
    out = OUT_ROOT / "heic" / "invalid" / "too-large.heic"
    bytes_in = src.read_bytes()
    idx = bytes_in.find(b"ispe")
    if idx < 0:
        raise RuntimeError(f"no ispe box in {src}; cannot synthesize too-large")
    payload_off = idx + 4 + 4  # skip "ispe" + version+flags
    patched = bytearray(bytes_in)
    # libheif enforces 32768 × 32768 max per side internally — exceed that
    # and we get SecurityLimitExceeded before reaching our dim check. Pick
    # 30000 × 9000 = 270M pixels: above MAX_PIXELS = 256M, below libheif's
    # per-side cap.
    patched[payload_off:payload_off + 4] = struct.pack(">I", 30000)
    patched[payload_off + 4:payload_off + 8] = struct.pack(">I", 9000)
    out.write_bytes(bytes(patched))
    return out


def main() -> None:
    written = []
    for fn in (write_gif, write_jpeg, write_tiff, write_webp, write_heic):
        p = fn()
        written.append(p)
        print(f"wrote {p.relative_to(OUT_ROOT.parent)} ({p.stat().st_size} bytes)")
    print(f"wrote {len(written)} too-large fixtures")


if __name__ == "__main__":
    main()
