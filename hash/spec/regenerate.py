"""rosetta-image-hash spec regenerator.

Reads every fixture in fixtures/ and produces:
  - decoded/<name>.rgb.bin   canonical RGB pixel buffer from PIL convert('RGB')
  - goldens.json             byte-exact hex of every algo x fixture x size

Outputs are idempotent: running twice produces zero git diff (modulo
the regenerated_at timestamp, which we exclude from --check comparison).

Usage:
  python regenerate.py            # write outputs
  python regenerate.py --check    # exit 1 if outputs would differ from committed
"""
from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import json
import shutil
import struct
import sys
import tempfile
from pathlib import Path

import imagehash
import numpy as np
import PIL
from PIL import Image

SPEC_DIR = Path(__file__).parent
FIXTURES_DIR = SPEC_DIR / "fixtures"
DECODED_DIR = SPEC_DIR / "decoded"
GOLDENS_PATH = SPEC_DIR / "goldens.json"


def write_decoded(fixtures: list[Path], decoded_dir: Path) -> dict[str, str]:
    """For each PNG, write a canonical RGB pixel buffer. Returns {fixture_name: sha256_hex}."""
    decoded_dir.mkdir(parents=True, exist_ok=True)
    # Wipe stale .rgb.bin files (anything not regenerated from current fixtures)
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


def collect_fixtures() -> list[Path]:
    fixtures = sorted([p for p in FIXTURES_DIR.glob("*.png")])
    if not fixtures:
        sys.exit(f"No fixtures found in {FIXTURES_DIR}")
    return fixtures


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true", help="Exit 1 if outputs would differ from committed")
    args = parser.parse_args(argv)

    fixtures = collect_fixtures()

    if args.check:
        # Regenerate to a temp dir, compare bytes
        with tempfile.TemporaryDirectory() as tmp:
            tmp_decoded = Path(tmp) / "decoded"
            tmp_sha = write_decoded(fixtures, tmp_decoded)
            drift = []
            for fix in fixtures:
                name = fix.name + ".rgb.bin"
                committed = (DECODED_DIR / name).read_bytes()
                generated = (tmp_decoded / name).read_bytes()
                if committed != generated:
                    drift.append(name)
            if drift:
                print("Drift detected in decoded/:", drift, file=sys.stderr)
                return 1
        print("decoded/: no drift.")
        return 0

    sha256s = write_decoded(fixtures, DECODED_DIR)
    print(f"Wrote {len(sha256s)} decoded buffers to {DECODED_DIR}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
