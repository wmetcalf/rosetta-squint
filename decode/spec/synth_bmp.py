"""Synthesize BMP fixtures across all variants we support.

Generates 27 .bmp files under fixtures/bmp/valid/. Each file is a small
test image (mostly 16x16) with a known pixel pattern, encoded in a
specific BMP variant. Run via:

    python synth_bmp.py

This is idempotent: re-running produces identical bytes.

PIL's `Image.save(format='BMP')` covers BI_RGB at most depths and
BI_BITFIELDS for 16-bit. For BI_RLE4, BI_RLE8, V4-header, V5-header,
and BI_BITFIELDS-32-bit-with-alpha, we hand-build the file.
"""
from __future__ import annotations

import struct
from pathlib import Path

from PIL import Image

OUT_DIR = Path(__file__).parent / "fixtures" / "bmp" / "valid"


def gradient_rgb(width: int, height: int) -> Image.Image:
    """Horizontal RGB gradient: red rises with x, blue rises with y."""
    pixels = bytearray(width * height * 3)
    for y in range(height):
        for x in range(width):
            i = (y * width + x) * 3
            pixels[i] = (x * 255) // max(1, width - 1)
            pixels[i + 1] = ((x + y) * 255) // max(1, width + height - 2) if (width + height - 2) > 0 else 0
            pixels[i + 2] = (y * 255) // max(1, height - 1)
    return Image.frombytes("RGB", (width, height), bytes(pixels))


def gradient_rgba(width: int, height: int, alpha_pattern: str = "diag") -> Image.Image:
    rgb = gradient_rgb(width, height)
    alpha = bytearray(width * height)
    for y in range(height):
        for x in range(width):
            if alpha_pattern == "diag":
                alpha[y * width + x] = ((x + y) * 255) // max(1, width + height - 2)
            elif alpha_pattern == "zero":
                alpha[y * width + x] = 0
            else:
                alpha[y * width + x] = 255
    rgba = Image.new("RGBA", (width, height))
    rgba.putdata([
        (rgb.getpixel((x, y))[0], rgb.getpixel((x, y))[1], rgb.getpixel((x, y))[2], alpha[y * width + x])
        for y in range(height) for x in range(width)
    ])
    return rgba


def checkerboard(width: int, height: int) -> Image.Image:
    """Black/white checkerboard."""
    pixels = bytearray(width * height)
    for y in range(height):
        for x in range(width):
            pixels[y * width + x] = 255 if ((x + y) % 2 == 0) else 0
    return Image.frombytes("L", (width, height), bytes(pixels))


def palette_image_8bit(width: int, height: int) -> Image.Image:
    """8-bit indexed image with a standard palette."""
    img = Image.new("P", (width, height))
    palette = []
    for i in range(256):
        palette.extend([i, i // 2, i // 3])
    img.putpalette(palette)
    pixels = bytearray(width * height)
    for y in range(height):
        for x in range(width):
            pixels[y * width + x] = (x * 16 + y) % 256
    img.putdata(list(pixels))
    return img


def write_pil_bmp(img: Image.Image, path: Path) -> None:
    img.save(path, format="BMP")


def write_bmp_24bit_topdown(img: Image.Image, path: Path) -> None:
    assert img.mode == "RGB"
    width, height = img.size
    row_stride = ((width * 3 + 3) // 4) * 4
    pixel_data = bytearray()
    for y in range(height):
        for x in range(width):
            r, g, b = img.getpixel((x, y))
            pixel_data.append(b)
            pixel_data.append(g)
            pixel_data.append(r)
        while len(pixel_data) % 4 != 0:
            pixel_data.append(0)
    image_size = row_stride * height
    file_size = 14 + 40 + image_size
    pixel_offset = 14 + 40
    file_header = struct.pack("<2sIHHI", b"BM", file_size, 0, 0, pixel_offset)
    info_header = struct.pack("<IiiHHIIiiII", 40, width, -height, 1, 24, 0, image_size, 2835, 2835, 0, 0)
    path.write_bytes(file_header + info_header + bytes(pixel_data))


def write_bmp_bitfields_16(img: Image.Image, path: Path, mask_555: bool) -> None:
    assert img.mode == "RGB"
    width, height = img.size
    row_stride = ((width * 2 + 3) // 4) * 4
    if mask_555:
        red_mask, green_mask, blue_mask = 0x7C00, 0x03E0, 0x001F
    else:
        red_mask, green_mask, blue_mask = 0xF800, 0x07E0, 0x001F

    def pack_pixel(r, g, b):
        if mask_555:
            return ((r >> 3) << 10) | ((g >> 3) << 5) | (b >> 3)
        return ((r >> 3) << 11) | ((g >> 2) << 5) | (b >> 3)

    pixel_data = bytearray()
    for y in range(height - 1, -1, -1):
        row = bytearray()
        for x in range(width):
            r, g, b = img.getpixel((x, y))
            row.extend(struct.pack("<H", pack_pixel(r, g, b)))
        while len(row) % 4 != 0:
            row.append(0)
        pixel_data.extend(row)
    image_size = row_stride * height
    pixel_offset = 14 + 52
    file_size = pixel_offset + image_size
    file_header = struct.pack("<2sIHHI", b"BM", file_size, 0, 0, pixel_offset)
    info_header = struct.pack("<IiiHHIIiiII", 40, width, height, 1, 16, 3, image_size, 2835, 2835, 0, 0)
    masks = struct.pack("<III", red_mask, green_mask, blue_mask)
    path.write_bytes(file_header + info_header + masks + bytes(pixel_data))


def write_bmp_bitfields_32(img: Image.Image, path: Path, abgr_order: bool = False) -> None:
    assert img.mode == "RGBA"
    width, height = img.size
    if abgr_order:
        red_mask, green_mask, blue_mask, alpha_mask = 0x000000FF, 0x0000FF00, 0x00FF0000, 0xFF000000
    else:
        red_mask, green_mask, blue_mask, alpha_mask = 0x00FF0000, 0x0000FF00, 0x000000FF, 0xFF000000

    def pack_pixel(r, g, b, a):
        if abgr_order:
            return r | (g << 8) | (b << 16) | (a << 24)
        return b | (g << 8) | (r << 16) | (a << 24)

    pixel_data = bytearray()
    for y in range(height - 1, -1, -1):
        for x in range(width):
            r, g, b, a = img.getpixel((x, y))
            pixel_data.extend(struct.pack("<I", pack_pixel(r, g, b, a)))
    image_size = width * height * 4
    pixel_offset = 14 + 56
    file_size = pixel_offset + image_size
    file_header = struct.pack("<2sIHHI", b"BM", file_size, 0, 0, pixel_offset)
    info_header = struct.pack("<IiiHHIIiiII", 56, width, height, 1, 32, 3, image_size, 2835, 2835, 0, 0)
    masks = struct.pack("<IIII", red_mask, green_mask, blue_mask, alpha_mask)
    path.write_bytes(file_header + info_header + masks + bytes(pixel_data))


def write_bmp_pal1(img: Image.Image, path: Path) -> None:
    assert img.mode == "1"
    width, height = img.size
    row_stride = ((width + 31) // 32) * 4
    pixel_data = bytearray()
    for y in range(height - 1, -1, -1):
        row = bytearray()
        cur_byte = 0
        bit_in_byte = 7
        for x in range(width):
            v = img.getpixel((x, y))
            if v:
                cur_byte |= 1 << bit_in_byte
            if bit_in_byte == 0:
                row.append(cur_byte)
                cur_byte = 0
                bit_in_byte = 7
            else:
                bit_in_byte -= 1
        if bit_in_byte != 7:
            row.append(cur_byte)
        while len(row) < row_stride:
            row.append(0)
        pixel_data.extend(row)
    palette_data = bytes([0, 0, 0, 0, 255, 255, 255, 0])
    image_size = row_stride * height
    pixel_offset = 14 + 40 + len(palette_data)
    file_size = pixel_offset + image_size
    file_header = struct.pack("<2sIHHI", b"BM", file_size, 0, 0, pixel_offset)
    info_header = struct.pack("<IiiHHIIiiII", 40, width, height, 1, 1, 0, image_size, 2835, 2835, 2, 0)
    path.write_bytes(file_header + info_header + palette_data + bytes(pixel_data))


def write_bmp_pal4(img: Image.Image, path: Path) -> None:
    assert img.mode == "P"
    width, height = img.size
    row_stride = ((width * 4 + 31) // 32) * 4
    palette_bytes = bytearray()
    for i in range(16):
        palette_bytes.extend([i * 17, i * 8, i * 4, 0])
    pixel_data = bytearray()
    for y in range(height - 1, -1, -1):
        row = bytearray()
        for x in range(0, width, 2):
            high = img.getpixel((x, y)) & 0x0F
            low = img.getpixel((x + 1, y)) & 0x0F if (x + 1) < width else 0
            row.append((high << 4) | low)
        while len(row) < row_stride:
            row.append(0)
        pixel_data.extend(row)
    image_size = row_stride * height
    pixel_offset = 14 + 40 + 64
    file_size = pixel_offset + image_size
    file_header = struct.pack("<2sIHHI", b"BM", file_size, 0, 0, pixel_offset)
    info_header = struct.pack("<IiiHHIIiiII", 40, width, height, 1, 4, 0, image_size, 2835, 2835, 16, 0)
    path.write_bytes(file_header + info_header + bytes(palette_bytes) + bytes(pixel_data))


def write_bmp_rle8_horizontal_stripes(path: Path, width: int, height: int) -> None:
    pixel_data = bytearray()
    for y in range(height - 1, -1, -1):
        stripe_color = (y * 16) % 256
        pixel_data.append(width)
        pixel_data.append(stripe_color)
        pixel_data.append(0)
        pixel_data.append(0)
    pixel_data.append(0)
    pixel_data.append(1)
    palette_bytes = bytearray()
    for i in range(256):
        palette_bytes.extend([i // 3, i // 2, i, 0])
    image_size = len(pixel_data)
    pixel_offset = 14 + 40 + len(palette_bytes)
    file_size = pixel_offset + image_size
    file_header = struct.pack("<2sIHHI", b"BM", file_size, 0, 0, pixel_offset)
    info_header = struct.pack("<IiiHHIIiiII", 40, width, height, 1, 8, 1, image_size, 2835, 2835, 256, 0)
    path.write_bytes(file_header + info_header + bytes(palette_bytes) + bytes(pixel_data))


def write_bmp_rle8_with_delta(path: Path) -> None:
    width, height = 16, 16
    pixel_data = bytearray()
    for y in range(height):
        if y == 0:
            pixel_data.extend([4, 1, 0, 2, 4, 0, 8, 2, 0, 0])
        else:
            pixel_data.extend([width, 1 if y % 2 == 0 else 2, 0, 0])
    pixel_data.extend([0, 1])
    palette_bytes = bytearray([0, 0, 0, 0, 0, 0, 255, 0, 255, 0, 0, 0])
    while len(palette_bytes) < 1024:
        palette_bytes.extend([0, 0, 0, 0])
    image_size = len(pixel_data)
    pixel_offset = 14 + 40 + len(palette_bytes)
    file_size = pixel_offset + image_size
    file_header = struct.pack("<2sIHHI", b"BM", file_size, 0, 0, pixel_offset)
    info_header = struct.pack("<IiiHHIIiiII", 40, width, height, 1, 8, 1, image_size, 2835, 2835, 256, 0)
    path.write_bytes(file_header + info_header + bytes(palette_bytes) + bytes(pixel_data))


def write_bmp_rle8_absolute(path: Path) -> None:
    width, height = 16, 16
    pixel_data = bytearray()
    for y in range(height):
        pixel_data.extend([8, (y * 8) % 256])
        pixel_data.append(0)
        pixel_data.append(8)
        for x in range(8, 16):
            pixel_data.append((x * 16 + y) % 256)
        pixel_data.append(0)
        pixel_data.append(0)
    pixel_data.extend([0, 1])
    palette_bytes = bytearray()
    for i in range(256):
        palette_bytes.extend([i // 3, i // 2, i, 0])
    image_size = len(pixel_data)
    pixel_offset = 14 + 40 + len(palette_bytes)
    file_size = pixel_offset + image_size
    file_header = struct.pack("<2sIHHI", b"BM", file_size, 0, 0, pixel_offset)
    info_header = struct.pack("<IiiHHIIiiII", 40, width, height, 1, 8, 1, image_size, 2835, 2835, 256, 0)
    path.write_bytes(file_header + info_header + bytes(palette_bytes) + bytes(pixel_data))


def write_bmp_rle4(path: Path) -> None:
    width, height = 16, 16
    pixel_data = bytearray()
    for y in range(height):
        idx_a = (y * 2) % 16
        idx_b = (y * 2 + 1) % 16
        pixel_data.append(width)
        pixel_data.append((idx_a << 4) | idx_b)
        pixel_data.extend([0, 0])
    pixel_data.extend([0, 1])
    palette_bytes = bytearray()
    for i in range(16):
        palette_bytes.extend([i * 17, i * 8, i * 4, 0])
    image_size = len(pixel_data)
    pixel_offset = 14 + 40 + len(palette_bytes)
    file_size = pixel_offset + image_size
    file_header = struct.pack("<2sIHHI", b"BM", file_size, 0, 0, pixel_offset)
    info_header = struct.pack("<IiiHHIIiiII", 40, width, height, 1, 4, 2, image_size, 2835, 2835, 16, 0)
    path.write_bytes(file_header + info_header + bytes(palette_bytes) + bytes(pixel_data))


def write_bmp_rle4_absolute(path: Path) -> None:
    width, height = 16, 16
    pixel_data = bytearray()
    for y in range(height):
        pixel_data.append(8)
        pixel_data.append((((y * 2) % 16) << 4) | ((y * 2 + 1) % 16))
        pixel_data.append(0)
        pixel_data.append(8)
        for i in range(4):
            high = ((i * 2 + 8) % 16)
            low = ((i * 2 + 9) % 16)
            pixel_data.append((high << 4) | low)
        pixel_data.extend([0, 0])
    pixel_data.extend([0, 1])
    palette_bytes = bytearray()
    for i in range(16):
        palette_bytes.extend([i * 17, i * 8, i * 4, 0])
    image_size = len(pixel_data)
    pixel_offset = 14 + 40 + len(palette_bytes)
    file_size = pixel_offset + image_size
    file_header = struct.pack("<2sIHHI", b"BM", file_size, 0, 0, pixel_offset)
    info_header = struct.pack("<IiiHHIIiiII", 40, width, height, 1, 4, 2, image_size, 2835, 2835, 16, 0)
    path.write_bytes(file_header + info_header + bytes(palette_bytes) + bytes(pixel_data))


def write_bmp_v4_header(img: Image.Image, path: Path) -> None:
    assert img.mode == "RGBA"
    width, height = img.size
    pixel_data = bytearray()
    for y in range(height - 1, -1, -1):
        for x in range(width):
            r, g, b, a = img.getpixel((x, y))
            pixel_data.extend([b, g, r, a])
    image_size = width * height * 4
    pixel_offset = 14 + 108
    file_size = pixel_offset + image_size
    file_header = struct.pack("<2sIHHI", b"BM", file_size, 0, 0, pixel_offset)
    info_part = struct.pack("<IiiHHIIiiII", 108, width, height, 1, 32, 0, image_size, 2835, 2835, 0, 0)
    masks = struct.pack("<IIII", 0x00FF0000, 0x0000FF00, 0x000000FF, 0xFF000000)
    cstype = struct.pack("<I", 0)
    endpoints = bytes(36)
    gamma = struct.pack("<III", 0, 0, 0)
    path.write_bytes(file_header + info_part + masks + cstype + endpoints + gamma + bytes(pixel_data))


def write_bmp_v5_header(img: Image.Image, path: Path) -> None:
    assert img.mode == "RGBA"
    width, height = img.size
    pixel_data = bytearray()
    for y in range(height - 1, -1, -1):
        for x in range(width):
            r, g, b, a = img.getpixel((x, y))
            pixel_data.extend([b, g, r, a])
    image_size = width * height * 4
    pixel_offset = 14 + 124
    file_size = pixel_offset + image_size
    file_header = struct.pack("<2sIHHI", b"BM", file_size, 0, 0, pixel_offset)
    info_part = struct.pack("<IiiHHIIiiII", 124, width, height, 1, 32, 0, image_size, 2835, 2835, 0, 0)
    masks = struct.pack("<IIII", 0x00FF0000, 0x0000FF00, 0x000000FF, 0xFF000000)
    cstype = struct.pack("<I", 0)
    endpoints = bytes(36)
    gamma = struct.pack("<III", 0, 0, 0)
    v5_tail = struct.pack("<IIII", 0, 0, 0, 0)
    path.write_bytes(file_header + info_part + masks + cstype + endpoints + gamma + v5_tail + bytes(pixel_data))


def write_bmp_v5_with_bitfields(img: Image.Image, path: Path) -> None:
    assert img.mode == "RGBA"
    width, height = img.size
    pixel_data = bytearray()
    for y in range(height - 1, -1, -1):
        for x in range(width):
            r, g, b, a = img.getpixel((x, y))
            pixel_data.extend(struct.pack("<I", b | (g << 8) | (r << 16) | (a << 24)))
    image_size = width * height * 4
    pixel_offset = 14 + 124
    file_size = pixel_offset + image_size
    file_header = struct.pack("<2sIHHI", b"BM", file_size, 0, 0, pixel_offset)
    info_part = struct.pack("<IiiHHIIiiII", 124, width, height, 1, 32, 3, image_size, 2835, 2835, 0, 0)
    masks = struct.pack("<IIII", 0x00FF0000, 0x0000FF00, 0x000000FF, 0xFF000000)
    cstype = struct.pack("<I", 0)
    endpoints = bytes(36)
    gamma = struct.pack("<III", 0, 0, 0)
    v5_tail = struct.pack("<IIII", 0, 0, 0, 0)
    path.write_bytes(file_header + info_part + masks + cstype + endpoints + gamma + v5_tail + bytes(pixel_data))


def main() -> None:
    OUT_DIR.mkdir(parents=True, exist_ok=True)

    # Tier 1
    write_pil_bmp(gradient_rgb(16, 16), OUT_DIR / "rgb24-bottom-up.bmp")
    write_bmp_24bit_topdown(gradient_rgb(16, 16), OUT_DIR / "rgb24-top-down.bmp")
    write_pil_bmp(gradient_rgb(5, 4), OUT_DIR / "rgb24-odd-width.bmp")
    write_pil_bmp(gradient_rgb(1, 1), OUT_DIR / "rgb24-1x1.bmp")
    write_pil_bmp(gradient_rgb(1, 16), OUT_DIR / "rgb24-1x16.bmp")
    write_pil_bmp(gradient_rgb(16, 1), OUT_DIR / "rgb24-16x1.bmp")
    write_pil_bmp(gradient_rgb(64, 64), OUT_DIR / "rgb24-large.bmp")
    write_pil_bmp(gradient_rgba(16, 16, "diag"), OUT_DIR / "rgba32-bgra.bmp")
    write_pil_bmp(gradient_rgba(16, 16, "zero"), OUT_DIR / "rgba32-no-alpha.bmp")
    write_pil_bmp(palette_image_8bit(16, 16), OUT_DIR / "pal8.bmp")
    p8_small = Image.new("P", (16, 16))
    small_palette = []
    for i in range(16):
        small_palette.extend([i * 16, i * 8, i * 4])
    p8_small.putpalette(small_palette)
    p8_small.putdata([(x + y) % 16 for y in range(16) for x in range(16)])
    p8_small.save(OUT_DIR / "pal8-small-palette.bmp", format="BMP")

    # Tier 2
    write_bmp_bitfields_16(gradient_rgb(16, 16), OUT_DIR / "bitfields16-565.bmp", mask_555=False)
    write_bmp_bitfields_16(gradient_rgb(16, 16), OUT_DIR / "bitfields16-555.bmp", mask_555=True)
    write_bmp_bitfields_32(gradient_rgba(16, 16, "diag"), OUT_DIR / "bitfields32-rgba.bmp", abgr_order=False)
    write_bmp_bitfields_32(gradient_rgba(16, 16, "diag"), OUT_DIR / "bitfields32-abgr.bmp", abgr_order=True)
    write_bmp_pal1(checkerboard(16, 16).convert("1"), OUT_DIR / "pal1.bmp")
    write_bmp_pal1(checkerboard(11, 8).convert("1"), OUT_DIR / "pal1-odd-width.bmp")
    p4 = Image.new("P", (16, 16))
    p4.putpalette([i * 17 for i in range(16) for _ in range(3)])
    p4.putdata([(x + y) % 16 for y in range(16) for x in range(16)])
    write_bmp_pal4(p4, OUT_DIR / "pal4.bmp")
    p4_odd = Image.new("P", (7, 8))
    p4_odd.putpalette([i * 17 for i in range(16) for _ in range(3)])
    p4_odd.putdata([(x + y) % 16 for y in range(8) for x in range(7)])
    write_bmp_pal4(p4_odd, OUT_DIR / "pal4-odd-width.bmp")

    # Tier 3
    write_bmp_rle8_horizontal_stripes(OUT_DIR / "rle8.bmp", 16, 16)
    write_bmp_rle8_with_delta(OUT_DIR / "rle8-with-delta.bmp")
    write_bmp_rle8_absolute(OUT_DIR / "rle8-absolute.bmp")
    write_bmp_rle4(OUT_DIR / "rle4.bmp")
    write_bmp_rle4_absolute(OUT_DIR / "rle4-absolute.bmp")
    write_bmp_v4_header(gradient_rgba(16, 16, "diag"), OUT_DIR / "v4-header.bmp")
    write_bmp_v5_header(gradient_rgba(16, 16, "diag"), OUT_DIR / "v5-header.bmp")
    write_bmp_v5_with_bitfields(gradient_rgba(16, 16, "diag"), OUT_DIR / "v5-with-bitfields.bmp")

    print(f"wrote {len(list(OUT_DIR.glob('*.bmp')))} BMP fixtures to {OUT_DIR}")


if __name__ == "__main__":
    main()
