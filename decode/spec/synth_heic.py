"""Synthesize HEIC fixtures via pillow-heif (libheif-backed)."""
from pathlib import Path
from PIL import Image
import pillow_heif

pillow_heif.register_heif_opener()

OUT = Path(__file__).parent / "fixtures" / "heic" / "valid"
OUT.mkdir(parents=True, exist_ok=True)


def grad_rgb(w, h):
    px = bytearray()
    for y in range(h):
        for x in range(w):
            px.extend([x * 255 // max(1, w - 1), y * 255 // max(1, h - 1), (x + y) * 127 // max(1, w + h - 2)])
    return Image.frombytes("RGB", (w, h), bytes(px))


def grad_rgba(w, h):
    px = bytearray()
    for y in range(h):
        for x in range(w):
            px.extend([x * 255 // max(1, w - 1), y * 255 // max(1, h - 1), 128, (x + y) * 255 // max(1, w + h - 2)])
    return Image.frombytes("RGBA", (w, h), bytes(px))


def main():
    # Lossy q variants
    grad_rgb(32, 32).save(OUT / "32x32-q50.heic", format="HEIF", quality=50)
    grad_rgb(32, 32).save(OUT / "32x32-q90.heic", format="HEIF", quality=90)
    grad_rgb(64, 64).save(OUT / "64x64-q90.heic", format="HEIF", quality=90)
    grad_rgb(128, 96).save(OUT / "larger-128x96.heic", format="HEIF", quality=80)
    # Square edge
    grad_rgb(16, 16).save(OUT / "16x16.heic", format="HEIF", quality=90)
    # Rect aspects
    grad_rgb(24, 16).save(OUT / "rect-24x16.heic", format="HEIF", quality=90)
    # Smaller real-world-ish photo
    grad_rgb(96, 96).save(OUT / "photo-96.heic", format="HEIF", quality=85)
    # Alpha
    grad_rgba(32, 32).save(OUT / "32x32-rgba.heic", format="HEIF", quality=90)
    grad_rgba(16, 16).save(OUT / "16x16-rgba.heic", format="HEIF", quality=90)
    # Lossless (libheif supports lossless via x265 setting; quality=100 triggers lossless)
    grad_rgb(16, 16).save(OUT / "16x16-lossless.heic", format="HEIF", quality=100)

    count = len(list(OUT.glob("*.heic")))
    print(f"wrote {count} valid HEIC fixtures to {OUT}")


if __name__ == "__main__":
    main()
