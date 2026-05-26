#!/usr/bin/env python3
"""Differential fuzzer for the squint chain.

For each randomly-generated input, run every available port's `squint-cli`
binary and assert all ports agree on the outcome — either all produce the
same hex hash, or all error out. Mixed results (one port hashes successfully,
another errors) or matching successes with differing hex strings are flagged
as differential disagreements.

What this catches that the single-port fuzzers don't
====================================================

The existing Rust + Go cargo-fuzz / Go-fuzz targets enforce "this port never
panics on any input". They don't check that "all 6 ports agree on what bytes
this input decodes to". A subtle DCT/IDCT rounding difference, an off-by-one
in an FFI-wrapped boundary check, or an unhandled JPEG marker variant can
make one port silently produce different pixels (and therefore a different
hash) than the others, without any panic — exactly the kind of cross-port
drift this differential fuzzer surfaces.

Strategies
==========

By default the fuzzer mixes three input sources:

1. Pure random bytes from os.urandom — exercises the format-detection +
   "is this even an image?" entry path. Most inputs will fail detect_format.
2. Magic-byte-prefixed random bytes — mirrors `decode_with_prefix.rs`. First
   random byte selects one of 7 format prefixes, then random body. Forces
   the per-format decoders to actually parse something.
3. Mutated seeds from the existing fixture corpus — flips a small number of
   random bits in a randomly-chosen valid fixture. Stays close to "real
   image" but introduces hostile twists that the format detect will accept
   and the decoder must handle gracefully.

For each input, every available squint-cli port runs `phash 8 <tmpfile>`.
Disagreements get saved to ``corpus/disagreements/`` keyed by SHA256 of the
input bytes so re-running with the same input is deterministic.

Usage
=====

    differential_fuzz.py                    # run for default 60 seconds
    differential_fuzz.py --duration 600     # 10 minutes
    differential_fuzz.py --iterations 5000  # stop after N inputs (whichever first)
    differential_fuzz.py --algo dhash       # use dhash instead of phash
    differential_fuzz.py --size 16          # different hash size
    differential_fuzz.py --seed 42          # deterministic-ish run
    differential_fuzz.py --replay <hashfile>  # re-run a saved disagreement input

Exit codes
==========

    0 — fuzz completed, no disagreements found
    1 — at least one differential disagreement (paths printed to stderr)
    2 — harness error (no ports available, etc.)
"""
from __future__ import annotations

import argparse
import hashlib
import os
import random
import shutil
import subprocess
import sys
import tempfile
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent.parent
HASH_FIXTURES = ROOT / "hash" / "spec" / "fixtures"
DECODE_FIXTURES = ROOT / "decode" / "spec" / "fixtures"
CORPUS_DIR = Path(__file__).parent / "fuzz-corpus" / "disagreements"

# Format magic prefixes — same set as decode_with_prefix.rs / FuzzDecodeWithPrefix.
PREFIXES: list[bytes] = [
    b"\x89PNG\r\n\x1a\n",                                           # PNG
    b"\xFF\xD8\xFF\xE0",                                            # JPEG (JFIF)
    b"GIF89a",                                                      # GIF
    b"BM",                                                          # BMP
    b"RIFF\x00\x00\x00\x00WEBP",                                    # WebP RIFF
    b"II*\x00",                                                     # TIFF (LE)
    b"\x00\x00\x00\x18ftypheic",                                    # HEIC
]


def _probe_turbojpeg() -> tuple[str | None, str | None]:
    """Same probe pattern as diff_all_squint.py — sourced inline so this script
    has no inter-tool import dependency."""
    env_lib = os.environ.get("TURBOJPEG_LIB_PATH")
    lib_candidates = [
        env_lib,
        "/usr/lib/x86_64-linux-gnu",
        "/usr/lib/aarch64-linux-gnu",
        "/usr/lib64",
        "/opt/homebrew/lib",
        "/usr/local/lib",
    ]
    lib_dir = None
    for cand in lib_candidates:
        if not cand:
            continue
        p = Path(cand)
        if any((p / n).exists() for n in ("libturbojpeg.so", "libturbojpeg.so.0", "libturbojpeg.dylib")):
            lib_dir = cand
            break
    env_jar = os.environ.get("TURBOJPEG_JAR_PATH")
    jar_candidates = [
        env_jar,
        "/usr/share/java/turbojpeg.jar",
        "/opt/homebrew/share/java/turbojpeg.jar",
        "/usr/local/share/java/turbojpeg.jar",
    ]
    jar_path = next((c for c in jar_candidates if c and Path(c).exists()), None)
    return lib_dir, jar_path


def _ports() -> dict[str, list[str]]:
    """Return only ports whose CLI binary exists. Skip Java if turbojpeg isn't found."""
    out: dict[str, list[str]] = {}
    candidates: dict[str, list[str]] = {
        "python": [sys.executable, str(ROOT / "tools" / "cross-squint-diff" / "squint_cli.py")],
        "rust":   [str(ROOT / "squint/rust/rosetta-squint/target/release/examples/squint-cli")],
        "go":     [str(ROOT / "tools/cross-squint-diff/squint-go")],
        "js":     ["node", str(ROOT / "squint/js/rosetta-squint/scripts/squint-cli.mjs")],
        "swift":  [str(ROOT / "squint/swift/RosettaSquint/.build/release/SquintCLI")],
    }
    for name, argv in candidates.items():
        first = argv[0]
        if "/" in first or first.endswith((".py", ".mjs")):
            if not Path(first).exists():
                continue
        elif shutil.which(first) is None:
            continue
        out[name] = argv
    lib_dir, jar_path = _probe_turbojpeg()
    if lib_dir and jar_path and shutil.which("java"):
        out["java"] = [
            "java",
            f"-Djava.library.path={lib_dir}",
            "--enable-native-access=ALL-UNNAMED",
            "-cp",
            f"{ROOT}/squint/java/rosetta-squint/target/squint-cli.jar:{jar_path}",
            "io.github.wmetcalf.rosettasquint.cli.SquintCli",
        ]
    return out


def _gen_random(rng: random.Random, size_range: tuple[int, int] = (16, 4096)) -> bytes:
    n = rng.randint(*size_range)
    return rng.randbytes(n)


def _gen_prefixed(rng: random.Random, size_range: tuple[int, int] = (16, 4096)) -> bytes:
    prefix = PREFIXES[rng.randrange(len(PREFIXES))]
    body = rng.randbytes(rng.randint(*size_range))
    return prefix + body


def _gen_mutated(rng: random.Random, fixtures: list[Path]) -> bytes:
    if not fixtures:
        return _gen_random(rng)
    src = rng.choice(fixtures)
    try:
        data = bytearray(src.read_bytes())
    except OSError:
        return _gen_random(rng)
    if not data:
        return bytes(data)
    # Flip 1-8 random bits. Keeps "looks like a real image" structure but
    # introduces hostile twists at random offsets.
    flips = rng.randint(1, 8)
    for _ in range(flips):
        idx = rng.randrange(len(data))
        data[idx] ^= 1 << rng.randrange(8)
    return bytes(data)


def _run_port(argv: list[str], path: Path, algo: str, size: int,
              timeout: float) -> tuple[str, str | None]:
    """Return (hex, err). hex is "" on error."""
    cmd = argv + [algo, str(size), str(path)]
    try:
        proc = subprocess.run(cmd, capture_output=True, timeout=timeout)
    except subprocess.TimeoutExpired:
        return "", "timeout"
    except OSError as e:
        return "", f"exec failed: {e}"
    if proc.returncode != 0:
        # Sanitize the err for storage — keep first line, cap at 200 chars.
        msg = (proc.stderr.decode(errors="replace").strip().splitlines() or [""])[0]
        return "", f"exit {proc.returncode}: {msg[:200]}"
    return proc.stdout.decode(errors="replace").strip(), None


def _check_input(input_bytes: bytes, ports: dict[str, list[str]], algo: str, size: int,
                 timeout: float) -> dict[str, tuple[str, str | None]]:
    """Run every port against the input. Returns {port_name: (hex, err)}."""
    with tempfile.NamedTemporaryFile(suffix=".bin", delete=False) as tf:
        tf.write(input_bytes)
        tmp_path = Path(tf.name)
    try:
        results = {}
        for name, argv in ports.items():
            results[name] = _run_port(argv, tmp_path, algo, size, timeout)
        return results
    finally:
        try: tmp_path.unlink()
        except OSError: pass


def _classify(results: dict[str, tuple[str, str | None]]) -> tuple[str, str]:
    """Return (category, summary) for a result set.

    Categories:
      'all-agree-success': all ports produced the same hex hash
      'all-agree-error':   all ports errored (any kind)
      'mixed':             some ports succeeded, others errored (real disagreement)
      'hex-disagreement':  all succeeded but hex differs (real disagreement)
    """
    hexes = {n: h for n, (h, e) in results.items() if e is None}
    errs = {n: e for n, (h, e) in results.items() if e is not None}
    if hexes and errs:
        success_names = ",".join(sorted(hexes))
        error_names = ",".join(sorted(errs))
        return "mixed", f"success=[{success_names}] error=[{error_names}]"
    if errs and not hexes:
        return "all-agree-error", "all ports errored"
    if hexes and not errs:
        unique = set(hexes.values())
        if len(unique) == 1:
            return "all-agree-success", f"hex={next(iter(unique))[:16]}…"
        # Hex disagreement — group ports by their hash for clarity.
        groups: dict[str, list[str]] = {}
        for n, h in hexes.items():
            groups.setdefault(h, []).append(n)
        parts = [f"{h[:12]}…={','.join(sorted(g))}" for h, g in sorted(groups.items())]
        return "hex-disagreement", "; ".join(parts)
    return "empty", "no ports configured"


def _save_disagreement(input_bytes: bytes, results: dict[str, tuple[str, str | None]],
                       category: str, summary: str) -> Path:
    CORPUS_DIR.mkdir(parents=True, exist_ok=True)
    h = hashlib.sha256(input_bytes).hexdigest()[:16]
    base = CORPUS_DIR / f"{category}-{h}"
    base.with_suffix(".bin").write_bytes(input_bytes)
    # Sidecar log so the disagreement is self-describing.
    lines = [f"# differential fuzz disagreement", f"# category: {category}",
             f"# summary: {summary}", f"# sha256: {hashlib.sha256(input_bytes).hexdigest()}",
             f"# input_size: {len(input_bytes)} bytes", ""]
    for name in sorted(results):
        hex_str, err = results[name]
        lines.append(f"{name}: hex={hex_str!r} err={err!r}")
    base.with_suffix(".log").write_text("\n".join(lines) + "\n")
    return base


def _seed_fixtures() -> list[Path]:
    """Collect all valid fixtures across hash + decode for the mutator."""
    fixtures: list[Path] = []
    for root in (HASH_FIXTURES, DECODE_FIXTURES):
        if root.is_dir():
            for p in root.rglob("*"):
                if p.is_file() and p.suffix.lower() in {".png", ".jpg", ".gif", ".bmp", ".webp", ".tif", ".heic"}:
                    fixtures.append(p)
    return fixtures


def _replay(replay_path: Path, ports: dict[str, list[str]], algo: str, size: int,
            timeout: float) -> int:
    """Run a single saved disagreement input and report. Useful for re-checking
    after a fix lands."""
    if not replay_path.is_file():
        print(f"replay file not found: {replay_path}", file=sys.stderr)
        return 2
    data = replay_path.read_bytes()
    results = _check_input(data, ports, algo, size, timeout)
    cat, summary = _classify(results)
    print(f"replay {replay_path.name}: category={cat} {summary}")
    for name in sorted(results):
        h, e = results[name]
        print(f"  {name}: hex={h or '(none)'} err={e or '(none)'}")
    return 0 if cat in ("all-agree-success", "all-agree-error") else 1


def main() -> int:
    p = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    p.add_argument("--duration", type=float, default=60.0,
                   help="run for at most N seconds (default: 60)")
    p.add_argument("--iterations", type=int, default=10000,
                   help="stop after N inputs, whichever comes first (default: 10000)")
    p.add_argument("--algo", default="phash", help="algorithm to fuzz (default: phash)")
    p.add_argument("--size", type=int, default=8, help="hash_size / binbits (default: 8)")
    p.add_argument("--timeout", type=float, default=10.0,
                   help="per-port subprocess timeout in seconds (default: 10)")
    p.add_argument("--seed", type=int, default=None, help="RNG seed (default: random)")
    p.add_argument("--replay", type=Path, default=None,
                   help="re-run a single saved input from corpus (skip fuzzing)")
    p.add_argument("--strategy", choices=["random", "prefixed", "mutated", "mixed"],
                   default="mixed", help="input strategy (default: mixed)")
    p.add_argument("--print-every", type=int, default=100,
                   help="print progress every N iterations (default: 100)")
    p.add_argument("--strict", action="store_true",
                   help="exit 1 on any disagreement (mixed or hex). Default: exit 1 "
                        "only on hex-disagreement; 'mixed' findings (decode-layer "
                        "format-detection / tolerance drift documented in decode/"
                        "spec/SPEC.md §3.2) are reported but not counted as failures.")
    args = p.parse_args()

    ports = _ports()
    if len(ports) < 2:
        print(f"need at least 2 ports for differential fuzzing; found: {sorted(ports)}",
              file=sys.stderr)
        return 2

    print(f"differential fuzz: {len(ports)} ports = {sorted(ports)}", file=sys.stderr)
    print(f"algorithm: {args.algo} @ size={args.size}", file=sys.stderr)

    if args.replay:
        return _replay(args.replay, ports, args.algo, args.size, args.timeout)

    rng = random.Random(args.seed) if args.seed is not None else random.SystemRandom()
    fixtures = _seed_fixtures()
    if not fixtures:
        print("WARNING: no fixtures found for mutator; falling back to random+prefixed only",
              file=sys.stderr)
    print(f"mutator seeded with {len(fixtures)} fixtures", file=sys.stderr)
    print(f"strategy: {args.strategy}", file=sys.stderr)
    print(f"duration: <= {args.duration}s, iterations: <= {args.iterations}", file=sys.stderr)
    print(file=sys.stderr)

    strategies = {
        "random":   [_gen_random],
        "prefixed": [_gen_prefixed],
        "mutated":  [_gen_mutated],
        "mixed":    [_gen_random, _gen_prefixed, _gen_mutated],
    }[args.strategy]

    start = time.monotonic()
    counts = {"all-agree-success": 0, "all-agree-error": 0, "mixed": 0, "hex-disagreement": 0}
    disagreement_paths: list[Path] = []

    iteration = 0
    while iteration < args.iterations and (time.monotonic() - start) < args.duration:
        gen = rng.choice(strategies)
        if gen is _gen_mutated:
            data = gen(rng, fixtures)
        else:
            data = gen(rng)
        results = _check_input(data, ports, args.algo, args.size, args.timeout)
        category, summary = _classify(results)
        counts[category] = counts.get(category, 0) + 1
        if category in ("mixed", "hex-disagreement"):
            path = _save_disagreement(data, results, category, summary)
            disagreement_paths.append(path)
            print(f"DISAGREEMENT [{category}] iter={iteration} saved={path.name} {summary}")
        if iteration > 0 and iteration % args.print_every == 0:
            elapsed = time.monotonic() - start
            rate = iteration / elapsed if elapsed > 0 else 0
            print(f"  progress: {iteration} iters / {elapsed:.1f}s "
                  f"({rate:.1f} iters/s) — agree-success={counts['all-agree-success']} "
                  f"agree-error={counts['all-agree-error']} "
                  f"mixed={counts['mixed']} hex-disagreement={counts['hex-disagreement']}",
                  file=sys.stderr)
        iteration += 1

    elapsed = time.monotonic() - start
    print(file=sys.stderr)
    print(f"=== differential fuzz summary ===", file=sys.stderr)
    print(f"  iterations:        {iteration}", file=sys.stderr)
    print(f"  elapsed:           {elapsed:.1f}s ({iteration/elapsed if elapsed>0 else 0:.1f}/s)",
          file=sys.stderr)
    print(f"  all-agree-success: {counts['all-agree-success']}", file=sys.stderr)
    print(f"  all-agree-error:   {counts['all-agree-error']}", file=sys.stderr)
    print(f"  mixed:             {counts['mixed']}", file=sys.stderr)
    print(f"  hex-disagreement:  {counts['hex-disagreement']}", file=sys.stderr)
    if disagreement_paths:
        print(f"  disagreement corpus: {CORPUS_DIR}", file=sys.stderr)
    # Exit policy: hex-disagreement always fails (real cross-port hash drift).
    # 'mixed' findings (decode-layer format-detection / tolerance drift) are
    # documented v1 limitations — only fail on them in --strict mode.
    failing = counts["hex-disagreement"] + (counts["mixed"] if args.strict else 0)
    if failing > 0:
        return 1
    if counts["mixed"] > 0:
        print(f"  note: {counts['mixed']} 'mixed' finding(s) reported but not "
              f"counted as failures (documented v1 tolerance drift; use --strict "
              f"to fail on them).", file=sys.stderr)
    else:
        print(f"  no disagreements — all {len(ports)} ports agreed on every input.",
              file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main())
