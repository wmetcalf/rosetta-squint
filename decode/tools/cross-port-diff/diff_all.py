#!/usr/bin/env python3
"""Orchestrator for the live PIL / cross-port diff harness.

For each fixture: run PIL + each available per-port CLI binary, capture the
raw byte streams (wire format: spec/SPEC.md §2), and diff them pairwise.

Reports cross-port drift that the per-port Group 2 tests (which compare
against frozen .bin goldens) wouldn't catch immediately.

Usage:
    diff_all.py                         # all valid fixtures
    diff_all.py <fixture.path> ...      # specific fixtures
    diff_all.py --regression            # exit 1 on any drift

Exit codes:
    0 — all port outputs match each other (and PIL where applicable)
    1 — at least one pairwise diff detected
    2 — harness error
"""
from __future__ import annotations

import argparse
import hashlib
import os
import shutil
import subprocess
import sys
from pathlib import Path
from typing import Iterable

ROOT = Path(__file__).resolve().parent.parent.parent
TOOLS = ROOT / "tools" / "cross-port-diff"
FIXTURES = ROOT / "spec" / "fixtures"

# Per-port CLI runners. Each is a list of argv to invoke; the fixture path
# is appended. The script defers to whichever binaries exist — missing ones
# are skipped (with a notice).
PORTS = {
    "pil": [sys.executable, str(TOOLS / "decode_pil.py")],
    "rust": [
        str(ROOT / "rust" / "rosetta-image-decode" / "target" / "release" / "examples" / "decode-cli"),
    ],
    "go": [
        str(TOOLS / "decode-go"),
    ],
    "java": [
        "java",
        "-Djava.library.path=/usr/lib/x86_64-linux-gnu",
        "--enable-native-access=ALL-UNNAMED",
        "-cp",
        f"{ROOT}/java/rosetta-image-decode/target/decode-cli.jar:/usr/share/java/turbojpeg.jar",
        "io.rosetta.imagedecode.cli.DecodeCli",
    ],
    "js": [
        "node",
        str(ROOT / "js" / "rosetta-image-decode" / "scripts" / "decode-cli.mjs"),
    ],
    "swift": [
        str(ROOT / "swift" / "RosettaImageDecode" / ".build" / "release" / "DecodeCLI"),
    ],
}

# HEIC special case: PIL via pillow-heif diverges from system libheif by ±1 px,
# and libheif-js (the WASM build) diverges from system libheif too. decode_pil.py
# uses the ctypes system wrapper, so PIL is fine. But the JS port's HEIC
# output will diverge from system libheif by ±2 px (documented in
# js/rosetta-image-decode/DECODER_NOTES.md). For HEIC we use a tolerance.
HEIC_MAX_DELTA = 2


def list_fixtures() -> list[Path]:
    out: list[Path] = []
    for fmt_dir in sorted(FIXTURES.iterdir()):
        if not fmt_dir.is_dir():
            continue
        valid = fmt_dir / "valid"
        if not valid.exists():
            continue
        for f in sorted(valid.iterdir()):
            if not f.is_file() or f.name.startswith("."):
                continue
            out.append(f)
    return out


def available_ports() -> dict[str, list[str]]:
    """Return only the ports whose first argv element exists / is on PATH."""
    out: dict[str, list[str]] = {}
    for name, argv in PORTS.items():
        first = argv[0]
        # If first looks like a path, check it exists; else assume on PATH.
        if "/" in first or first.endswith(".py") or first.endswith(".mjs"):
            if not Path(first).exists():
                print(f"  skip {name}: {first} not built", file=sys.stderr)
                continue
        else:
            if shutil.which(first) is None:
                print(f"  skip {name}: {first} not on PATH", file=sys.stderr)
                continue
        out[name] = argv
    return out


def run_port(name: str, argv: list[str], fixture: Path) -> tuple[bytes, str | None]:
    """Run a port's CLI on a fixture. Returns (bytes, error_message)."""
    try:
        proc = subprocess.run(
            argv + [str(fixture)],
            capture_output=True,
            timeout=60,
        )
        if proc.returncode != 0:
            return b"", f"{name} exit {proc.returncode}: {proc.stderr.decode(errors='replace').strip()}"
        return proc.stdout, None
    except subprocess.TimeoutExpired:
        return b"", f"{name} timeout"
    except FileNotFoundError as e:
        return b"", f"{name} missing binary: {e}"


def short_sha(b: bytes) -> str:
    return hashlib.sha256(b).hexdigest()[:16]


def diff_within_tolerance(a: bytes, b: bytes, max_delta: int) -> tuple[bool, int]:
    """Returns (within_tolerance, observed_max_delta)."""
    if len(a) != len(b):
        return False, -1
    observed = 0
    for x, y in zip(a, b):
        d = abs(x - y)
        if d > observed:
            observed = d
    return observed <= max_delta, observed


def cmp_fixtures(fixtures: Iterable[Path], ports: dict[str, list[str]]) -> int:
    failures = 0
    for fixture in fixtures:
        is_heic = fixture.suffix.lower() in (".heic", ".heif")
        results: dict[str, bytes] = {}
        errors: dict[str, str] = {}
        for name, argv in ports.items():
            data, err = run_port(name, argv, fixture)
            if err:
                errors[name] = err
            else:
                results[name] = data
        rel = fixture.relative_to(ROOT)
        if not results:
            print(f"FAIL {rel} — no ports produced output: {errors}")
            failures += 1
            continue
        # Pick a reference: PIL if present, else the first port.
        ref_name = "pil" if "pil" in results else next(iter(results))
        ref = results[ref_name]
        ref_sha = short_sha(ref)
        line_parts = [f"{ref_name}={ref_sha}"]
        fixture_failed = False
        for name, data in results.items():
            if name == ref_name:
                continue
            sha = short_sha(data)
            equal = data == ref
            tag = sha
            if equal:
                tag = f"{sha} ✓"
            elif is_heic and name == "js":
                ok, delta = diff_within_tolerance(data, ref, HEIC_MAX_DELTA)
                tag = f"{sha} {'~' if ok else '✗'}Δ{delta}"
                if not ok:
                    fixture_failed = True
            else:
                tag = f"{sha} ✗"
                fixture_failed = True
            line_parts.append(f"{name}={tag}")
        for name, err in errors.items():
            line_parts.append(f"{name}=ERROR")
        if fixture_failed:
            print(f"DIFF {rel}: {' '.join(line_parts)}")
            failures += 1
        else:
            print(f"OK   {rel}: {' '.join(line_parts)}")
    return failures


def cmp_vs_goldens(fixtures: Iterable[Path], ports: dict[str, list[str]]) -> int:
    """Per-port comparison against frozen spec/decoded/<format>/<fixture>.bin.

    Catches REGRESSIONS in any single port without forcing PIL or any other
    port to be the reference. A port that breaks its own Group 2 tests will
    fail here too, but the harness verifies the live binary outside the
    test framework — useful if you suspect a test infrastructure bug.
    """
    decoded = ROOT / "spec" / "decoded"
    failures = 0
    for fixture in fixtures:
        rel = fixture.relative_to(ROOT)
        # Map fixture path → golden path. spec/fixtures/<fmt>/valid/<file>
        # → spec/decoded/<fmt>/valid/<file>.bin
        try:
            after_fixtures = fixture.relative_to(FIXTURES)
        except ValueError:
            continue
        golden_path = decoded / (str(after_fixtures) + ".bin")
        if not golden_path.exists():
            print(f"SKIP {rel}: no golden at {golden_path.relative_to(ROOT)}")
            continue
        golden = golden_path.read_bytes()
        golden_sha = short_sha(golden)
        is_heic = fixture.suffix.lower() in (".heic", ".heif")
        line_parts = [f"golden={golden_sha}"]
        fixture_failed = False
        for name, argv in ports.items():
            data, err = run_port(name, argv, fixture)
            if err:
                line_parts.append(f"{name}=ERROR")
                continue
            sha = short_sha(data)
            equal = data == golden
            if equal:
                line_parts.append(f"{name}={sha} ✓")
            elif is_heic and name == "js":
                ok, delta = diff_within_tolerance(data, golden, HEIC_MAX_DELTA)
                line_parts.append(f"{name}={sha} {'~' if ok else '✗'}Δ{delta}")
                if not ok:
                    fixture_failed = True
            else:
                line_parts.append(f"{name}={sha} ✗")
                fixture_failed = True
        if fixture_failed:
            print(f"DIFF {rel}: {' '.join(line_parts)}")
            failures += 1
        else:
            print(f"OK   {rel}: {' '.join(line_parts)}")
    return failures


def main() -> int:
    p = argparse.ArgumentParser()
    p.add_argument("fixtures", nargs="*", help="specific fixtures (default: all)")
    p.add_argument("--regression", action="store_true", help="exit 1 on any diff")
    p.add_argument(
        "--vs-goldens",
        action="store_true",
        help="compare each port vs spec/decoded/*.bin (regression check), not pairwise",
    )
    args = p.parse_args()

    if args.fixtures:
        fixtures = [Path(x).resolve() for x in args.fixtures]
    else:
        fixtures = list_fixtures()

    print(f"available ports:", file=sys.stderr)
    ports = available_ports()
    if not ports:
        print("no ports available — build at least one CLI first", file=sys.stderr)
        return 2
    for name in ports:
        print(f"  {name}", file=sys.stderr)
    print(f"comparing {len(fixtures)} fixtures across {len(ports)} ports", file=sys.stderr)
    print(file=sys.stderr)

    if args.vs_goldens:
        failures = cmp_vs_goldens(fixtures, ports)
    else:
        failures = cmp_fixtures(fixtures, ports)
    if failures > 0:
        print(f"\n{failures} diff(s) detected.", file=sys.stderr)
        return 1 if args.regression else 0
    print(f"\nAll {len(fixtures)} fixtures match.", file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main())
