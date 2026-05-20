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
from PIL import Image

SPEC_DIR = Path(__file__).parent
FIXTURES_DIR = SPEC_DIR / "fixtures"
DECODED_DIR = SPEC_DIR / "decoded"
GOLDENS_PATH = SPEC_DIR / "goldens.json"

ALGO_HASH_SIZES = {
    "average_hash": [4, 8, 16],
    "dhash":        [4, 8, 16],
    "phash":        [4, 8, 16],
    "whash_haar":   [8, 16],
}
COLORHASH_BINBITS = [3, 4]


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


def compute_goldens(fixtures: list[Path]) -> dict:
    """Run every v1 algorithm at every supported size on every fixture."""
    algorithms: dict[str, dict] = {}
    for algo_name, sizes in ALGO_HASH_SIZES.items():
        algorithms[algo_name] = {"hash_sizes": sizes, "fixtures": {}}
    algorithms["colorhash"] = {"binbits": COLORHASH_BINBITS, "fixtures": {}}

    for fix in sorted(fixtures):
        img = Image.open(fix)
        for size in ALGO_HASH_SIZES["average_hash"]:
            algorithms["average_hash"]["fixtures"].setdefault(fix.name, {})[str(size)] = str(imagehash.average_hash(img, hash_size=size))
        for size in ALGO_HASH_SIZES["dhash"]:
            algorithms["dhash"]["fixtures"].setdefault(fix.name, {})[str(size)] = str(imagehash.dhash(img, hash_size=size))
        for size in ALGO_HASH_SIZES["phash"]:
            algorithms["phash"]["fixtures"].setdefault(fix.name, {})[str(size)] = str(imagehash.phash(img, hash_size=size))
        for size in ALGO_HASH_SIZES["whash_haar"]:
            # whash needs hash_size <= image_natural_scale; skip if it would assert
            try:
                algorithms["whash_haar"]["fixtures"].setdefault(fix.name, {})[str(size)] = str(imagehash.whash(img, hash_size=size, mode="haar", remove_max_haar_ll=True))
            except AssertionError:
                # Fixture too small for this hash_size; emit explicit null sentinel
                algorithms["whash_haar"]["fixtures"].setdefault(fix.name, {})[str(size)] = None
        for binbits in COLORHASH_BINBITS:
            algorithms["colorhash"]["fixtures"].setdefault(fix.name, {})[str(binbits)] = str(imagehash.colorhash(img, binbits=binbits))

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
    out = dict(g)
    out.pop("regenerated_at", None)
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
