"""Pin the Lanczos resize to the spec's pass order, independent of Pillow.

SPEC.md §Step 4 defines the resize as a horizontal pass followed by a
vertical pass, each rounding to uint8. Pillow <= 12.3 always did that, so the
Python reference could call ``Image.resize`` directly. Pillow >= 13 runs the
vertical pass first whenever the height shrinks by more than twice as much as
the width, which changes the output for inputs taller than ~2:1 — and so
their hashes. ``_spec_resize`` issues the two passes explicitly to pin the
spec order on any Pillow version.

Two guards:

1. ``_spec_resize`` reproduces every ``spec/lanczos_cases/*.bin`` reference
   vector (the same bins every native port tests against).
2. A deterministic 300x900 input resized through ``_spec_resize`` matches
   sha256 values frozen under Pillow 12.3.0. No fixture in ``spec/`` is
   taller than 2:1, so the goldens alone would not catch a pass-order
   change; this does.
"""

from __future__ import annotations

import hashlib
import json
import struct
from pathlib import Path

import numpy as np
import pytest
from PIL import Image

from rosetta_squint_hash._impl import _spec_resize

REPO_ROOT = Path(__file__).resolve().parent.parent.parent
LANCZOS_DIR = REPO_ROOT / "spec" / "lanczos_cases"


def _lanczos_cases() -> list[str]:
    manifest = json.loads((LANCZOS_DIR / "MANIFEST.json").read_text())
    return [case["name"] for case in manifest["cases"]]


@pytest.mark.parametrize("name", _lanczos_cases())
def test_spec_resize_matches_lanczos_reference_bins(name: str):
    blob = (LANCZOS_DIR / f"{name}.bin").read_bytes()
    src_w, src_h, dst_w, dst_h = struct.unpack("<IIII", blob[:16])
    src = np.frombuffer(blob[16:16 + src_w * src_h], dtype=np.uint8).reshape((src_h, src_w))
    expected = np.frombuffer(blob[16 + src_w * src_h:], dtype=np.uint8).reshape((dst_h, dst_w))
    assert expected.size == dst_w * dst_h

    got = np.asarray(_spec_resize(Image.fromarray(src, mode="L"), (dst_w, dst_h)))
    assert np.array_equal(got, expected), f"{name}: _spec_resize diverged from reference bin"


def _tall_input() -> Image.Image:
    """300x900 'L' image (3:1 portrait) from a closed-form pattern — no RNG,
    so the pixels are identical on every numpy version."""
    y, x = np.mgrid[0:900, 0:300]
    return Image.fromarray(((x * 7 + y * 13 + (x * y) // 37) % 256).astype(np.uint8), mode="L")


# sha256 of the uint8 output buffer, frozen under Pillow 12.3.0 (spec order).
TALL_REFERENCE = {
    (9, 8): "d45c787e82e3a8d7ed37c344125dcc4788bb5b0262c98c526b3cc2dbf7bb436f",
    (8, 9): "b9eec45b17b0a6a4f6d2307c179f7e4bf6b877918c00910df28db83fa0c39c3d",
    (32, 32): "ed7fd456686280eac747381ddf4ee0d755c2d74d9184aab61b5c1ee1d6ea38a3",
    (300, 300): "21de1a0b71ebf19526b6a5660f9705785e9652c3dfc0cd3b054a50bdb9f05c50",
}


@pytest.mark.parametrize("size", sorted(TALL_REFERENCE))
def test_spec_resize_tall_input_is_pillow_independent(size: tuple[int, int]):
    out = np.asarray(_spec_resize(_tall_input(), size))
    assert out.shape == (size[1], size[0])
    assert hashlib.sha256(out.tobytes()).hexdigest() == TALL_REFERENCE[size], (
        f"_spec_resize {size} on a 3:1 input no longer matches the Pillow 12.3.0 "
        "reference — the installed Pillow changed its per-pass Lanczos arithmetic"
    )
