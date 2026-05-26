# cross-port-diff — live decoder equivalence harness

Decodes a fixture with **PIL/Pillow** and each of the **5 ports** (Rust, Go, Java, JS, Swift) in separate processes, then diffs the raw RGB(A) byte streams pairwise (or against frozen goldens). Catches drift that the per-port Group 2 tests wouldn't immediately surface.

## Layout

```
tools/cross-port-diff/
├── decode_pil.py            # Python CLI: PIL + ctypes-system-libheif for HEIC
├── decode-go                # Go CLI binary (built from go/imagedecode/cmd/decode-cli)
├── diff_all.py              # orchestrator
└── README.md                # this file
```

Per-port CLIs live inside each port's project tree (so they can use the port's package paths directly):

| Port | Source | Build |
|---|---|---|
| Rust | `rust/rosetta-squint-decode/examples/decode-cli.rs` | `cargo build --release --example decode-cli` |
| Go | `go/imagedecode/cmd/decode-cli/main.go` | `go build -o tools/cross-port-diff/decode-go ./cmd/decode-cli` |
| Java | `java/rosetta-squint-decode/src/main/java/io/rosetta/imagedecode/cli/DecodeCli.java` | `mvn -DskipTests package` (produces `target/decode-cli.jar`) |
| JS | `js/rosetta-squint-decode/scripts/decode-cli.mjs` | `npm run build` (requires `dist/` from `tsc`) |
| Swift | `swift/RosettaSquintDecode/Sources/DecodeCLI/main.swift` | `swift build --product DecodeCLI -c release` |

## Wire format

Every CLI emits the same bytes on stdout per `spec/SPEC.md` §2:

```
[0..4)   u32 LE  width
[4..8)   u32 LE  height
[8..9)   u8      channels (3 or 4)
[9..12)  3 bytes zero padding
[12..)   pixels  row-major, length = width × height × channels
```

This matches `spec/decoded/<format>/valid/<fixture>.bin` byte-for-byte, so `diff_all.py --vs-goldens` is a regression check against the frozen goldens.

## Usage

```bash
# Build all CLIs first (one-time setup)
cd ~/rosetta-squint-decode
( cd rust/rosetta-squint-decode && cargo build --release --example decode-cli )
( cd go/imagedecode && go build -o ../../tools/cross-port-diff/decode-go ./cmd/decode-cli )
( cd java/rosetta-squint-decode && mvn -B -ntp -DskipTests -Dmaven.compiler.source=17 -Dmaven.compiler.target=17 package )
( cd js/rosetta-squint-decode && npm run build )
( cd swift/RosettaSquintDecode && swift build --product DecodeCLI -c release )

# Run on all fixtures
tools/cross-port-diff/diff_all.py

# Just one fixture
tools/cross-port-diff/diff_all.py spec/fixtures/webp/valid/16x16-lossless.webp

# Regression mode (exit 1 on any diff vs frozen goldens)
tools/cross-port-diff/diff_all.py --vs-goldens --regression
```

## Two modes

**Default — cross-port pairwise:** Pick the first available port as reference (PIL if present) and compare every other port's output against it. Reports cluster agreement.

**`--vs-goldens`:** Each port's output compared against `spec/decoded/<fmt>/valid/<fixture>.bin`. This is the **regression-detection mode** — any port that diverges from its own committed golden has either a code regression or an environment/lib version change.

## Special handling

- **HEIC, JS port:** `libheif-js@1.17.1` (WASM) diverges from system libheif 1.17.6 by ±1–2 px on lossy fixtures. The harness applies a `HEIC_MAX_DELTA = 2` tolerance for `(format=heic, port=js)` pairs only. Other ports + HEIC must be byte-exact.

- **HEIC, PIL:** `decode_pil.py` does NOT use `pillow-heif` (which bundles libheif 1.21.2 and diverges ±1 px from system libheif). Instead it uses a `ctypes`-based wrapper around system `libheif.so.1` (1.17.6) so the PIL CLI matches the ports.

## Known cross-port divergences (not regressions)

Running `--vs-goldens` against the current fixture corpus surfaces a few persistent divergences:

| Fixtures | Outlier | Why |
|---|---|---|
| `bmp/valid/rle8-with-delta.bmp` | PIL | The 5 ports interpret BMP RLE8 delta-code semantics differently from PIL; the goldens follow the ports' interpretation (the BMP impl was test-driven, PIL's choice surfaced as wrong). |
| `gif/valid/*transparent.gif` | PIL | PIL zeroes the RGB of the transparent palette index; the 5 ports preserve the original palette RGB. Goldens follow the ports. |
| `pngsuite-tbbn3p08.png` | PIL | PIL trns-chunk handling diverges; ports agree on the spec-correct interpretation. |
| Many PNGs | JS | `pngjs` (the JS port's PNG library) makes different rounding choices than `libpng` + PIL + swift-png + the Rust `image` crate. Documented in `js/rosetta-squint-decode/DECODER_NOTES.md`; tests skip these in Group 3. |
| Lossy HEICs | JS | Bundled WASM libheif build diverges from system libheif by ±1–2 px — handled by tolerance. |

These all show up as `DIFF` rows in `--vs-goldens` mode. A **new** DIFF row appearing in this list (not in the above table) is a real regression to investigate.

## CI

The harness is scriptable for CI:

```yaml
- name: Build all 5 port CLIs + PIL
  run: |
    cd rust/rosetta-squint-decode && cargo build --release --example decode-cli
    cd ../../go/imagedecode && go build -o ../../tools/cross-port-diff/decode-go ./cmd/decode-cli
    cd ../../java/rosetta-squint-decode && mvn -B -ntp -DskipTests -Dmaven.compiler.source=17 -Dmaven.compiler.target=17 package
    cd ../../js/rosetta-squint-decode && npm install && npm run build
    cd ../../swift/RosettaSquintDecode && swift build --product DecodeCLI -c release

- name: Cross-port regression
  run: tools/cross-port-diff/diff_all.py --vs-goldens --regression
```

Not yet wired into `.github/workflows/ci.yml` because the repo is local-only.
