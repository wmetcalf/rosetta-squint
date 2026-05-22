# rosetta_imagehash — Python port (extensions over `imagehash`)

A thin wrapper around the Python [`imagehash`](https://pypi.org/project/ImageHash/) package (4.3.2+) that adds the cross-port-stable extensions implemented across the 5 ports of `rosetta-image-hash` (Rust, Go, Java, JS/TS, Swift).

The v1 surface that's actually different from upstream `imagehash` is **one function**: `whash_db4_robust`. Everything else (`phash`, `dhash`, `whash(mode='haar')`, `whash(mode='db4')`, `phash_simple`, `dhash_vertical`, `colorhash`, `crop_resistant_hash`, hex round-trip) is re-exported from upstream unchanged.

## Why this package exists

Python `imagehash.whash(mode='db4')` and our 5 ports' `whashDb4` produce **the same hash** for ~93% of test fixtures. The other ~7% are pathological synthetic inputs (checkerboards, high-contrast geometric icons) where the algorithm computes a wavelet LL band whose values are mathematically exactly 0 — but float64 rounding produces tiny noise (~1e-17). The "is `coef > median`" comparison then depends on which side of zero the noise lands, which depends on the order of float additions, which depends on SIMD/FMA and compiler.

PyWavelets' C+SIMD inner loop, Rust's f64 accumulation, Java's double loops, Go's column-first traversal, JS's Number arithmetic, and Swift's Double accumulation can disagree by ~10 bits out of 256 on those inputs. **Every implementation is "right" per IEEE 754 — the input is at a tie point.**

`whash_db4_robust` resolves this by snapping `|coef| < 1e-12` to exactly 0 *before* the median + threshold step. All implementations now agree (cross-port stable) at the cost of those specific inputs producing different hex than `imagehash.whash(mode='db4')`.

For real photos this never triggers; both functions return identical output.

## Install

```bash
pip install -e .              # from python/
```

Not yet on PyPI.

### Version policy

The wrapper pins upstream dependencies to the exact versions our goldens were validated against:

| Dep | Pinned to | Why |
|---|---|---|
| `imagehash` | `==4.3.2` | Algorithm output is what generates `spec/goldens.json`. New upstream release → potential drift → release of `rosetta-imagehash` after re-validation. |
| `Pillow` | `==10.4.*` | PIL's Lanczos resize, `Image.crop` rounding, `ImageFilter.GaussianBlur` box-radius formula, and grayscale conversion are all involved in our hash pipeline. A Pillow major-version bump can change any of these and silently break parity with the 5 other ports. |
| `PyWavelets` | `>=1.5,<2.0` | db4 filter coefficients are mathematical constants; less drift risk, but bound the major version. |
| `numpy` | `>=1.26,<2.0` | NumPy 2.0 changed some default dtypes and behaviors; pin to 1.x. |

This is intentional. The upstream-tracker workflow (`.github/workflows/upstream-tracker.yml`) catches new upstream releases weekly and surfaces the goldens diff so a human can decide whether to bump and re-release.

If you need to share a Python environment with packages requiring different Pillow / imagehash versions, install with the unpinned extra:

```bash
pip install rosetta-imagehash[unpinned]
```

**Caveat:** with `[unpinned]` the cross-port byte-exact guarantee is no longer enforced by the wrapper — verify your own output matches `spec/goldens.json` before relying on it. The 5 non-Python ports are unaffected (they don't link to Pillow at all).

## Usage

```python
import rosetta_imagehash as rih
from PIL import Image

img = Image.open("photo.jpg")

# Drop-in upstream imagehash:
h_strict = rih.whash(img, mode="db4")            # matches imagehash exactly

# Our cross-port-stable variant:
h_robust = rih.whash_db4_robust(img, hash_size=8)

print(str(h_strict))                              # hex, identical to imagehash.whash output
print(str(h_robust))                              # hex, identical to Rust/Go/Java/JS/Swift output

# On a real photo, the two agree:
assert str(h_strict) == str(h_robust)

# On a checkerboard, they will differ:
checker = Image.open("spec/fixtures/checker-256.png")
print(rih.whash(checker, mode="db4"))             # SIMD-dependent
print(rih.whash_db4_robust(checker, hash_size=8)) # same on every platform
```

## When to pick which

| Use case | Function |
|---|---|
| Matching against existing hashes stored from Python `imagehash` | `rih.whash(img, mode="db4")` |
| Cross-language storage/lookup where every port (Python, Rust, Go, Java, JS, Swift) must produce the same hex for the same input | `rih.whash_db4_robust(img)` |
| Hashing photographs (untrusted or trusted) | Either; output is identical for non-pathological inputs |
| Hashing synthetic test inputs or geometric patterns | `rih.whash_db4_robust` if cross-port matters |

## API

```python
def whash_db4_robust(
    image: PIL.Image.Image,
    hash_size: int = 8,
    image_scale: int | None = None,
) -> imagehash.ImageHash:
    """Cross-port-stable variant of imagehash.whash(mode='db4').

    See docstring in rosetta_imagehash/_impl.py for the full algorithm.
    """

WHASH_DB4_ROBUST_EPS: float = 1e-12  # the ε threshold; fixed across ports
```

All other names re-exported from `imagehash` are accessible as `rosetta_imagehash.<name>`.

## Testing

```bash
cd python
pip install -e ".[test]"
pytest
```

Tests verify `whash_db4_robust` output against `spec/goldens.json` — the same goldens the 5 non-Python ports test against. The Python reference produces the goldens, so this is a self-consistency check; the value comes from confirming the per-port implementations match.

## License

BSD-2-Clause.
