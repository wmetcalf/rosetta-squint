#!/usr/bin/env python3
"""Differential **hash** fuzzer for the squint chain.

The companion `differential_fuzz.py` feeds random / mutated bytes through
every port's full chain (format detect → decode → hash). That tool found
many cross-port disagreements, but they all live in the **decoder** layer —
libjpeg-turbo strict-mode mismatches, javax.imageio's lenient LZW, PIL's
upsampling differences, etc. None of them ever exercise the hash code
because the decoder layer fails out first.

This fuzzer skips the decoder layer entirely: it generates a random RGB(A)
pixel buffer and wraps it in a minimal valid PNG (PIL synthesizes one in
three lines), then runs the same PNG through every port's `squint-cli`.

Since all 6 ports decode that PNG to an identical pixel buffer
(PNG is lossless and the decode side has frozen golden parity for PNG),
this isolates the hash algorithm. Any disagreement here is a real
cross-port bug in the hash code — a violation of the project's core claim
that all 6 ports produce byte-exact hashes for identical pixel input.

Strategy
========

By default the fuzzer mixes two strategies:

1. **random** — uniform random pixel bytes. Catches drift on noise where
   thresholding / quantization may differ across ports.
2. **structured** — generates "photo-like" inputs (smooth gradients with
   noise overlay, checkerboards, solid blocks, color ramps). Catches drift
   on inputs that sit near algorithm thresholds (median, mean, bit
   boundaries).

For each iteration the fuzzer randomly picks **one** algorithm from the
full set (phash, phash_simple, dhash, dhash_vertical, average_hash,
whash_haar, whash_db4, whash_db4_robust, colorhash) — running all 9 algos
× 6 ports × every iteration would be ~54 subprocesses per input, killing
throughput. The random-pick approach gives much broader coverage per
wall-clock second; over thousands of iterations every algo gets exercised
many times.

Usage
=====

    differential_hash_fuzz.py                       # 60s default
    differential_hash_fuzz.py --duration 600        # 10 minutes
    differential_hash_fuzz.py --iterations 5000     # cap by iterations
    differential_hash_fuzz.py --algo phash          # pin one algo
    differential_hash_fuzz.py --seed 42             # deterministic-ish
    differential_hash_fuzz.py --replay <pngfile>    # re-check a saved input
    differential_hash_fuzz.py --strategy structured # photo-like inputs

Exit codes
==========

    0 — fuzz completed, no disagreements found
    1 — at least one hash-algorithm disagreement (paths printed to stderr)
    2 — harness error (no ports available, etc.)
"""
from __future__ import annotations

import argparse
import hashlib
import io
import os
import random
import shutil
import subprocess
import sys
import tempfile
import time
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent.parent
CORPUS_DIR = Path(__file__).parent / "hash-fuzz-corpus" / "disagreements"

# Algorithms — match the spec names used by every squint-cli port.
# colorhash uses `size` as binbits (legal: 3 or 8); the rest use it as
# `hash_size`. crop_resistant_hash is excluded — it segments the image
# via flood-fill and produces variable-length output, which is not a
# clean fit for the size-8 single-hex-string comparison this fuzzer does.
ALL_ALGOS: list[str] = [
    "phash",
    "phash_simple",
    "dhash",
    "dhash_vertical",
    "average_hash",
    "whash_haar",
    "whash_db4",
    "whash_db4_robust",
    "colorhash",
]


# ─── port discovery (copied from differential_fuzz.py to keep this script
#     standalone — no inter-tool import dependency) ───────────────────────

def _probe_turbojpeg() -> tuple[str | None, str | None]:
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
    """Return only ports whose CLI binary exists. Same probe as differential_fuzz.py."""
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
            "io.rosetta.squint.cli.SquintCli",
        ]
    return out


# ─── pixel buffer generators ────────────────────────────────────────────

def _pick_shape(rng: random.Random) -> tuple[int, int, int]:
    """Pick (w, h, channels). Sizes 4..256, channels 3 or 4."""
    w = rng.randint(4, 256)
    h = rng.randint(4, 256)
    c = rng.choice((3, 4))
    return w, h, c


def _gen_random_pixels(rng: random.Random, w: int, h: int, c: int) -> bytes:
    return rng.randbytes(w * h * c)


def _gen_structured_pixels(rng: random.Random, w: int, h: int, c: int) -> bytes:
    """Photo-like inputs near algorithm thresholds.

    Variants:
      * solid block (uniform color)
      * 2-tone vertical / horizontal split
      * checkerboard at random period
      * smooth horizontal/vertical gradient
      * gradient + uniform noise overlay (low amplitude)
      * random per-pixel from a small palette
    """
    variant = rng.randrange(6)
    has_alpha = c == 4
    buf = bytearray(w * h * c)

    if variant == 0:
        # solid block
        rgba = [rng.randrange(256) for _ in range(c)]
        for i in range(0, len(buf), c):
            buf[i:i + c] = bytes(rgba)

    elif variant == 1:
        # 2-tone split, vertical or horizontal
        rgba_a = [rng.randrange(256) for _ in range(c)]
        rgba_b = [rng.randrange(256) for _ in range(c)]
        vertical = rng.random() < 0.5
        for y in range(h):
            for x in range(w):
                px = rgba_a if (x if vertical else y) < (w if vertical else h) // 2 else rgba_b
                off = (y * w + x) * c
                buf[off:off + c] = bytes(px)

    elif variant == 2:
        # checkerboard
        period = rng.randint(1, max(1, min(w, h) // 2))
        rgba_a = [rng.randrange(256) for _ in range(c)]
        rgba_b = [rng.randrange(256) for _ in range(c)]
        for y in range(h):
            for x in range(w):
                cell = ((x // period) + (y // period)) & 1
                px = rgba_a if cell == 0 else rgba_b
                off = (y * w + x) * c
                buf[off:off + c] = bytes(px)

    elif variant == 3:
        # gradient (horizontal or vertical), per-channel direction
        horizontal = rng.random() < 0.5
        for y in range(h):
            for x in range(w):
                t = (x if horizontal else y) / max(1, (w if horizontal else h) - 1)
                px = [int(t * 255) & 0xFF for _ in range(3)]
                if has_alpha:
                    px.append(255)
                off = (y * w + x) * c
                buf[off:off + c] = bytes(px)

    elif variant == 4:
        # gradient + small uniform noise
        horizontal = rng.random() < 0.5
        amp = rng.randint(1, 32)
        for y in range(h):
            for x in range(w):
                t = (x if horizontal else y) / max(1, (w if horizontal else h) - 1)
                base = int(t * 255)
                px = [max(0, min(255, base + rng.randint(-amp, amp))) for _ in range(3)]
                if has_alpha:
                    px.append(255)
                off = (y * w + x) * c
                buf[off:off + c] = bytes(px)

    else:
        # small random palette
        palette_size = rng.randint(2, 8)
        palette = [bytes(rng.randrange(256) for _ in range(c)) for _ in range(palette_size)]
        for i in range(0, len(buf), c):
            buf[i:i + c] = palette[rng.randrange(palette_size)]

    return bytes(buf)


def _pixels_to_png(pixels: bytes, w: int, h: int, c: int) -> bytes:
    """Wrap raw pixels in a minimal valid PNG via PIL.
    PNG is lossless so all 6 decoders produce byte-exact pixels back."""
    mode = "RGB" if c == 3 else "RGBA"
    img = Image.frombytes(mode, (w, h), pixels)
    buf = io.BytesIO()
    img.save(buf, "PNG")
    return buf.getvalue()


# ─── port runner / classifier (same shape as differential_fuzz.py) ──────

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
        msg = (proc.stderr.decode(errors="replace").strip().splitlines() or [""])[0]
        return "", f"exit {proc.returncode}: {msg[:200]}"
    return proc.stdout.decode(errors="replace").strip(), None


def _check_input(png_bytes: bytes, ports: dict[str, list[str]], algo: str,
                 size: int, timeout: float) -> dict[str, tuple[str, str | None]]:
    """Run every port against the input PNG. Returns {port_name: (hex, err)}."""
    with tempfile.NamedTemporaryFile(suffix=".png", delete=False) as tf:
        tf.write(png_bytes)
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
    """Return (category, summary). Same 4 categories as differential_fuzz.py."""
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
        groups: dict[str, list[str]] = {}
        for n, h in hexes.items():
            groups.setdefault(h, []).append(n)
        parts = [f"{h[:12]}…={','.join(sorted(g))}" for h, g in sorted(groups.items())]
        return "hex-disagreement", "; ".join(parts)
    return "empty", "no ports configured"


def _save_disagreement(png_bytes: bytes, pixels: bytes, shape: tuple[int, int, int],
                       algo: str, size: int, strategy: str,
                       results: dict[str, tuple[str, str | None]],
                       category: str, summary: str) -> Path:
    CORPUS_DIR.mkdir(parents=True, exist_ok=True)
    h = hashlib.sha256(png_bytes).hexdigest()[:16]
    base = CORPUS_DIR / f"{category}-{algo}-{h}"
    base.with_suffix(".png").write_bytes(png_bytes)
    # Save raw pixels too — these are the actual input that mattered, the PNG
    # is just a transport wrapper. Useful for triage / reproducing in Python.
    base.with_suffix(".pixels").write_bytes(pixels)
    lines = [
        f"# differential hash fuzz disagreement",
        f"# category:   {category}",
        f"# algo:       {algo}",
        f"# size:       {size}",
        f"# strategy:   {strategy}",
        f"# summary:    {summary}",
        f"# shape:      w={shape[0]} h={shape[1]} channels={shape[2]}",
        f"# png_sha256: {hashlib.sha256(png_bytes).hexdigest()}",
        f"# png_size:   {len(png_bytes)} bytes",
        f"# pixel_size: {len(pixels)} bytes ({shape[0]}*{shape[1]}*{shape[2]})",
        "",
    ]
    for name in sorted(results):
        hex_str, err = results[name]
        lines.append(f"{name}: hex={hex_str!r} err={err!r}")
    base.with_suffix(".log").write_text("\n".join(lines) + "\n")
    return base


def _replay(replay_path: Path, ports: dict[str, list[str]],
            algos: list[str], size: int, timeout: float) -> int:
    """Run a saved PNG through every port × every requested algo and report."""
    if not replay_path.is_file():
        print(f"replay file not found: {replay_path}", file=sys.stderr)
        return 2
    data = replay_path.read_bytes()
    bad = 0
    for algo in algos:
        results = _check_input(data, ports, algo, size, timeout)
        cat, summary = _classify(results)
        print(f"replay {replay_path.name} [{algo}]: category={cat} {summary}")
        for name in sorted(results):
            h, e = results[name]
            print(f"  {name}: hex={h or '(none)'} err={e or '(none)'}")
        if cat in ("mixed", "hex-disagreement"):
            bad += 1
    return 0 if bad == 0 else 1


def main() -> int:
    p = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    p.add_argument("--duration", type=float, default=60.0,
                   help="run for at most N seconds (default: 60)")
    p.add_argument("--iterations", type=int, default=100000,
                   help="stop after N inputs, whichever first (default: 100000)")
    p.add_argument("--algo", default="random",
                   help="algorithm to fuzz, or 'random' to pick uniformly per iter (default: random)")
    p.add_argument("--size", type=int, default=8, help="hash_size / binbits (default: 8)")
    p.add_argument("--timeout", type=float, default=10.0,
                   help="per-port subprocess timeout in seconds (default: 10)")
    p.add_argument("--seed", type=int, default=None, help="RNG seed (default: random)")
    p.add_argument("--replay", type=Path, default=None,
                   help="re-run a single saved PNG from corpus (skip fuzzing)")
    p.add_argument("--strategy", choices=["random", "structured", "mixed"],
                   default="mixed", help="input strategy (default: mixed)")
    p.add_argument("--print-every", type=int, default=50,
                   help="print progress every N iterations (default: 50)")
    args = p.parse_args()

    if args.algo != "random" and args.algo not in ALL_ALGOS:
        print(f"unknown algo: {args.algo}; valid: random, {', '.join(ALL_ALGOS)}",
              file=sys.stderr)
        return 2

    ports = _ports()
    if len(ports) < 2:
        print(f"need at least 2 ports for differential fuzzing; found: {sorted(ports)}",
              file=sys.stderr)
        return 2

    print(f"differential hash fuzz: {len(ports)} ports = {sorted(ports)}",
          file=sys.stderr)
    if args.algo == "random":
        print(f"algorithms: random per iteration from {ALL_ALGOS}", file=sys.stderr)
    else:
        print(f"algorithm: {args.algo} @ size={args.size}", file=sys.stderr)

    if args.replay:
        algos = ALL_ALGOS if args.algo == "random" else [args.algo]
        return _replay(args.replay, ports, algos, args.size, args.timeout)

    rng = random.Random(args.seed) if args.seed is not None else random.SystemRandom()
    strategies = {
        "random":     ["random"],
        "structured": ["structured"],
        "mixed":      ["random", "structured"],
    }[args.strategy]
    print(f"strategy: {args.strategy}", file=sys.stderr)
    print(f"duration: <= {args.duration}s, iterations: <= {args.iterations}",
          file=sys.stderr)
    print(file=sys.stderr)

    start = time.monotonic()
    counts = {"all-agree-success": 0, "all-agree-error": 0,
              "mixed": 0, "hex-disagreement": 0}
    # per-algo counts of hash-disagreements — the headline finding for this tool
    per_algo_disagreements: dict[str, int] = {a: 0 for a in ALL_ALGOS}
    disagreement_paths: list[Path] = []

    iteration = 0
    while iteration < args.iterations and (time.monotonic() - start) < args.duration:
        algo = rng.choice(ALL_ALGOS) if args.algo == "random" else args.algo
        # Pick a shape compatible with the chosen algo / size.
        # whash requires hash_size to be a power of 2 and ≤ min(w,h);
        # the others tolerate larger images comfortably. Constrain min
        # dimension so size=8 hashes don't ValueError.
        w, h, c = _pick_shape(rng)
        min_dim = args.size
        if w < min_dim: w = min_dim
        if h < min_dim: h = min_dim

        strat = rng.choice(strategies)
        if strat == "structured":
            pixels = _gen_structured_pixels(rng, w, h, c)
        else:
            pixels = _gen_random_pixels(rng, w, h, c)

        try:
            png_bytes = _pixels_to_png(pixels, w, h, c)
        except Exception as e:
            print(f"  iter={iteration}: PNG synthesis failed ({e}); skipping",
                  file=sys.stderr)
            iteration += 1
            continue

        results = _check_input(png_bytes, ports, algo, args.size, args.timeout)
        category, summary = _classify(results)
        counts[category] = counts.get(category, 0) + 1

        if category in ("mixed", "hex-disagreement"):
            if category == "hex-disagreement":
                per_algo_disagreements[algo] = per_algo_disagreements.get(algo, 0) + 1
            path = _save_disagreement(png_bytes, pixels, (w, h, c), algo, args.size,
                                      strat, results, category, summary)
            disagreement_paths.append(path)
            print(f"DISAGREEMENT [{category}] iter={iteration} algo={algo} "
                  f"shape={w}x{h}x{c} saved={path.name} {summary}")

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
    print(f"=== differential hash fuzz summary ===", file=sys.stderr)
    print(f"  iterations:        {iteration}", file=sys.stderr)
    print(f"  elapsed:           {elapsed:.1f}s "
          f"({iteration/elapsed if elapsed > 0 else 0:.1f}/s)", file=sys.stderr)
    print(f"  all-agree-success: {counts['all-agree-success']}", file=sys.stderr)
    print(f"  all-agree-error:   {counts['all-agree-error']}", file=sys.stderr)
    print(f"  mixed:             {counts['mixed']}", file=sys.stderr)
    print(f"  hex-disagreement:  {counts['hex-disagreement']}", file=sys.stderr)
    if any(v > 0 for v in per_algo_disagreements.values()):
        print(f"  per-algo hex-disagreement breakdown:", file=sys.stderr)
        for a in ALL_ALGOS:
            v = per_algo_disagreements.get(a, 0)
            if v > 0:
                print(f"    {a}: {v}", file=sys.stderr)
    if disagreement_paths:
        print(f"  disagreement corpus: {CORPUS_DIR}", file=sys.stderr)
        return 1
    print(f"  no disagreements — all {len(ports)} ports agreed on every input.",
          file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main())
