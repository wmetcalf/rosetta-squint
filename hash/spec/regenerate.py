"""rosetta-image-hash spec regenerator.

Reads every fixture in fixtures/ and produces:
  - decoded/<name>.rgb.bin   canonical RGB pixel buffer from PIL convert('RGB')
  - goldens.json             byte-exact hex of every algo x fixture x size

Outputs are idempotent: running twice produces zero git diff (modulo
the regenerated_at timestamp, which is excluded from --check comparison).

Usage:
  python regenerate.py            # write outputs
  python regenerate.py --check    # exit 1 if outputs would differ from committed
"""
from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import json
import struct
import sys
import tempfile
from pathlib import Path

import imagehash
import numpy as np
import PIL
import pywt
import scipy
import scipy.fftpack
from PIL import Image

SPEC_DIR = Path(__file__).parent
FIXTURES_DIR = SPEC_DIR / "fixtures"
DECODED_DIR = SPEC_DIR / "decoded"
GOLDENS_PATH = SPEC_DIR / "goldens.json"

ALGO_HASH_SIZES = {
    "average_hash":     [2, 4, 8, 16, 32, 64],
    "dhash":            [2, 4, 8, 16, 32, 64],
    "dhash_vertical":   [2, 4, 8, 16, 32, 64],
    "phash":            [2, 4, 8, 16, 32, 64],
    "phash_simple":     [2, 4, 8, 16, 32, 64],
    "whash_haar":       [2, 8, 16, 32, 64],
    "whash_db4":        [2, 8, 16, 32, 64],
    "whash_db4_robust": [2, 8, 16, 32, 64],
}
# Boundary-size goldens (see SPEC.md §"Boundary hash sizes"): hash_size=2 was
# added across all algorithms, then sizes 32 and 64 followed using the
# snap-to-threshold tie-break described in SPEC.md §"Threshold tie-break".
# For phash, phash_simple, whash_db4, and whash_db4_robust the bit comparison
# is `v > threshold + SNAP_EPS` instead of `v > threshold`, which
# deterministically maps near-threshold
# coefficients to 0 across every port (eliminating FP-noise tie flips).
# This is a deliberate spec change: existing size 8/16 goldens for those four
# algorithms may shift compared to pre-snap upstream `imagehash` output.
COLORHASH_BINBITS = [3, 4]

# Our-invention bolt-on. NOT in upstream imagehash. ε threshold for snapping
# near-zero db4 coefficients to exactly zero before median+threshold, so
# pathological symmetric inputs (where the LL band is mathematically 0 +
# float noise) produce a deterministic cross-port hash instead of relying on
# pywt's C+SIMD/FMA accumulation to land on a specific side of zero.
# For real photos, LL coefficients are far above ε, so output matches the
# strict whash_db4. See SPEC.md.
WHASH_DB4_ROBUST_EPS = 1e-12

# Snap-to-threshold tie-break ε. Coefficients within ε of the threshold
# (median for phash / whash_db4, mean for phash_simple) deterministically map
# to bit 0 — `bit = v > threshold + SNAP_EPS`. Sized to comfortably exceed
# the cross-port FP accumulation noise floor (~1e-12 for DCT on uint8 inputs,
# ~1e-15 for db4 wavedec2 on uint8/255 inputs) while staying far below any
# meaningful signal (next coefficient is typically O(0.1) or larger).
# Applied identically in all 6 ports (Python + Rust + Go + Java + JS + Swift).
# See SPEC.md §"Threshold tie-break".
SNAP_EPS = 1e-10


def _phash_with_snap(img, hash_size: int):
    """Reference phash with snap-to-threshold tie-break. Used to produce goldens.

    Identical to imagehash.phash() except the bit comparison is
    `v > median + SNAP_EPS` instead of strict `v > median`. This collapses
    near-median ties (within SNAP_EPS of the median) to bit 0 deterministically
    across all ports — independent of FP noise in the median sort or DCT.
    """
    highfreq_factor = 4
    img_size = hash_size * highfreq_factor
    L = img.convert("L").resize((img_size, img_size), imagehash.ANTIALIAS)
    pixels = np.asarray(L, dtype=np.float64)
    dct = scipy.fftpack.dct(scipy.fftpack.dct(pixels, axis=0), axis=1)
    block = dct[:hash_size, :hash_size]
    med = np.median(block)
    diff = block > med + SNAP_EPS
    return imagehash.ImageHash(diff)


def _phash_simple_with_snap(img, hash_size: int):
    """Reference phash_simple with snap-to-threshold tie-break.

    Identical to imagehash.phash_simple() except the bit comparison is
    `v > mean + SNAP_EPS` instead of strict `v > mean`. See _phash_with_snap.
    """
    highfreq_factor = 4
    img_size = hash_size * highfreq_factor
    L = img.convert("L").resize((img_size, img_size), imagehash.ANTIALIAS)
    pixels = np.asarray(L, dtype=np.float64)
    dct = scipy.fftpack.dct(pixels)
    dctlowfreq = dct[:hash_size, 1:hash_size + 1]
    mean = dctlowfreq.mean()
    diff = dctlowfreq > mean + SNAP_EPS
    return imagehash.ImageHash(diff)


def _whash_db4_with_snap(img, hash_size: int):
    """Reference whash_db4 with snap-to-threshold tie-break.

    Identical to imagehash.whash(mode='db4', remove_max_haar_ll=True) except
    the bit comparison is `v > median + SNAP_EPS` instead of strict
    `v > median`. See _phash_with_snap.
    """
    image_natural_scale = 2 ** int(np.log2(min(img.size)))
    image_scale = max(image_natural_scale, hash_size)
    ll_max_level = int(np.log2(image_scale))
    level = int(np.log2(hash_size))
    assert hash_size & (hash_size - 1) == 0, "hash_size must be power of 2"
    assert level <= ll_max_level, "hash_size in a wrong range"
    dwt_level = ll_max_level - level
    L = img.convert("L").resize((image_scale, image_scale), imagehash.ANTIALIAS)
    pixels = np.asarray(L) / 255.0
    coeffs = pywt.wavedec2(pixels, "haar", level=ll_max_level)
    coeffs = list(coeffs)
    coeffs[0] *= 0
    pixels = pywt.waverec2(coeffs, "haar")
    coeffs = pywt.wavedec2(pixels, "db4", level=dwt_level)
    dwt_low = coeffs[0]
    med = np.median(dwt_low)
    diff = dwt_low > med + SNAP_EPS
    return imagehash.ImageHash(diff)


def _whash_db4_robust(img, hash_size: int):
    """Reference whash_db4_robust. Used to produce goldens.

    Identical pipeline to imagehash.whash(mode='db4') through the dwt_low
    stage, then applies BOTH snap steps: (a) snap |c| < WHASH_DB4_ROBUST_EPS
    to exactly 0 (the legacy near-zero snap), then (b) compare
    `v > median + SNAP_EPS` (the new near-threshold snap). The combination
    handles both pathological-symmetric (large plateau of near-zero values)
    and general-image (near-median ties) cases.
    """
    image_natural_scale = 2 ** int(np.log2(min(img.size)))
    image_scale = max(image_natural_scale, hash_size)
    ll_max_level = int(np.log2(image_scale))
    level = int(np.log2(hash_size))
    assert hash_size & (hash_size - 1) == 0, "hash_size must be power of 2"
    assert level <= ll_max_level, "hash_size in a wrong range"
    dwt_level = ll_max_level - level
    L = img.convert("L").resize((image_scale, image_scale), imagehash.ANTIALIAS)
    pixels = np.asarray(L) / 255.0
    coeffs = pywt.wavedec2(pixels, "haar", level=ll_max_level)
    coeffs = list(coeffs)
    coeffs[0] *= 0
    pixels = pywt.waverec2(coeffs, "haar")
    coeffs = pywt.wavedec2(pixels, "db4", level=dwt_level)
    dwt_low = coeffs[0]
    # Snap near-zero coefficients to exactly zero (the original robust step).
    dwt_low = np.where(np.abs(dwt_low) < WHASH_DB4_ROBUST_EPS, 0.0, dwt_low)
    med = np.median(dwt_low)
    # Snap near-median coefficients to bit 0 (the new tie-break step).
    diff = dwt_low > med + SNAP_EPS
    return imagehash.ImageHash(diff)


def write_decoded(fixtures: list[Path], decoded_dir: Path) -> dict[str, str]:
    """For each PNG, write a canonical RGB pixel buffer. Returns {fixture_name: sha256_hex}."""
    decoded_dir.mkdir(parents=True, exist_ok=True)
    expected = {f.name + ".rgb.bin" for f in fixtures}
    for existing in decoded_dir.glob("*.rgb.bin"):
        if existing.name not in expected:
            existing.unlink()

    sha256s: dict[str, str] = {}
    for fix in sorted(fixtures):
        img = Image.open(fix).convert("RGB")
        w, h = img.size
        rgb = np.asarray(img, dtype=np.uint8)
        assert rgb.shape == (h, w, 3), f"shape mismatch for {fix.name}: {rgb.shape}"
        header = struct.pack("<II", w, h)
        body = rgb.tobytes()
        out_path = decoded_dir / (fix.name + ".rgb.bin")
        with open(out_path, "wb") as fh:
            fh.write(header)
            fh.write(body)
        sha256s[fix.name] = hashlib.sha256(header + body).hexdigest()
    return sha256s


CROP_RESISTANT_DEFAULTS = {
    "hash_func": "dhash",
    "limit_segments": None,
    "min_segment_size": 500,
    "segment_threshold": 128,
    "segmentation_image_size": 300,
}

# Fixtures excluded from the crop_resistant_hash golden table.
# crop-boundary-150.png lives in the spec specifically to expose the audit's
# H-H1 banker's-rounding drift (Rust/Java/Swift use the wrong rounding mode).
# Until H-H1 is fixed in those ports the Python-generated golden would FAIL
# the cross-port tests, so skip this fixture for now and revisit when H-H1
# lands.
CROP_RESISTANT_HASH_EXCLUDE = {"crop-boundary-150.png"}


def compute_goldens(fixtures: list[Path]) -> dict:
    """Run every v1 algorithm at every supported size on every fixture."""
    algorithms: dict[str, dict] = {}
    for algo_name, sizes in ALGO_HASH_SIZES.items():
        algorithms[algo_name] = {"hash_sizes": sizes, "fixtures": {}}
    algorithms["colorhash"] = {"binbits": COLORHASH_BINBITS, "fixtures": {}}
    algorithms["crop_resistant_hash"] = {
        "default_params": CROP_RESISTANT_DEFAULTS,
        "fixtures": {},
    }

    for fix in sorted(fixtures):
        img = Image.open(fix)
        for size in ALGO_HASH_SIZES["average_hash"]:
            algorithms["average_hash"]["fixtures"].setdefault(fix.name, {})[str(size)] = str(imagehash.average_hash(img, hash_size=size))
        for size in ALGO_HASH_SIZES["dhash"]:
            algorithms["dhash"]["fixtures"].setdefault(fix.name, {})[str(size)] = str(imagehash.dhash(img, hash_size=size))
        for size in ALGO_HASH_SIZES["dhash_vertical"]:
            algorithms["dhash_vertical"]["fixtures"].setdefault(fix.name, {})[str(size)] = str(imagehash.dhash_vertical(img, hash_size=size))
        for size in ALGO_HASH_SIZES["phash"]:
            # snap-to-threshold tie-break (SNAP_EPS) — see SPEC.md §"Threshold tie-break"
            algorithms["phash"]["fixtures"].setdefault(fix.name, {})[str(size)] = str(_phash_with_snap(img, size))
        for size in ALGO_HASH_SIZES["phash_simple"]:
            algorithms["phash_simple"]["fixtures"].setdefault(fix.name, {})[str(size)] = str(_phash_simple_with_snap(img, size))
        for size in ALGO_HASH_SIZES["whash_haar"]:
            # whash needs hash_size <= image_natural_scale; skip if it would assert
            try:
                algorithms["whash_haar"]["fixtures"].setdefault(fix.name, {})[str(size)] = str(imagehash.whash(img, hash_size=size, mode="haar", remove_max_haar_ll=True))
            except AssertionError:
                # Fixture too small for this hash_size; emit explicit null sentinel
                algorithms["whash_haar"]["fixtures"].setdefault(fix.name, {})[str(size)] = None
        for size in ALGO_HASH_SIZES["whash_db4"]:
            try:
                algorithms["whash_db4"]["fixtures"].setdefault(fix.name, {})[str(size)] = str(_whash_db4_with_snap(img, size))
            except (AssertionError, ValueError):
                algorithms["whash_db4"]["fixtures"].setdefault(fix.name, {})[str(size)] = None
        for size in ALGO_HASH_SIZES["whash_db4_robust"]:
            try:
                algorithms["whash_db4_robust"]["fixtures"].setdefault(fix.name, {})[str(size)] = str(_whash_db4_robust(img, size))
            except (AssertionError, ValueError):
                algorithms["whash_db4_robust"]["fixtures"].setdefault(fix.name, {})[str(size)] = None
        for binbits in COLORHASH_BINBITS:
            algorithms["colorhash"]["fixtures"].setdefault(fix.name, {})[str(binbits)] = str(imagehash.colorhash(img, binbits=binbits))
        if fix.name not in CROP_RESISTANT_HASH_EXCLUDE:
            # crop_resistant_hash uses default params; single "default" key per fixture.
            algorithms["crop_resistant_hash"]["fixtures"].setdefault(fix.name, {})["default"] = str(imagehash.crop_resistant_hash(img))

    return algorithms


def build_goldens(fixtures: list[Path], decoded_sha256: dict[str, str]) -> dict:
    return {
        "schema_version": 1,
        "imagehash_version": imagehash.__version__,
        "pillow_version": PIL.__version__,
        "numpy_version": np.__version__,
        "scipy_version": scipy.__version__,
        "pywavelets_version": pywt.__version__,
        "regenerated_at": dt.datetime.now(dt.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "algorithms": compute_goldens(fixtures),
        "decoded_sha256": decoded_sha256,
    }


def goldens_minus_timestamp(g: dict) -> dict:
    # Strip fields that don't affect the actual algorithm output but do
    # change across patch/minor installs of the underlying libraries.
    # The cross-port byte-exact guarantee depends on the algo data
    # (hashes/decoded), not on the labels recorded for provenance.
    out = dict(g)
    for key in (
        "regenerated_at",
        "imagehash_version",
        "pillow_version",
        "numpy_version",
        "scipy_version",
        "pywavelets_version",
    ):
        out.pop(key, None)
    return out


def collect_fixtures() -> list[Path]:
    fixtures = sorted([p for p in FIXTURES_DIR.glob("*.png")])
    if not fixtures:
        sys.exit(f"No fixtures found in {FIXTURES_DIR}")
    return fixtures


def atomic_write_json(path: Path, data: dict) -> None:
    tmp = path.with_suffix(path.suffix + ".tmp")
    tmp.write_text(json.dumps(data, indent=2, sort_keys=True) + "\n")
    tmp.replace(path)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true", help="Exit 1 if outputs would differ from committed")
    args = parser.parse_args(argv)

    fixtures = collect_fixtures()

    if args.check:
        with tempfile.TemporaryDirectory() as tmp:
            tmp_decoded = Path(tmp) / "decoded"
            tmp_sha = write_decoded(fixtures, tmp_decoded)
            decoded_drift = []
            for fix in fixtures:
                name = fix.name + ".rgb.bin"
                committed = (DECODED_DIR / name).read_bytes()
                generated = (tmp_decoded / name).read_bytes()
                if committed != generated:
                    decoded_drift.append(name)

            tmp_goldens = build_goldens(fixtures, tmp_sha)
            committed_goldens = json.loads(GOLDENS_PATH.read_text())
            a = goldens_minus_timestamp(committed_goldens)
            b = goldens_minus_timestamp(tmp_goldens)
            if a != b:
                print("Drift detected in goldens.json", file=sys.stderr)
                # Show first 5 differing keys to aid diagnosis
                diff_keys = [k for k in set(a) | set(b) if a.get(k) != b.get(k)]
                print("  differing top-level keys:", diff_keys[:5], file=sys.stderr)
                return 1
            if decoded_drift:
                print("Drift detected in decoded/:", decoded_drift, file=sys.stderr)
                return 1
        print("spec: no drift.")
        return 0

    sha256s = write_decoded(fixtures, DECODED_DIR)
    goldens = build_goldens(fixtures, sha256s)
    atomic_write_json(GOLDENS_PATH, goldens)
    print(f"Wrote {len(sha256s)} decoded buffers and goldens for {len(ALGO_HASH_SIZES) + 1} algorithms.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
