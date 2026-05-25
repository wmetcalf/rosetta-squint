# rosetta-image-decode

Byte-exact, PIL-compatible image decoders across Java, Go, Rust, JavaScript/TypeScript, and Swift.

Sibling component to the hash library at [`../hash`](../hash). The hash library focuses on perceptual hashing of pre-decoded RGB pixel buffers; this library focuses on producing those buffers from arbitrary image bytes, with byte-exact cross-language parity.

## Status

**v1 shipped — 7 formats × 5 ports byte-exact.** Supported formats: BMP, PNG, GIF, JPEG, WebP, TIFF, HEIC. See [`STATUS.md`](./STATUS.md) for the full per-port × per-format matrix and [`spec/formats.json`](./spec/formats.json) for the machine-readable version.

## Project layout

```
rosetta-image-decode/
├── spec/                                # shared bit-level specification + golden generator
│   ├── SPEC.md                          # spec doc (foundational sections + per-format)
│   ├── formats.json                     # format registry (status, reference libs, ports done)
│   ├── goldens.json                     # SHA256 manifest for decoded pixel buffers
│   ├── regenerate.py                    # PIL-based golden generator
│   ├── consistency.py                   # drift detection
│   ├── fixtures/<format>/               # per-format input files (populated by sub-projects)
│   └── decoded/<format>/<file>.bin      # pre-decoded pixel goldens (populated by sub-projects)
├── java/                                # Java port (added by first format sub-project)
├── go/                                  # Go port
├── rust/                                # Rust port
├── js/                                  # JS/TS port
└── swift/                               # Swift port
```

## Build / test the shared spec

```bash
cd spec
python3 -m venv .venv
.venv/bin/pip install -r requirements.txt

# Regenerate goldens from fixtures (no-op until a sub-project adds fixtures)
.venv/bin/python regenerate.py

# Drift check (CI runs this)
.venv/bin/python regenerate.py --check

# Consistency check (CI runs this too)
.venv/bin/python consistency.py
```

## Format roadmap

In planned implementation order (easy-first):

1. **BMP** — pure-language reimplementation (deterministic)
2. **PNG** — re-uses proven decoders from rosetta-image-hash
3. **GIF** — palette + LZW (deterministic, pure-language)
4. **JPEG** — first format requiring native FFI (libjpeg-turbo across 5 languages)
5. **WebP** — libwebp FFI
6. **TIFF** — strip-based RGB/grayscale subset
7. **HEIC** — libheif FFI (HEVC patent disclaimers apply)
8. **EMF/WMF** — minimal support matching PIL's limitations

See each format's sub-spec section in [`spec/SPEC.md`](./spec/SPEC.md) once that format's sub-project lands.

## API surface (per port)

Every language port exports the same conceptual API, in idiomatic style:

```
decode(bytes)           -> DecodedImage  (or throws DecodeError)
detectFormat(bytes)     -> Format?       (magic-byte sniff)
supportedFormats()      -> [Format]      (what this port handles)
```

A `DecodedImage` contains `width`, `height`, `channels` (3 or 4), `data` (row-major bytes), and `format` (enum tag).

## License

BSD-2-Clause. See [LICENSE](./LICENSE).
