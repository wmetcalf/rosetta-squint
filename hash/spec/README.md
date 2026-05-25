# `/spec` — the rosetta-image-hash shared core

This directory is the **single source of truth** every language port validates against.

## Contents

- `SPEC.md` — bit-level pipeline rules (PIL grayscale, PIL HSV, PIL Lanczos fixed-point, scipy DCT, pywt Haar). Reading this is the prerequisite for porting.
- `fixtures/` — ~20 PNGs covering edge cases (all-white, all-black, gradients, real images, boundary cases for colorhash thresholds).
- `decoded/<fixture>.rgb.bin` — canonical RGB pixel buffers produced by Pillow's `convert('RGB')`. Ports test the algorithm in isolation from PNG decoder variance by reading these directly.
- `goldens.json` — byte-exact hex outputs of every algorithm × fixture × hash_size combination, produced by Python `imagehash` 4.3.2.
- `goldens.schema.json` — JSON Schema for `goldens.json`.
- `lanczos_cases/`, `dct_cases.json`, `hsv_cases.json`, `grayscale_cases.json`, `haar_cases.json` — Group-1 unit-test reference vectors.
- `regenerate.py` — regenerates `decoded/` and `goldens.json` from `fixtures/`. Idempotent; `--check` returns non-zero on drift.
- `gen_unit_cases.py` — regenerates the Group-1 reference vectors.
- `consistency.py` — validates the structure of `goldens.json` against the schema and verifies SHA-256 of every `decoded/*.rgb.bin` matches the manifest.

## Usage

Install dependencies:
```
pip install -r requirements.txt
```

Regenerate goldens (run after adding a fixture or upgrading a dependency pin):
```
python regenerate.py
python gen_unit_cases.py
```

Check for drift (CI):
```
python regenerate.py --check
python gen_unit_cases.py --check
python consistency.py
```

## Pinned versions

Goldens are produced with:
- `imagehash==4.3.2`
- `Pillow==12.2.0`
- `numpy>=1.26,<2.0`
- `scipy>=1.11,<1.15`
- `PyWavelets>=1.5,<2.0`

If any of these change, run `python regenerate.py` and commit the diff. The
version strings are recorded in `goldens.json` so ports can detect mismatches.
