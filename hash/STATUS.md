# rosetta-image-hash — Status & Setup

Byte-exact ports of the Python `imagehash` library (PyPI v4.3.2) to **Python, Java, Go, Rust, JavaScript/TypeScript, and Swift**. The Python port (`python/rosetta_imagehash/`) is a thin extension package that re-exports upstream `imagehash` unchanged and adds one cross-port-stable function — `whash_db4_robust`.

Every port produces the same hex output as the Python `imagehash` package for the same input image, algorithm, and hash size.

---

## What is implemented

| Algorithm | Python ref (`imagehash`) | Python ext (`rosetta_imagehash`) | Rust | Go | JS | Swift | Java |
|---|---|---|---|---|---|---|---|
| `average_hash` | ✓ | ✓ (re-export) | ✓ | ✓ | ✓ | ✓ | ✓ |
| `phash` | ✓ | ✓ (re-export) | ✓ | ✓ | ✓ | ✓ | ✓ |
| `phash_simple` (1-D DCT + mean) | ✓ | ✓ (re-export) | ✓ | ✓ | ✓ | ✓ | ✓ |
| `dhash` (horizontal) | ✓ | ✓ (re-export) | ✓ | ✓ | ✓ | ✓ | ✓ |
| `dhash_vertical` (pre-3.0 back-compat) | ✓ | ✓ (re-export) | ✓ | ✓ | ✓ | ✓ | ✓ |
| `whash` (Haar) | ✓ | ✓ (re-export) | ✓ | ✓ | ✓ | ✓ | ✓ |
| `whash` db4 mode | ✓ | ✓ (re-export) | ✓¹ | ✓¹ | ✓¹ | ✓¹ | ✓¹ |
| `whash_db4_robust` (ours, snap-to-zero bolt-on) | — | ✓² | ✓² | ✓² | ✓² | ✓² | ✓² |
| `colorhash` | ✓ | ✓ (re-export) | ✓ | ✓ | ✓ | ✓ | ✓ |
| hex round-trip (`hex_to_hash`, `hex_to_flathash`) | ✓ | ✓ (re-export) | ✓ | ✓ | ✓ | ✓ | ✓ |
| Hamming distance (`Hash.subtract`) | ✓ | ✓ (re-export) | ✓ | ✓ | ✓ | ✓ | ✓ |
| `crop_resistant_hash` + `ImageMultiHash` | ✓ | ✓ (re-export) | — | — | — | — | — |
| `old_hex_to_hash` (pre-4.0 migration) | ✓ | ✓ (re-export) | — | — | — | — | — |

¹ `whash_db4`: 39–41 of 42 golden cases byte-exact across ports. A handful of pathological synthetic fixtures (`checker-256.png`, `line-art-icon-256.png`) sit at a ULP-level median tie point where PyWavelets' C+SIMD/FMA accumulation resolves the sign differently than portable double arithmetic. Each port skips a documented `ULP_EXEMPT` set; real-world photos are unaffected. See `spec/SPEC.md` §whash_db4.

² `whash_db4_robust` is our cross-port-stable bolt-on (NOT in upstream Python imagehash). Identical pipeline to `whash_db4` up to the LL band, then snaps `|coef| < 1e-12 → 0` before median + threshold. **All 42 goldens pass byte-exact across all 5 ports — no exemptions needed.** Real-world photos produce the same hash as `whash_db4`. Pathological symmetric inputs (checkerboards, etc.) produce a deterministic hash across every port — at the cost of those hashes differing from Python `imagehash.whash(mode='db4')` on the same inputs. Use `whash_db4` for upstream-Python parity; use `whash_db4_robust` for cross-port stability on untrusted inputs. See `spec/SPEC.md` §whash_db4_robust.

The remaining gaps (`crop_resistant_hash` and `old_hex_to_hash`) have a written design at [`docs/superpowers/specs/2026-05-21-rosetta-image-hash-crop-resistant-design.md`](../imagehash/docs/superpowers/specs/2026-05-21-rosetta-image-hash-crop-resistant-design.md) but are deferred — `crop_resistant_hash` requires byte-exact ports of PIL `GaussianBlur(radius=2)` and `MedianFilter(size=3)` plus a new `ImageMultiHash` type, scoped as ~3× the effort of any prior algorithm.

---

## Python reference pin

Goldens were generated against:
- `imagehash==4.3.2`
- `Pillow==10.4.0`
- `numpy>=1.26,<2.0`
- `scipy>=1.11,<1.15`
- `PyWavelets>=1.5,<2.0`

Pinned in `spec/SPEC.md` and `spec/requirements.txt`. The actual versions used live in `spec/goldens.json` under `imagehash_version` / `pillow_version` etc.

---

## How "tested against Python" actually works

**Group 2 — algorithm goldens (the headline parity check):**
`spec/regenerate.py` literally `import imagehash`, runs `imagehash.average_hash`, `dhash`, `phash`, `whash(mode="haar")`, `colorhash` on every fixture × hash-size combination, and freezes the resulting hex strings into `spec/goldens.json`. Each port's Group 2 test loads that JSON and asserts byte-for-byte equality with its own hex output.

**Group 1 — unit reference vectors:**
`spec/gen_unit_cases.py` runs scipy DCT, PIL grayscale conversion, colorsys HSV, and PyWavelets Haar on hand-picked inputs and writes the raw outputs to `dct_cases.json`, `grayscale_cases.json`, `hsv_cases.json`, `haar_cases.json`. Ports test their intermediate math against these. Lanczos has its own format: `spec/lanczos_cases/*.bin` containing PIL's exact resize output.

**Groups 3–5:** invariants (idempotence, hex round-trip, error semantics) — port-internal, no Python comparison.

**What is NOT live-tested against Python:** the Python step runs once at fixture-generation time, then the hex/binary outputs are frozen on disk. A future change in the upstream `imagehash` package or PIL would silently invalidate the goldens until someone re-ran `regenerate.py --check`.

---

## Test counts (current, post-`hash-sweep-v0.1.0` + `robust-db4-v0.1.0`)

| Port | Tests | How to run |
|---|---|---|
| Python ext | 45 | `cd python && pytest` |
| Rust | 65 | `cd rust/rosetta-image-hash && cargo test` |
| Go | all pkgs | `cd go/imagehash && go test ./...` |
| Java | 807 | `cd java && mvn -B -Dmaven.compiler.source=17 -Dmaven.compiler.target=17 test` |
| JS/TS | 69 | `cd js/rosetta-image-hash && npm test` |
| Swift | 59 | `cd swift/RosettaImageHash && swift test` |

All numbers measured on Linux x86-64. **No macOS or Windows runs.**

---

## Installation & build per language

### Rust

```bash
cd rust/rosetta-image-hash
cargo build
cargo test
```

Dependencies: `image = "0.25"` (PNG only feature), `thiserror`. Pure Rust, no system libraries.

### Go

```bash
cd go/imagehash
go test ./...
```

Pure Go. Standard library only — `image/png` is in the stdlib. No `go get` needed beyond the module already pinned in `go.mod`.

### Java (Maven)

```bash
cd java
mvn -B -ntp test
```

Requires JDK 17+. Test deps: JUnit 5, Jackson. No system libraries.

### JavaScript / TypeScript

```bash
cd js/rosetta-image-hash
npm install
npm test       # runs vitest
npm run build  # tsc → dist/
```

Requires Node 18+. Uses `vitest`. PNG decoding via `pngjs` (pure-JS).

### Swift

```bash
cd swift/RosettaImageHash
swift build
swift test
```

Requires Swift 5.9+. The only dependency is [tayloraswift/swift-png](https://github.com/tayloraswift/swift-png) for PNG decoding (pinned to `4.0.0..<4.4.0`). No system C libraries.

**Linux:** install the Swift toolchain from swift.org. The development setup here uses `/opt/swift/swift-5.9.2-RELEASE-ubuntu22.04/usr/bin`. Export it on your PATH first.

**macOS:** Xcode 15+ ships Swift 5.9. `swift test` from the package directory should work. **Not tested by us on macOS** — pure-Swift package with one pure-Swift dependency, so it *should* work without changes.

---

## Regenerating goldens

If you ever change Python ref versions or the algorithm itself:

```bash
cd spec
python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
python regenerate.py          # writes goldens.json + decoded/*.bin + dct/haar/hsv/grayscale cases
python regenerate.py --check  # exits 1 if outputs would differ from committed (excluding timestamp)
python consistency.py         # schema validation
```

Then re-run every port's tests against the new goldens.

---

## Known gaps & caveats

- **No macOS testing.** All `swift test` runs are on Linux. The Mac path should work (pure Swift) but is unverified.
- **No CI execution.** `.github/workflows/ci.yml` exists but the repo is local-only — no GitHub runs have happened.
- **No live Python diff job.** Goldens are frozen at fixture-gen time; no test runs Python and a port side by side.
- **Missing algorithms:** see top table.
- **No published packages.** Nothing is on crates.io, Maven Central, npm, or Swift Package Index yet.

## Security

See [SECURITY.md](./SECURITY.md). Short version: the library operates on already-decoded RGB buffers, so the attack surface is small. All PNG decoder helpers are pure-language (no C FFI). Hash-size validation is in place but the library does NOT impose an upper bound on `hash_size` — the caller is responsible for sane values (recommended `<= 64`). For hashing **untrusted** images, decode them with [rosetta-image-decode](../rosetta-image-decode) first, which enforces `MAX_PIXELS`.

---

## Repository layout

```
rosetta-image-hash/
├── spec/                      # the bit-level specification + golden generator
│   ├── SPEC.md
│   ├── regenerate.py          # imports `imagehash`, writes goldens.json
│   ├── gen_unit_cases.py      # writes Group-1 unit refs (DCT, Haar, HSV, etc.)
│   ├── consistency.py
│   ├── goldens.json           # hex outputs of every (algo, fixture, size)
│   ├── dct_cases.json         # scipy DCT reference vectors
│   ├── grayscale_cases.json   # PIL grayscale reference vectors
│   ├── hsv_cases.json         # colorsys HSV reference vectors
│   ├── haar_cases.json        # PyWavelets Haar reference vectors
│   ├── lanczos_cases/         # PIL Lanczos resize reference .bin files
│   ├── fixtures/              # input PNGs (the things being hashed)
│   └── decoded/               # PIL-decoded RGB buffers, one .bin per fixture
├── rust/rosetta-image-hash/
├── go/imagehash/
├── java/
├── js/rosetta-image-hash/
└── swift/RosettaImageHash/
```
