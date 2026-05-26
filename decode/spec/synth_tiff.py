"""Synthesize 10 baseline TIFF fixtures via PIL (libtiff-backed)."""
from pathlib import Path
from PIL import Image

OUT = Path(__file__).parent / "fixtures" / "tiff" / "valid"
OUT.mkdir(parents=True, exist_ok=True)

def gradient_rgb(w, h):
    pixels = bytearray()
    for y in range(h):
        for x in range(w):
            pixels.extend([x*255//max(1,w-1), y*255//max(1,h-1), (x+y)*127//max(1,w+h-2)])
    return Image.frombytes("RGB", (w, h), bytes(pixels))

def main():
    # Uncompressed
    gradient_rgb(8, 8).save(OUT / "8x8-rgb-none.tif", compression="raw")
    gradient_rgb(16, 16).save(OUT / "16x16-rgb-none.tif", compression="raw")
    gradient_rgb(1, 1).save(OUT / "1x1-rgb.tif", compression="raw")
    # LZW
    gradient_rgb(8, 8).save(OUT / "8x8-rgb-lzw.tif", compression="tiff_lzw")
    gradient_rgb(16, 16).save(OUT / "16x16-rgb-lzw.tif", compression="tiff_lzw")
    # Grayscale
    gradient_rgb(8, 8).convert("L").save(OUT / "8x8-grayscale-none.tif", compression="raw")
    gradient_rgb(16, 16).convert("L").save(OUT / "16x16-grayscale-lzw.tif", compression="tiff_lzw")
    # Deflate
    gradient_rgb(8, 8).save(OUT / "8x8-rgb-deflate.tif", compression="tiff_deflate")
    # Rectangular
    gradient_rgb(24, 16).save(OUT / "rect-24x16-deflate.tif", compression="tiff_deflate")
    # Larger
    try:
        photo = Image.open(Path.home() / "rosetta-squint-hash/spec/fixtures/peppers.png").convert("RGB")
        photo.thumbnail((128, 128))
        photo.save(OUT / "larger-photo-128.tif", compression="tiff_lzw")
    except Exception:
        gradient_rgb(128, 128).save(OUT / "larger-photo-128.tif", compression="tiff_lzw")

    count = len(list(OUT.glob("*.tif")))
    print(f"wrote {count} TIFF fixtures to {OUT}")

if __name__ == "__main__":
    main()
