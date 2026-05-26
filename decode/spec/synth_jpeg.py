"""Synthesize 15 JPEG fixtures via PIL (libjpeg-turbo-backed)."""
from pathlib import Path
from PIL import Image

OUT = Path(__file__).parent / "fixtures" / "jpeg" / "valid"
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


def main():
	# Quality variants
	for q in [10, 50, 95]:
		gradient_rgb(8, 8).save(OUT / f"8x8-quality-{q}.jpg", quality=q, subsampling=2 if q < 90 else 0)
	gradient_rgb(16, 16).save(OUT / "16x16-quality-95.jpg", quality=95, subsampling=0)
	gradient_rgb(16, 16).save(OUT / "16x16-quality-50.jpg", quality=50)
	gradient_rgb(32, 32).save(OUT / "32x32-quality-95.jpg", quality=95)
	gradient_rgb(64, 64).save(OUT / "64x64-quality-50.jpg", quality=50)

	# Grayscale
	gradient_rgb(8, 8).convert("L").save(OUT / "8x8-grayscale.jpg", quality=90)
	gradient_rgb(16, 16).convert("L").save(OUT / "16x16-grayscale.jpg", quality=90)

	# Subsampling
	gradient_rgb(8, 8).save(OUT / "8x8-444.jpg", quality=85, subsampling=0)
	gradient_rgb(8, 8).save(OUT / "8x8-422.jpg", quality=85, subsampling=1)
	gradient_rgb(8, 8).save(OUT / "8x8-420.jpg", quality=85, subsampling=2)

	# Progressive
	gradient_rgb(16, 16).save(OUT / "16x16-progressive.jpg", quality=85, progressive=True)

	# Irregular dimensions
	gradient_rgb(7, 11).save(OUT / "irregular-7x11.jpg", quality=85)

	# Larger photo
	try:
		photo = Image.open(Path.home() / "rosetta-squint-hash/spec/fixtures/peppers.png").convert("RGB")
		photo.thumbnail((128, 128))
		photo.save(OUT / "larger-photo-128.jpg", quality=85)
	except Exception:
		gradient_rgb(128, 128).save(OUT / "larger-photo-128.jpg", quality=85)

	count = len(list(OUT.glob("*.jpg")))
	print(f"wrote {count} JPEG fixtures to {OUT}")


if __name__ == "__main__":
	main()
