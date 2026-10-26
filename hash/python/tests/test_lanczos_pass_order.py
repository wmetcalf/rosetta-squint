"""Lanczos pass order is fixed to horizontal-then-vertical (SPEC.md §"Step 4").

Pillow picks the two-pass order internally and Pillow 13 started running the
vertical pass first on tall resizes, changing hash output. The tall fixtures
below trip both Pillow 12's (>100:1) and Pillow 13's vertical-first paths, so
these goldens fail if any resize falls back to Pillow's own ordering.
"""

from __future__ import annotations

import json
from pathlib import Path

import numpy as np
import pytest
from PIL import Image

import rosetta_squint_hash as rih
from rosetta_squint_hash._impl import _lanczos_resize

SPEC = Path(__file__).resolve().parent.parent.parent / "spec"
GOLDENS = json.loads((SPEC / "goldens.json").read_text())["algorithms"]
TALL_FIXTURES = ["portrait-tall-256x768.png", "sliver-8x1024.png"]

SIZED = {
    "average_hash": rih.average_hash,
    "dhash": rih.dhash,
    "dhash_vertical": rih.dhash_vertical,
    "phash": rih.phash,
    "phash_simple": rih.phash_simple,
    "whash_haar": rih.whash,
    "whash_db4": rih.whash_db4,
    "whash_db4_robust": rih.whash_db4_robust,
}


def _cases() -> list[tuple[str, str, int, str]]:
    out = []
    for fixture in TALL_FIXTURES:
        for algo in SIZED:
            for size, want in GOLDENS[algo]["fixtures"][fixture].items():
                if want is not None:
                    out.append((fixture, algo, int(size), want))
    return out


@pytest.mark.parametrize("fixture,algo,size,want", _cases())
def test_tall_fixture_goldens(fixture: str, algo: str, size: int, want: str) -> None:
    img = Image.open(SPEC / "fixtures" / fixture)
    assert str(SIZED[algo](img, size)) == want


@pytest.mark.parametrize("fixture", TALL_FIXTURES)
def test_tall_fixture_crop_resistant(fixture: str) -> None:
    img = Image.open(SPEC / "fixtures" / fixture)
    want = GOLDENS["crop_resistant_hash"]["fixtures"][fixture]["default"]
    assert str(rih.crop_resistant_hash(img)) == want


@pytest.mark.parametrize("src,dst", [((28, 110), (9, 8)), ((7, 1000), (8, 8)), ((256, 64), (8, 8))])
def test_resize_is_horizontal_then_vertical(src: tuple[int, int], dst: tuple[int, int]) -> None:
    rng = np.random.default_rng(0)
    img = Image.fromarray(rng.integers(0, 256, (src[1], src[0]), dtype=np.uint8), "L")
    lanczos = Image.Resampling.LANCZOS
    expected = img.resize((dst[0], src[1]), lanczos).resize(dst, lanczos)
    assert np.array_equal(np.asarray(_lanczos_resize(img, dst)), np.asarray(expected))
