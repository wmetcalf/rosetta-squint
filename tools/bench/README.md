# Cross-port benchmark

Times the end-to-end squint chain (process startup → decode → hash → print) for each port. Lets you see relative performance across all 6 implementations on the same input.

## Run

```bash
# Default: phash @ 8 on peppers.png, 5 iters
tools/bench/bench.py

# Or:
make bench

# Custom:
tools/bench/bench.py --algo whash_db4 --iter 20 --fixture path/to.jpg
```

## Sample results

On the dev box (Linux x86-64, all CLIs built in release mode), `phash @ 8` on `peppers.png`:

| Port | Median | vs fastest |
|---|---|---|
| Rust | ~9 ms | 1.00× |
| Swift | ~19 ms | 2.2× |
| Go | ~26 ms | 3.0× |
| JS (Node) | ~150 ms | 17× |
| Java | ~170 ms | 19× |
| Python | ~230 ms | 24× |

## What the numbers don't show

These are **end-to-end CLI invocation** times. For each port that includes:
- Process startup (JVM cold-start = ~100 ms; Python interpreter + numpy/scipy imports = ~150 ms; Node + Vitest's transform chain = lots; even native binaries pay ~5 ms of dlopen/PLT setup)
- Library init (JS: WASM module compile for mozjpeg)
- The actual decode + hash work
- Print to stdout

**For steady-state per-hash latency** (e.g. a long-running service hashing thousands of images), the JIT/VM ports are dramatically faster than these numbers suggest — most of their cost is paid once at startup. Use a port's in-process benchmark for that measurement:

| Port | In-process bench tool |
|---|---|
| Rust | `cargo bench` (criterion or built-in `#[bench]`) |
| Go | `go test -bench=.` |
| Java | JMH (`mvn -P bench`) |
| JS | `vitest bench` or `tinybench` |
| Swift | `XCTest`'s `measure { ... }` |
| Python | `pytest-benchmark` or `timeit` |

## When to use this harness vs. native benchmarks

- **This harness:** comparing CLI invocation cost. Realistic for one-shot use (e.g. a build script hashing one screenshot per CI run).
- **Native bench:** comparing algorithmic cost. Realistic for batch / server workloads.

The two answer different questions; both are interesting.
