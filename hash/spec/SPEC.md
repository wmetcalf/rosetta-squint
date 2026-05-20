# rosetta-image-hash bit-level specification

Every language port reproduces this pipeline byte-exact. The Python reference is
`imagehash==4.3.2` with `Pillow==10.4.0`, `numpy>=1.26,<2.0`, `scipy>=1.11,<1.15`,
`PyWavelets>=1.5,<2.0`. Versions are recorded in `goldens.json`; ports that read
different versions and fail parity should expect drift.

This document is organized by **pipeline step**, not by algorithm, because most
algorithms share early steps and the parity hazards live at the shared-step level.

## Conventions

- All integer arithmetic uses two's-complement of at least the width specified.
- All thresholds use **strict `>`**; equal values fall through. Goldens depend on this.
- `numpy.asarray(pil_image)` returns shape `(H, W)` — height-first — even though
  `Image.resize` takes `(W, H)`. Every port that flattens to a row-major buffer
  must consistently use `(H, W)` indexing.
- Hex strings are lowercase.

## Step 1 — PNG decode → RGB

Pillow's `Image.open(path).convert('RGB')` produces a uint8 RGB buffer with:
- alpha channel dropped (transparent pixels composite over black);
- gAMA/sRGB chunks ignored;
- indexed/palette PNGs expanded.

This spec **does not** redefine PNG decoding. Ports use their native PNG decoder
(`ImageIO.read` in Java, `image.Decode` in Go, `image::open` in Rust, `sharp` in
Node, etc.) and validate against `decoded/<fixture>.rgb.bin` — the canonical
Pillow output, stored as:

```
offset 0:  uint32_le  width
offset 4:  uint32_le  height
offset 8:  uint8 * (width * height * 3)  R,G,B,R,G,B,…  row-major
```

If a port's decoder produces different bytes for a fixture, the port may declare
that fixture **Group-3 exempt** with a documented reason in `<port>/DECODER_NOTES.md`
and `<port>/README.md`. The fixture still must pass Group 2 (algorithm tests reading
the `.rgb.bin` directly).

## Step 2 — RGB → grayscale (PIL `'L'` mode)

Fixed-point ITU-R 601 luma:

```
L = (R*19595 + G*38470 + B*7471 + 32768) >> 16    // uint8 output
```

The coefficients sum to exactly `65536 = 2^16`. Naive floating-point luma
(`0.299*R + 0.587*G + 0.114*B`) differs from PIL by ±1 on edge cases — do not use it.

Reference: `grayscale_cases.json` — ~30 RGB triples and expected `L` values.
Group-1 test: `test_pil_grayscale`.

## Step 3 — RGB → HSV (PIL `'HSV'` mode)

PIL's `convert('HSV')` is implemented in C (`libImaging/Convert.c`) using **integer
arithmetic on uint8 R/G/B**, not by computing a float HSV and scaling. This is
load-bearing for `colorhash`: bin assignment depends on PIL's exact integer
quirks.

**Anti-pattern warning:** Java's `Color.RGBtoHSB` produces float H/S/V in `[0,1]`.
`Math.round(hsbBuf[0] * 255)` does **not** match PIL. PIL truncates. Example:
RGB(100,150,200) → PIL `(H=148, S=127, V=200)`; round-based code gives `(149, 128, 200)`.
**Every port must reimplement PIL's integer formula. Lifting Java's float HSV is wrong.**

Reference algorithm (transcribed from `libImaging/Convert.c` `rgb2hsv_row`). All
arithmetic on non-negative integers — **no signed-division-semantics issues** because
we wrap `h_pre` into `[0, 1530)` before dividing.

```
// Pseudocode — int arithmetic; only non-negative values pass through the division.
int maxc = max(r, max(g, b));
int minc = min(r, min(g, b));
int v = maxc;
if (maxc == 0) return (0, 0, v);
int s = (255 * (maxc - minc)) / maxc;            // 0..255
if (minc == maxc) return (0, s, v);
int delta = maxc - minc;
int rc = ((maxc - r) * 255) / delta;             // 0..255
int gc = ((maxc - g) * 255) / delta;
int bc = ((maxc - b) * 255) / delta;
int h_pre;
if (r == maxc)      h_pre = bc - gc;             // [-255, 255]
else if (g == maxc) h_pre = 2 * 255 + rc - bc;   // [255, 765]
else                h_pre = 4 * 255 + gc - rc;   // [765, 1275]
if (h_pre < 0) h_pre += 6 * 255;                  // wrap negatives into [1275, 1530)
int h = h_pre / 6;                                // non-negative; result in [0, 255]
return (h, s, v);
```

Worked examples (verified empirically against PIL 10.4):
- `(100, 150, 200)`: `b == maxc`, `rc=255, gc=127, bc=0`; `h_pre = 1020 + 127 - 255 = 892`; `h = 892/6 = 148`. PIL: `(148, 127, 200)` ✓
- `(0, 255, 255)`: `g == maxc` (g checked before b when both equal max); `rc=255, gc=0, bc=0`; `h_pre = 510 + 255 - 0 = 765`; `h = 765/6 = 127`. PIL: `(127, 255, 255)` ✓
- `(200, 100, 150)`: `r == maxc`; `rc=0, gc=255, bc=127`; `h_pre = 127 - 255 = -128`; wrap: `-128 + 1530 = 1402`; `h = 1402/6 = 233`. PIL: `(233, 127, 200)` ✓

Reference: `hsv_cases.json` — ~30 RGB triples including the `.5`-boundary cases
and negative `h_pre` cases. Group-1 test: `test_pil_hsv_integer_formula`. A port that
uses Java's `Color.RGBtoHSB`-with-`Math.round`, or one that uses C-style truncated
div without the wrap step, will fail on the negative-`h_pre` cases.

## Step 4 — Lanczos3 resize (PIL `LANCZOS`)

Pillow's `LANCZOS`/`ANTIALIAS` filter, called on a uint8 `'L'`-mode image.

### Algorithm parameters

- center-of-pixel: `center = (idx + 0.5) * scale`
- `filter_scale = max(1.0, scale)` — kernel widens on downsample only.
- support: `3 * filter_scale`
- kernel: `lanczos(x) = sinc(x) * sinc(x/3)` for `|x| < 3`, else 0 (with `sinc(0) = 1`)
- weights normalized per output pixel (sum to 1.0)
- separable: horizontal pass over source rows, then vertical pass over horizontal-pass output

### Byte-exact match requires fixed-point arithmetic

Pillow's C implementation precomputes the kernel weights as 32-bit signed integers
(`PRECISION_BITS = 32`) — see `precompute_coeffs` in `Resample.c`. A pure
`float64` reference diverges from Pillow by ±1 in roughly 3% of pixels on a 64→32
downsample.

To achieve byte-exact parity, every port reproduces:

```
PRECISION_BITS = 32

for each output pixel xd:
    center = (xd + 0.5) * scale
    filter_scale = max(1.0, scale)
    support = 3.0 * filter_scale
    xmin = max(0, ceil(center - support))
    xmax = min(src_w - 1, floor(center + support))
    weights_f64 = [lanczos((xmin + i + 0.5 - center) / filter_scale) for i in range(xmax - xmin + 1)]
    wsum = sum(weights_f64)
    if wsum != 0: weights_f64 = [w / wsum for w in weights_f64]
    weights_i32 = [int(round(w * (1 << PRECISION_BITS))) for w in weights_f64]
    for each source row:
        acc_i64 = sum(weights_i32[i] * src_uint8[xmin + i] for i in range(n))
        out_uint8 = clip((acc_i64 + (1 << (PRECISION_BITS - 1))) >> PRECISION_BITS, 0, 255)
```

Vertical pass uses the same arithmetic, applied to the columns of the horizontal-pass output. **Both passes use the same fixed-point arithmetic; no intermediate float buffer exists** in PIL's C path.

References: `lanczos_cases/*.bin` — 4 reference cases (downsample, upsample, identity,
asymmetric). Group-1 test: `test_lanczos_fixed_point`. The downsample case is the
one where float reference diverges; that's the most sensitive parity check.

## Step 5 — DCT-II (used by pHash on 32×32 blocks)

**Output values, not implementation.** Spec mandates the output of:

```
y[k] = 2 * sum_{n=0..N-1} x[n] * cos(π * k * (2n+1) / (2N))    for k in [0, N)
```

This is `scipy.fftpack.dct(x, type=2, norm=None)`. Note the leading `2 *` factor
applies to **all** `k` (not just `k > 0`).

2-D DCT is column-wise 1-D then row-wise 1-D, matching Python's
`dct(dct(pixels, axis=0), axis=1)`. For a 32×32 input that's 32 column DCTs
followed by 32 row DCTs.

Ports may implement this with a direct O(N²) formula, Makhoul's FFT trick (the
approach used by the partial Java reference in `pdf-titan-arum`), an FFTW
binding, or anything else, as long as the output matches the reference.

Reference: `dct_cases.json` — 1-D DCT of three N=32 inputs (`arange`, impulse,
seeded random). Tolerance: max abs diff < 1e-9. Group-1 test: `test_dct_matches_scipy`.
