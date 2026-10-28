#!/usr/bin/env python3
"""Check the installed `rosetta_squint` decodes every valid decode fixture to
the pixels recorded in decode/spec/goldens.json.

`rosetta_squint` decodes through Pillow (and system libheif for HEIC), and the
shipped Pillow pin is a range, so the decoder a user gets can move between
releases without a change here. Run by .github/workflows/pypi-drift.yml
against a fresh `pip install rosetta-squint`. Exits 1 on any mismatch.

Usage: check_decode.py [--skip-format heic ...]
"""

from __future__ import annotations

import argparse
import hashlib
import json
import sys
from pathlib import Path

import rosetta_squint

DECODE_SPEC = Path(__file__).resolve().parent.parent.parent / "decode" / "spec"


def canonical(img):
    """decode/spec/regenerate.py decide_channels(): keep alpha if present."""
    mode = img.mode
    if mode in ("RGBA", "LA", "PA") or (mode == "P" and "transparency" in img.info) or "A" in mode:
        return img.convert("RGBA"), 4
    return img.convert("RGB"), 3


def main() -> int:
    p = argparse.ArgumentParser()
    p.add_argument("--skip-format", action="append", default=[], help="format to skip (repeatable)")
    args = p.parse_args()

    fixtures = json.loads((DECODE_SPEC / "goldens.json").read_text())["fixtures"]
    checked = failed = 0
    for rel, want in sorted(fixtures.items()):
        if want["format"] in args.skip_format:
            continue
        checked += 1
        try:
            img, channels = canonical(rosetta_squint.decode_file(str(DECODE_SPEC / "fixtures" / rel)))
            got = (img.width, img.height, channels, hashlib.sha256(img.tobytes()).hexdigest())
        except Exception as e:  # noqa: BLE001 — report every fixture, not just the first
            got = (type(e).__name__, str(e))
        if got != (want["width"], want["height"], want["channels"], want["sha256"]):
            failed += 1
            print(f"DIFF {rel}: got {got}, want {want}")
    print(f"{checked - failed}/{checked} decode fixtures match decode/spec/goldens.json"
          + (f" (skipped: {', '.join(args.skip_format)})" if args.skip_format else ""))
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
