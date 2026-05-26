"""Group-2 byte-exact tests: rosetta_squint_hash.whash_db4_robust output
matches spec/goldens.json on all 21 fixtures × 2 sizes. The goldens are
generated from this same Python implementation (via spec/regenerate.py),
so this is a self-consistency check.

The value of running this test is that the other 5 ports (Rust, Go, Java,
JS, Swift) test against the same goldens — so all 6 implementations
agreeing means cross-port stability.
"""

from __future__ import annotations

import json
import struct
from pathlib import Path

import numpy as np
import pytest
from PIL import Image

import rosetta_squint_hash as rih


REPO_ROOT = Path(__file__).resolve().parent.parent.parent
SPEC = REPO_ROOT / "spec"
GOLDENS_PATH = SPEC / "goldens.json"
FIXTURES_DIR = SPEC / "fixtures"
DECODED_DIR = SPEC / "decoded"


def _load_goldens() -> dict:
    return json.loads(GOLDENS_PATH.read_text())


def _load_predecoded(fixture_name: str) -> Image.Image:
    """Read the spec/decoded/<fixture>.rgb.bin and reconstruct a PIL Image."""
    path = DECODED_DIR / f"{fixture_name}.rgb.bin"
    blob = path.read_bytes()
    w, h = struct.unpack("<II", blob[:8])
    rgb = np.frombuffer(blob[8:], dtype=np.uint8).reshape((h, w, 3))
    return Image.fromarray(rgb, mode="RGB")


def _algorithm_cases(algo_name: str) -> list[tuple[str, int, str]]:
    goldens = _load_goldens()
    algo = goldens["algorithms"][algo_name]
    cases: list[tuple[str, int, str]] = []
    for fixture, sizes in sorted(algo["fixtures"].items()):
        for size_str, hex_str in sorted(sizes.items(), key=lambda kv: int(kv[0])):
            if hex_str is None:
                continue  # fixtures too small for this size
            cases.append((fixture, int(size_str), hex_str))
    return cases


@pytest.mark.parametrize(
    "fixture,size,expected_hex",
    _algorithm_cases("whash_db4_robust"),
    ids=lambda v: str(v),
)
def test_whash_db4_robust_matches_goldens(fixture: str, size: int, expected_hex: str):
    """Every fixture × size produces the exact hex committed in goldens.json."""
    img = _load_predecoded(fixture)
    h = rih.whash_db4_robust(img, hash_size=size)
    assert str(h) == expected_hex, (
        f"fixture={fixture} size={size}\n"
        f"  got      {h}\n"
        f"  expected {expected_hex}"
    )


def test_strict_and_robust_match_on_real_photo():
    """For a real photograph, whash and whash_db4_robust must agree.
    Demonstrates that the snap-to-zero step does NOT affect non-pathological
    inputs — only pathological synthetic patterns trigger it."""
    img = _load_predecoded("peppers.png")
    h_strict = rih.whash(img, mode="db4")
    h_robust = rih.whash_db4_robust(img)
    assert str(h_strict) == str(h_robust), (
        f"real photo whash output disagreed:\n"
        f"  strict {h_strict}\n"
        f"  robust {h_robust}"
    )


def test_strict_and_robust_differ_on_checker():
    """For a perfectly symmetric input (checker pattern), the strict and
    robust variants should produce different hashes. The strict variant's
    output depends on PyWavelets' SIMD path; the robust variant's output is
    cross-port stable."""
    img = _load_predecoded("checker-256.png")
    h_strict = rih.whash(img, hash_size=16, mode="db4")
    h_robust = rih.whash_db4_robust(img, hash_size=16)
    assert str(h_strict) != str(h_robust), (
        "Expected strict and robust to disagree on the checker pattern; "
        "if they agree, the snap-to-zero step is not actually doing anything "
        "and the test is meaningless."
    )


def test_eps_constant():
    """Ensure the epsilon constant is the same value used by the 5 other
    ports (1e-12). Hardcoded — must NOT vary per-call."""
    assert rih.WHASH_DB4_ROBUST_EPS == 1e-12
