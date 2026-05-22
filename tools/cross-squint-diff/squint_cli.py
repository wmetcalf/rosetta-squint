#!/usr/bin/env python3
"""Python squint-cli — mirrors the Rust/Go/Java/JS/Swift squint-cli binaries
for the cross-port live-diff harness.

Usage: squint_cli.py <algo> <size_or_binbits> <fixture-path>

Output: hex string on stdout (trailing newline).
Errors: message on stderr, exit code 1.

Supported algos (match spec/SPEC.md names):
    phash, phash_simple, dhash, dhash_vertical, average_hash,
    whash_haar, whash_db4, whash_db4_robust,
    colorhash (size = binbits), crop_resistant_hash (size ignored)
"""

import sys
from pathlib import Path

# Make sure rosetta_squint resolves: this script lives in tools/cross-squint-diff/
# which is a sibling of squint/python/. The package is installable via pip
# install -e squint/python — assume the user has done that.
import rosetta_squint as rs

ALGOS = {
    "phash": rs.phash,
    "phash_simple": rs.phash_simple,
    "dhash": rs.dhash,
    "dhash_vertical": rs.dhash_vertical,
    "average_hash": rs.average_hash,
    "whash_haar": rs.whash_haar,
    "whash_db4": rs.whash_db4,
    "whash_db4_robust": rs.whash_db4_robust,
    "colorhash": rs.colorhash,  # second arg is binbits
    "crop_resistant_hash": rs.crop_resistant_hash,  # no size arg
}


def main() -> int:
    if len(sys.argv) != 4:
        print("usage: squint_cli.py <algo> <size> <path>", file=sys.stderr)
        return 2
    algo = sys.argv[1]
    size_str = sys.argv[2]
    path = Path(sys.argv[3])

    if algo not in ALGOS:
        print(f"unknown algo: {algo}", file=sys.stderr)
        return 2

    fn = ALGOS[algo]
    try:
        if algo == "crop_resistant_hash":
            result = fn(path)
        else:
            size = int(size_str)
            result = fn(path, size)
        print(str(result))
        return 0
    except Exception as e:
        print(f"error: {e}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
