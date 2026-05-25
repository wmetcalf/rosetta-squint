#!/usr/bin/env python3
"""Cross-port live diff for the squint convenience API.

For each (fixture × algorithm × size) combination, run every available
port's `squint-cli` binary and compare the hex outputs. Exits 1 on any
cross-port disagreement.

This complements `decode/tools/cross-port-diff/diff_all.py` (which diffs
raw decoded pixel buffers). This script diffs the full squint chain —
decode + hash — across all 6 ports. End-to-end byte-exact verification.

Usage:
    diff_all_squint.py                       # default grid (70 combos × 2 fixtures = full)
    diff_all_squint.py --grid=minimal        # ~22 combos: one canonical size per algo, fast
    diff_all_squint.py --grid=boundary       # ~48 combos: just sizes 2/32/64
    diff_all_squint.py --fixture <path>      # specific fixture (repeatable)
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


def _probe_turbojpeg() -> tuple[str | None, str | None]:
    """Return (lib_dir, jar_path) for turbojpeg, or (None, None) if not found.

    Honors TURBOJPEG_LIB_PATH / TURBOJPEG_JAR_PATH env vars if set; otherwise
    probes common system locations on Debian/Ubuntu/RHEL/macOS-homebrew.
    """
    # Library directory: needs to contain libturbojpeg.so / .dylib
    env_lib = os.environ.get("TURBOJPEG_LIB_PATH")
    lib_candidates = [
        env_lib,
        "/usr/lib/x86_64-linux-gnu",  # Debian/Ubuntu amd64
        "/usr/lib/aarch64-linux-gnu",  # Debian/Ubuntu arm64
        "/usr/lib64",                  # Fedora/RHEL
        "/opt/homebrew/lib",           # macOS arm64
        "/usr/local/lib",              # macOS intel / generic
    ]
    lib_dir: str | None = None
    for cand in lib_candidates:
        if not cand:
            continue
        p = Path(cand)
        if any((p / name).exists() for name in
               ("libturbojpeg.so", "libturbojpeg.so.0", "libturbojpeg.dylib")):
            lib_dir = cand
            break

    # JAR path
    env_jar = os.environ.get("TURBOJPEG_JAR_PATH")
    jar_candidates = [
        env_jar,
        "/usr/share/java/turbojpeg.jar",  # Debian/Ubuntu
        "/opt/homebrew/share/java/turbojpeg.jar",
        "/usr/local/share/java/turbojpeg.jar",
    ]
    jar_path: str | None = None
    for cand in jar_candidates:
        if cand and Path(cand).exists():
            jar_path = cand
            break

    return lib_dir, jar_path


_TJ_LIB_DIR, _TJ_JAR_PATH = _probe_turbojpeg()


def _java_argv() -> list[str] | None:
    """Build the Java CLI argv if turbojpeg lib + jar were probed; else None."""
    if _TJ_LIB_DIR is None or _TJ_JAR_PATH is None:
        return None
    return [
        "java",
        f"-Djava.library.path={_TJ_LIB_DIR}",
        "--enable-native-access=ALL-UNNAMED",
        "-cp",
        f"{ROOT}/squint/java/rosetta-squint/target/squint-cli.jar:{_TJ_JAR_PATH}",
        "io.rosetta.squint.cli.SquintCli",
    ]


# Per-port CLI invocations. Each is the argv prefix; the harness appends
# <algo> <size> <fixture-path> at call time. Missing binaries are silently
# skipped (with a notice).
PORTS: dict[str, list[str]] = {
    "python": [sys.executable, str(Path(__file__).parent / "squint_cli.py")],
    "rust": [str(ROOT / "squint/rust/rosetta-squint/target/release/examples/squint-cli")],
    "go":   [str(Path(__file__).parent / "squint-go")],  # binary built into tools/cross-squint-diff/
    "js":    ["node", str(ROOT / "squint/js/rosetta-squint/scripts/squint-cli.mjs")],
    "swift": [str(ROOT / "squint/swift/RosettaSquint/.build/release/SquintCLI")],
}
_java = _java_argv()
if _java is not None:
    PORTS["java"] = _java
else:
    print(
        "  skip java: turbojpeg lib/jar not found "
        "(set TURBOJPEG_LIB_PATH / TURBOJPEG_JAR_PATH to override)",
        file=sys.stderr,
    )

# Algorithm × hash-size grid to test against each fixture.
DEFAULT_GRID: list[tuple[str, int]] = [
    # hash_size=2 boundary cases (H-L8, AUDIT-claude.md).
    ("average_hash",     2),
    ("dhash",            2),
    ("dhash_vertical",   2),
    ("phash",            2),
    ("phash_simple",     2),
    ("whash_haar",       2),
    ("whash_db4",        2),
    ("whash_db4_robust", 2),
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
    # hash_size=32 and hash_size=64 boundary cases (H-L8 follow-up,
    # AUDIT-claude.md, 2026-05-23). Goldens enabled after adding
    # snap-to-threshold tie-break to phash, phash_simple, whash_db4 and
    # whash_db4_robust. colorhash takes binbits, not hash_size; skip.
    # crop_resistant_hash uses fixed segment-phash size; skip.
    ("phash",            32),
    ("phash",            64),
    ("phash_simple",     32),
    ("phash_simple",     64),
    ("whash_haar",       32),
    ("whash_haar",       64),
    ("whash_db4",        32),
    ("whash_db4",        64),
    ("whash_db4_robust", 32),
    ("whash_db4_robust", 64),
    ("average_hash",     32),
    ("average_hash",     64),
    ("dhash",            32),
    ("dhash",            64),
    ("dhash_vertical",   32),
    ("dhash_vertical",   64),
    # crop_resistant_hash has no size — pass 0 as placeholder; CLIs ignore
    ("crop_resistant_hash", 0),
]

# Smaller grid for fast local iteration — drops the slower boundary sizes (32, 64)
# and the duplicate sizes within each algorithm. Keeps coverage breadth across
# all 10 algorithms at hash_size 8 + the canonical phash@16 + colorhash@3.
MINIMAL_GRID: list[tuple[str, int]] = [
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
    ("crop_resistant_hash", 0),
]

# Only the boundary sizes (2, 32, 64) — useful for verifying the snap-to-threshold
# tie-break + size-2 goldens after a change to the algorithm code.
BOUNDARY_GRID: list[tuple[str, int]] = [
    ("average_hash",     2),
    ("dhash",            2),
    ("dhash_vertical",   2),
    ("phash",            2),
    ("phash_simple",     2),
    ("whash_haar",       2),
    ("whash_db4",        2),
    ("whash_db4_robust", 2),
    ("phash",           32),
    ("phash",           64),
    ("phash_simple",    32),
    ("phash_simple",    64),
    ("dhash",           32),
    ("dhash",           64),
    ("average_hash",    32),
    ("average_hash",    64),
    ("whash_haar",      32),
    ("whash_haar",      64),
    ("whash_db4",       32),
    ("whash_db4",       64),
    ("whash_db4_robust",32),
    ("whash_db4_robust",64),
    ("dhash_vertical",  32),
    ("dhash_vertical",  64),
]

GRIDS: dict[str, list[tuple[str, int]]] = {
    "default":  DEFAULT_GRID,
    "minimal":  MINIMAL_GRID,
    "boundary": BOUNDARY_GRID,
}

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
    # 300s, not 120s — crop_resistant_hash on the slowest port (Java/Swift cold-start
    # under JIT/launch overhead) can take ~370ms per call on the default fixture
    # set, so 120s leaves zero headroom for larger fixtures someone might point at.
    cmd = argv + [algo, str(size), str(fixture)]
    proc = None
    try:
        proc = subprocess.Popen(
            cmd,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
        )
        try:
            stdout, stderr = proc.communicate(timeout=300)
        except subprocess.TimeoutExpired:
            proc.kill()
            proc.communicate()
            return ("", "timeout")
        if proc.returncode != 0:
            return ("", f"exit {proc.returncode}: {stderr.decode(errors='replace').strip()}")
        return (stdout.decode().strip(), None)
    except KeyboardInterrupt:
        # Ctrl-C: tear down the child so the user gets their shell back promptly,
        # then re-raise so main() exits.
        if proc is not None:
            proc.kill()
            try:
                proc.communicate(timeout=2)
            except Exception:
                pass
        raise
    except FileNotFoundError as e:
        return ("", f"binary missing: {e}")


def main() -> int:
    p = argparse.ArgumentParser()
    p.add_argument("--fixture", action="append", help="specific fixture (repeatable)")
    p.add_argument("--algo", help="single algorithm only")
    p.add_argument("--size", type=int, help="single size only (with --algo)")
    p.add_argument("--regression", action="store_true", help="exit 1 on any diff")
    p.add_argument("--grid", choices=sorted(GRIDS.keys()), default="default",
                   help="which (algo, size) grid to run: default (all), "
                        "minimal (one canonical size per algo, fast), "
                        "boundary (just sizes 2/32/64 for snap-to-threshold verification). "
                        "Ignored when --algo is given.")
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
        grid = GRIDS[args.grid]

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
            if errors:
                # Some ports errored. Even if the ones that returned all agreed,
                # surface the errors — silent skip would hide individual-port
                # regressions (stale npm install, missing system library, etc.).
                failed += 1
                ports_passed = ",".join(sorted(results.keys()))
                err_summary = "; ".join(f"{name}: {e}" for name, e in sorted(errors.items()))
                print(f"WARN {fixture.name}/{algo}@{size}: {short_ref}  ports=[{ports_passed}]  ERRORED: {err_summary}")
            elif agreement:
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
