#!/usr/bin/env python3
"""Cross-port live diff for the squint convenience API.

For each (fixture × algorithm × size) combination, run every available
port's `squint-cli` binary and compare the hex outputs. Exits 1 on any
cross-port disagreement.

This complements `decode/tools/cross-port-diff/diff_all.py` (which diffs
raw decoded pixel buffers). This script diffs the full squint chain —
decode + hash — across all 6 ports. End-to-end byte-exact verification.

Usage:
    diff_all_squint.py                       # default: all algos, peppers.png + imagehash.png
    diff_all_squint.py --fixture <path>      # specific fixture
    diff_all_squint.py --algo phash --size 8 # specific algorithm
    diff_all_squint.py --regression          # exit 1 on any diff (CI mode)

Exit codes:
    0 — all available ports agreed on every (fixture × algo × size)
    1 — at least one disagreement
    2 — harness error (no ports available, etc.)
"""

from __future__ import annotations

import argparse
import os
import shutil
import subprocess
import sys
from pathlib import Path
from typing import Iterable

ROOT = Path(__file__).resolve().parent.parent.parent
HASH_FIXTURES = ROOT / "hash" / "spec" / "fixtures"

# Per-port CLI invocations. Each is the argv prefix; the harness appends
# <algo> <size> <fixture-path> at call time. Missing binaries are silently
# skipped (with a notice).
PORTS: dict[str, list[str]] = {
    "python": [sys.executable, str(Path(__file__).parent / "squint_cli.py")],
    "rust": [str(ROOT / "squint/rust/rosetta-squint/target/release/examples/squint-cli")],
    "go":   [str(Path(__file__).parent / "squint-go")],  # binary built into tools/cross-squint-diff/
    "java": [
        "java",
        "-Djava.library.path=/usr/lib/x86_64-linux-gnu",
        "--enable-native-access=ALL-UNNAMED",
        "-cp",
        f"{ROOT}/squint/java/rosetta-squint/target/squint-cli.jar:"
        f"/usr/share/java/turbojpeg.jar",
        "io.rosetta.squint.cli.SquintCli",
    ],
    "js":    ["node", str(ROOT / "squint/js/rosetta-squint/scripts/squint-cli.mjs")],
    "swift": [str(ROOT / "squint/swift/RosettaSquint/.build/release/SquintCLI")],
}

# Algorithm × hash-size grid to test against each fixture.
DEFAULT_GRID: list[tuple[str, int]] = [
    ("phash",            8),
    ("phash",           16),
    ("phash_simple",     8),
    ("dhash",            8),
    ("dhash_vertical",   8),
    ("average_hash",     8),
    ("whash_haar",       8),
    ("whash_db4",        8),
    ("whash_db4_robust", 8),
    ("colorhash",        3),
    # crop_resistant_hash has no size — pass 0 as placeholder; CLIs ignore
    ("crop_resistant_hash", 0),
]

DEFAULT_FIXTURES = [
    HASH_FIXTURES / "imagehash.png",
    HASH_FIXTURES / "peppers.png",
]


def available_ports() -> dict[str, list[str]]:
    """Return only the ports whose first argv element is executable / on PATH."""
    out: dict[str, list[str]] = {}
    for name, argv in PORTS.items():
        first = argv[0]
        if "/" in first or first.endswith(".py") or first.endswith(".mjs"):
            if not Path(first).exists():
                print(f"  skip {name}: {first} not built", file=sys.stderr)
                continue
        elif shutil.which(first) is None:
            print(f"  skip {name}: {first} not on PATH", file=sys.stderr)
            continue
        out[name] = argv
    return out


def run_port(name: str, argv: list[str], algo: str, size: int, fixture: Path) -> tuple[str, str | None]:
    """Run a port's CLI for one (algo, size, fixture). Returns (hex, err)."""
    try:
        proc = subprocess.run(
            argv + [algo, str(size), str(fixture)],
            capture_output=True,
            timeout=120,
        )
        if proc.returncode != 0:
            return ("", f"exit {proc.returncode}: {proc.stderr.decode(errors='replace').strip()}")
        return (proc.stdout.decode().strip(), None)
    except subprocess.TimeoutExpired:
        return ("", "timeout")
    except FileNotFoundError as e:
        return ("", f"binary missing: {e}")


def main() -> int:
    p = argparse.ArgumentParser()
    p.add_argument("--fixture", action="append", help="specific fixture (repeatable)")
    p.add_argument("--algo", help="single algorithm only")
    p.add_argument("--size", type=int, help="single size only (with --algo)")
    p.add_argument("--regression", action="store_true", help="exit 1 on any diff")
    args = p.parse_args()

    print("available ports:", file=sys.stderr)
    ports = available_ports()
    if not ports:
        print("no squint CLIs built — see Makefile target `make build-all-squint-clis`", file=sys.stderr)
        return 2
    for name in ports:
        print(f"  {name}", file=sys.stderr)
    print(file=sys.stderr)

    fixtures = [Path(f) for f in args.fixture] if args.fixture else DEFAULT_FIXTURES
    if args.algo:
        grid = [(args.algo, args.size or 8)]
    else:
        grid = DEFAULT_GRID

    total = 0
    failed = 0
    for fixture in fixtures:
        if not fixture.exists():
            print(f"FAIL {fixture} — missing", file=sys.stderr)
            failed += 1
            continue
        for algo, size in grid:
            total += 1
            results: dict[str, str] = {}
            errors: dict[str, str] = {}
            for name, argv in ports.items():
                hex_str, err = run_port(name, argv, algo, size, fixture)
                if err:
                    errors[name] = err
                else:
                    results[name] = hex_str
            if not results:
                print(f"FAIL {fixture.name}/{algo}@{size} — all ports errored: {errors}")
                failed += 1
                continue
            # Reference: first port's output (deterministic — Python is always present).
            ref_name = "python" if "python" in results else next(iter(results))
            ref = results[ref_name]
            agreement = all(v == ref for v in results.values())
            short_ref = ref[:12] + ("…" if len(ref) > 12 else "")
            if agreement:
                ports_passed = ",".join(sorted(results.keys()))
                print(f"OK   {fixture.name}/{algo}@{size}: {short_ref}  ports=[{ports_passed}]")
            else:
                failed += 1
                rows = []
                for name in sorted(results.keys()):
                    h = results[name]
                    marker = "✓" if h == ref else "✗"
                    rows.append(f"{name}={h[:12]}{'…' if len(h) > 12 else ''} {marker}")
                for name, e in errors.items():
                    rows.append(f"{name}=ERROR")
                print(f"DIFF {fixture.name}/{algo}@{size}: {' '.join(rows)}")
    print(file=sys.stderr)
    if failed:
        print(f"{failed}/{total} disagreements", file=sys.stderr)
        return 1 if args.regression else 0
    print(f"All {total} (fixture × algo × size) combinations agreed across {len(ports)} ports.", file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main())
