"""Synthesize 15 GIF fixtures via PIL."""
from pathlib import Path
from PIL import Image

OUT = Path(__file__).parent / "fixtures" / "gif" / "valid"
OUT.mkdir(parents=True, exist_ok=True)


def make_paletted(size, palette_colors, pattern_fn, transparent=False, transparent_idx=0, name="out.gif"):
    img = Image.new("P", size)
    palette_flat = []
    for c in palette_colors:
        palette_flat.extend(c)
    while len(palette_flat) < 768:
        palette_flat.append(0)
    img.putpalette(palette_flat)
    pixels = bytearray()
    for y in range(size[1]):
        for x in range(size[0]):
            pixels.append(pattern_fn(x, y, len(palette_colors)))
    img.putdata(list(pixels))
    if transparent:
        img.save(OUT / name, format="GIF", transparency=transparent_idx)
    else:
        img.save(OUT / name, format="GIF")


def gradient(x, y, n): return (x + y) % n
def checker(x, y, n): return (x + y) % 2
def solid(x, y, n): return 0
def text_like(x, y, n): return (x * 7 + y * 13) % n
def stripes(x, y, n): return y % n


def main():
    rgb_palette = [(i * 32, i * 16, i * 8) for i in range(8)]
    palette16 = [(i * 16, i * 8, i * 4) for i in range(16)]
    palette256 = [(i, i // 2, i // 3) for i in range(256)]

    # Basic opaque
    make_paletted((8, 8), rgb_palette, gradient, name="8x8-rgb-opaque.gif")
    make_paletted((16, 16), rgb_palette, gradient, name="16x16-rgb-opaque.gif")
    make_paletted((32, 32), rgb_palette, gradient, name="32x32-rgb-opaque.gif")
    make_paletted((1, 1), [(255, 0, 0)], solid, name="1x1-rgb.gif")
    # With transparency
    make_paletted((8, 8), rgb_palette, gradient, transparent=True, transparent_idx=0, name="8x8-transparent.gif")
    make_paletted((16, 16), rgb_palette, gradient, transparent=True, transparent_idx=3, name="16x16-transparent.gif")
    # Palette sizes
    make_paletted((8, 8), [(0, 0, 0), (255, 255, 255)], checker, name="8x8-pal2.gif")
    make_paletted((8, 8), palette16, gradient, name="8x8-pal16.gif")
    make_paletted((8, 8), palette256, text_like, name="8x8-pal256.gif")
    # Patterns
    make_paletted((16, 16), palette16, gradient, name="gradient-16.gif")
    make_paletted((8, 8), [(0, 0, 0), (255, 255, 255)], checker, name="checkerboard-8.gif")
    make_paletted((16, 16), [(255, 0, 0)], solid, name="solid-red-16.gif")
    make_paletted((32, 32), palette256, text_like, name="text-mock-32.gif")
    make_paletted((24, 16), palette16, stripes, name="rect-24x16.gif")

    # Multi-frame
    frames = []
    for i in range(3):
        img = Image.new("P", (16, 16))
        palette = []
        for j in range(256):
            palette.extend([j * 16 % 256, j * 8 % 256, j * 4 % 256])
        img.putpalette(palette)
        img.putdata([(x + y + i * 4) % 16 for y in range(16) for x in range(16)])
        frames.append(img)
    frames[0].save(OUT / "multi-frame.gif", format="GIF", save_all=True,
                   append_images=frames[1:], duration=100, loop=0)

    count = len(list(OUT.glob("*.gif")))
    print(f"wrote {count} GIF fixtures to {OUT}")


if __name__ == "__main__":
    main()
