"""Synthesize PNG fixtures for rosetta-squint-decode.

Copies the 21 proven PNG fixtures from rosetta-squint-hash/spec/fixtures/
and generates 5 hand-crafted edge fixtures via PIL.

Run:
    python synth_png.py
"""
from __future__ import annotations

import shutil
from pathlib import Path
from PIL import Image

IMAGEHASH_FIXTURES = Path.home() / "rosetta-squint-hash" / "spec" / "fixtures"
OUT_DIR = Path(__file__).parent / "fixtures" / "png" / "valid"


def copy_imagehash_pngs() -> int:
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    count = 0
    for src in sorted(IMAGEHASH_FIXTURES.glob("*.png")):
        dst = OUT_DIR / src.name
        shutil.copyfile(src, dst)
        count += 1
    return count


def make_1x1_rgb() -> None:
    img = Image.new("RGB", (1, 1), (255, 0, 0))
    img.save(OUT_DIR / "edge-1x1-rgb.png", format="PNG")


def make_1x1_rgba() -> None:
    img = Image.new("RGBA", (1, 1), (255, 0, 0, 128))
    img.save(OUT_DIR / "edge-1x1-rgba.png", format="PNG")


def make_large_rgba() -> None:
    pixels = bytearray()
    for y in range(256):
        for x in range(256):
            pixels.extend([x, (x + y) % 256, y, (x * y // 256) % 256])
    img = Image.frombytes("RGBA", (256, 256), bytes(pixels))
    img.save(OUT_DIR / "edge-large-rgba.png", format="PNG")


def make_odd_width() -> None:
    pixels = bytearray()
    for y in range(4):
        for x in range(7):
            pixels.extend([x * 36, y * 64, 128])
    img = Image.frombytes("RGB", (7, 4), bytes(pixels))
    img.save(OUT_DIR / "edge-odd-width.png", format="PNG")


def make_gray_alpha_zero() -> None:
    pixels = bytearray()
    for i in range(16 * 16):
        pixels.extend([200, 0])  # gray=200, alpha=0
    img = Image.frombytes("LA", (16, 16), bytes(pixels))
    img.save(OUT_DIR / "edge-gray-alpha-zero.png", format="PNG")


def main() -> None:
    copied = copy_imagehash_pngs()
    make_1x1_rgb()
    make_1x1_rgba()
    make_large_rgba()
    make_odd_width()
    make_gray_alpha_zero()
    total = len(list(OUT_DIR.glob("*.png")))
    print(f"copied {copied} imagehash PNGs + 5 edge fixtures, {total} total in {OUT_DIR}")


if __name__ == "__main__":
    main()
