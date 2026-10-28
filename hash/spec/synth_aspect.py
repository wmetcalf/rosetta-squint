#!/usr/bin/env python3
"""Generate the extreme-aspect-ratio hash fixtures in fixtures/.

SPEC.md §Step 4 pins the Lanczos pass order (horizontal, then vertical), and
Pillow reorders the passes on tall inputs: Pillow <= 12.3 past 100:1, Pillow
>= 13 past ~2:1. None of the original fixtures is taller than 2:1, so the
goldens could not see either reorder. These fixtures cover both, plus the
wide mirror case and a narrow source whose width is upscaled at every hash
size.

Pixels are closed-form (no RNG), so the output does not depend on the numpy
version. Run from hash/spec/, then `python regenerate.py` to refresh
decoded/ and goldens.json.
"""

from __future__ import annotations

from pathlib import Path

import numpy as np
from PIL import Image

FIXTURES_DIR = Path(__file__).parent / "fixtures"

# name -> (width, height). The comments say which Pillow reorder each one trips.
SHAPES: dict[str, tuple[int, int]] = {
    "aspect-tall-3to1-96x288.png": (96, 288),       # Pillow >= 13 (past ~2:1)
    "aspect-tall-120to1-10x1200.png": (10, 1200),   # Pillow <= 12.3 (past 100:1) and >= 13
    "aspect-tall-240to1-5x1200.png": (5, 1200),     # as above; width upscaled at every size
    "aspect-wide-120to1-1200x10.png": (1200, 10),   # neither: wide inputs keep spec order
}


def _pixels(width: int, height: int) -> np.ndarray:
    """RGB pattern with structure along both axes, so every hash has bits to set."""
    y, x = np.mgrid[0:height, 0:width]
    r = (x * 53 + y * 7 + (x * y) // 11) % 256
    g = (x * 17 + (y * 3) // 2 + (x ^ y) % 29) % 256
    b = ((x + y) * 5 + (y // 37) * 41) % 256
    return np.dstack([r, g, b]).astype(np.uint8)


def main() -> None:
    for name, (width, height) in SHAPES.items():
        Image.fromarray(_pixels(width, height), mode="RGB").save(FIXTURES_DIR / name)
        print(f"wrote {name}")


if __name__ == "__main__":
    main()
