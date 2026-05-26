#!/usr/bin/env python3
"""Cross-port performance benchmark for the squint chain.

For each (port × algorithm) combination, time N iterations of
`squint_cli phash 8 <fixture>` end-to-end. Reports median wall-time per
hash. Side-by-side comparison across all 6 ports.

End-to-end means: process startup + library init + decode + hash + print.
This is what a real caller pays. For a more granular view, the underlying
hash/decode libraries each have their own benchmark targets you'd run
via the language's native bench tool (cargo bench / go test -bench /
JMH / vitest bench / XCTest measure / pytest-benchmark).

Caveats:
- JS, Java, Swift, Python pay JIT/VM warmup on every invocation. For
  workloads where you'd amortise startup (e.g. a long-running server),
  the per-hash latency is dramatically lower than what this benchmark
  shows. Use a port's native in-process bench harness to measure that
  steady-state cost.
- Times are walltime — includes any system load. Use --iter to control
  variance.

Usage:
    bench.py                       # default: phash@8, 5 iters, peppers.png
    bench.py --iter 20
    bench.py --algo dhash --size 8
    bench.py --fixture path/to.jpg
"""

from __future__ import annotations

import argparse
import os
import shutil
import statistics
import subprocess
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent.parent
HASH_FIXTURES = ROOT / "hash" / "spec" / "fixtures"


def _probe_turbojpeg() -> tuple[str | None, str | None]:
    """Return (lib_dir, jar_path) for turbojpeg, or (None, None) if not found.

    Honors TURBOJPEG_LIB_PATH / TURBOJPEG_JAR_PATH env vars if set; otherwise
    probes common system locations on Debian/Ubuntu/RHEL/macOS-homebrew.
    """
    env_lib = os.environ.get("TURBOJPEG_LIB_PATH")
    lib_candidates = [
        env_lib,
        "/usr/lib/x86_64-linux-gnu",
        "/usr/lib/aarch64-linux-gnu",
        "/usr/lib64",
        "/opt/homebrew/lib",
        "/usr/local/lib",
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

    env_jar = os.environ.get("TURBOJPEG_JAR_PATH")
    jar_candidates = [
        env_jar,
        "/usr/share/java/turbojpeg.jar",
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
    if _TJ_LIB_DIR is None or _TJ_JAR_PATH is None:
        return None
    return [
        "java",
        f"-Djava.library.path={_TJ_LIB_DIR}",
        "--enable-native-access=ALL-UNNAMED",
        "-cp",
        f"{ROOT}/squint/java/rosetta-squint/target/squint-cli.jar:{_TJ_JAR_PATH}",
        "io.github.wmetcalf.rosettasquint.cli.SquintCli",
    ]


PORTS: dict[str, list[str]] = {
    "python": [sys.executable, str(ROOT / "tools" / "cross-squint-diff" / "squint_cli.py")],
    "rust":   [str(ROOT / "squint/rust/rosetta-squint/target/release/examples/squint-cli")],
    "go":     [str(ROOT / "tools/cross-squint-diff/squint-go")],
    "js":     ["node", str(ROOT / "squint/js/rosetta-squint/scripts/squint-cli.mjs")],
    "swift":  [str(ROOT / "squint/swift/RosettaSquint/.build/release/SquintCLI")],
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


def available_ports() -> dict[str, list[str]]:
    out: dict[str, list[str]] = {}
    for name, argv in PORTS.items():
        first = argv[0]
        if "/" in first or first.endswith(".py") or first.endswith(".mjs"):
            if Path(first).exists():
                out[name] = argv
        elif shutil.which(first) is not None:
            out[name] = argv
    return out


def time_one(argv: list[str], algo: str, size: int, fixture: Path) -> float:
    """Return wall-time in seconds for one invocation. Raises on non-zero exit."""
    cmd = argv + [algo, str(size), str(fixture)]
    t0 = time.perf_counter()
    proc = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    try:
        try:
            stdout, stderr = proc.communicate(timeout=120)
        except subprocess.TimeoutExpired:
            proc.kill()
            proc.communicate()
            raise
    except KeyboardInterrupt:
        # Ctrl-C: tear down the child so the user gets their shell back promptly,
        # then re-raise so the caller exits.
        proc.kill()
        try:
            proc.communicate(timeout=2)
        except Exception:
            pass
        raise
    elapsed = time.perf_counter() - t0
    if proc.returncode != 0:
        raise RuntimeError(
            f"port exited {proc.returncode}: "
            + stderr.decode(errors="replace").strip()
        )
    return elapsed


def main() -> int:
    p = argparse.ArgumentParser()
    p.add_argument("--algo", default="phash", help="algorithm to benchmark")
    p.add_argument("--size", type=int, default=8, help="hash_size / binbits")
    p.add_argument("--fixture", default=str(HASH_FIXTURES / "peppers.png"),
                   help="path to fixture")
    p.add_argument("--iter", type=int, default=5, help="iterations per port")
    p.add_argument("--warm", type=int, default=1,
                   help="warm-up iterations not counted in the median")
    args = p.parse_args()

    fixture = Path(args.fixture)
    if not fixture.exists():
        print(f"fixture not found: {fixture}", file=sys.stderr)
        return 2

    ports = available_ports()
    print(f"Ports available: {', '.join(sorted(ports))}", file=sys.stderr)
    print(f"Fixture:         {fixture.relative_to(ROOT)}", file=sys.stderr)
    print(f"Algorithm:       {args.algo} @ size={args.size}", file=sys.stderr)
    print(f"Iterations:      {args.iter} (after {args.warm} warm-ups)", file=sys.stderr)
    print(file=sys.stderr)

    results: dict[str, dict] = {}
    for name in sorted(ports):
        argv = ports[name]
        try:
            for _ in range(args.warm):
                time_one(argv, args.algo, args.size, fixture)
            times = [time_one(argv, args.algo, args.size, fixture) for _ in range(args.iter)]
        except Exception as e:
            print(f"{name:8s} ERROR: {e}")
            results[name] = {"error": str(e)}
            continue
        median = statistics.median(times)
        minimum = min(times)
        maximum = max(times)
        results[name] = {
            "median_ms": median * 1000,
            "min_ms": minimum * 1000,
            "max_ms": maximum * 1000,
            "times": times,
        }

    # Sort by median, fastest first.
    successful = {n: r for n, r in results.items() if "error" not in r}
    if successful:
        fastest = min(successful.values(), key=lambda r: r["median_ms"])["median_ms"]
        print(f"{'port':<8s}  {'median':>9s}  {'min':>9s}  {'max':>9s}  vs-fastest")
        print(f"{'─'*8}  {'─'*9}  {'─'*9}  {'─'*9}  {'─'*10}")
        for name, r in sorted(successful.items(), key=lambda kv: kv[1]["median_ms"]):
            ratio = r["median_ms"] / fastest
            print(f"{name:<8s}  {r['median_ms']:>7.1f}ms  {r['min_ms']:>7.1f}ms  {r['max_ms']:>7.1f}ms  {ratio:>5.2f}×")
    for name, r in results.items():
        if "error" in r:
            print(f"{name:<8s}  ERROR: {r['error']}")

    print(file=sys.stderr)
    print("NOTE: includes process startup. Steady-state per-hash latency for JIT/VM",
          file=sys.stderr)
    print("ports (JS/Java/Swift/Python) is much lower; use a native in-process",
          file=sys.stderr)
    print("bench tool (vitest bench, JMH, etc.) for that measurement.", file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main())
