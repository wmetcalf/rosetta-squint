"""Synthesize WebP fixtures via PIL (libwebp-backed)."""
from pathlib import Path
from PIL import Image

OUT = Path(__file__).parent / "fixtures" / "webp" / "valid"
OUT.mkdir(parents=True, exist_ok=True)


def gradient_rgb(w, h):
    pixels = bytearray()
    for y in range(h):
        for x in range(w):
            pixels.extend([
                x * 255 // max(1, w - 1),
                y * 255 // max(1, h - 1),
                (x + y) * 127 // max(1, w + h - 2),
            ])
    return Image.frombytes("RGB", (w, h), bytes(pixels))


def gradient_rgba(w, h):
    rgba = bytearray()
    for y in range(h):
        for x in range(w):
            rgba.extend([
                x * 255 // max(1, w - 1),
                y * 255 // max(1, h - 1),
                128,
                (x + y) * 255 // max(1, w + h - 2),
            ])
    return Image.frombytes("RGBA", (w, h), bytes(rgba))


def main():
    # Lossy
    gradient_rgb(16, 16).save(OUT / "16x16-lossy-q50.webp", quality=50, method=4)
    gradient_rgb(16, 16).save(OUT / "16x16-lossy-q90.webp", quality=90, method=4)
    gradient_rgb(32, 32).save(OUT / "32x32-lossy-q90.webp", quality=90, method=4)
    gradient_rgb(64, 64).save(OUT / "64x64-lossy-q90.webp", quality=90, method=4)
    # Lossless
    gradient_rgb(16, 16).save(OUT / "16x16-lossless.webp", lossless=True)
    gradient_rgb(32, 32).save(OUT / "32x32-lossless.webp", lossless=True)
    # With alpha
    gradient_rgba(16, 16).save(OUT / "16x16-rgba-lossless.webp", lossless=True)
    gradient_rgba(16, 16).save(OUT / "16x16-rgba-lossy-q90.webp", quality=90, method=4)
    # Edges
    gradient_rgb(1, 1).save(OUT / "1x1-lossy-q90.webp", quality=90)
    gradient_rgb(8, 8).save(OUT / "8x8-lossy-q90.webp", quality=90)
    gradient_rgb(24, 16).save(OUT / "rect-24x16-lossless.webp", lossless=True)
    gradient_rgb(128, 96).save(OUT / "larger-128x96-lossy.webp", quality=85)

    count = len(list(OUT.glob("*.webp")))
    print(f"wrote {count} WebP fixtures to {OUT}")


if __name__ == "__main__":
    main()
