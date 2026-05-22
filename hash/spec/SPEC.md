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

Pillow's C implementation precomputes kernel weights as 32-bit signed integers
but the shift amount accounts for the 8-bit pixel range plus a 2-bit safety margin:
`#define PRECISION_BITS (32 - 8 - 2)` = **22** in `src/libImaging/Resample.c`.
A pure `float64` reference diverges from Pillow by ±1 in roughly 3% of pixels on
a 64→32 downsample.

To achieve byte-exact parity, every port reproduces:

```
PRECISION_BITS = 22   # = 32 - 8 - 2 in Pillow's Resample.c

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

Note: with `PRECISION_BITS = 22`, the quantized weight `int(round(1.0 * (1 << 22)))`
= 4194304 fits comfortably in int32. With 22-bit weights × uint8 pixels × ~7 taps,
the accumulator stays well under int64 range.

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

## Step 6 — Median for pHash threshold

For an N×N block (N²-element flattened sort): `numpy.median(block)` semantics —
for even N², `(sorted[N²/2 - 1] + sorted[N²/2]) / 2.0`. For N=8 that's
`(sorted[31] + sorted[32]) / 2`.

Reference: `dct_cases.json` doesn't cover this directly; ports add a sanity
unit test (`test_median_64_elements`) over a small known input.

## Step 7 — Bit packing → hex

Python source (`imagehash._binary_array_to_hex`):

```python
bit_string = ''.join(str(b) for b in 1 * arr.flatten())
width = int(numpy.ceil(len(bit_string) / 4))
return '{:0>{width}x}'.format(int(bit_string, 2), width=width)
```

In plain language: flatten the boolean array **row-major** (numpy C order),
produce one bit per element MSB-first, parse the resulting bit-string as a single
big-endian unsigned integer, format as zero-padded hex with `width = ceil(M*N / 4)`.

Worked example: the 4×4 pattern

```
1 0 1 0
0 1 0 1
1 1 1 1
0 0 0 0
```

produces hex `"a5f0"`. Group-1 test: `test_bit_pack_to_hex_msb_first`.

## Step 8 — `colorhash` quirky bin encoding

For each bin value `v` (integer in `[0, 2^B)`) and `binbits` `B`, generate B bits:

```python
bit[i] = (v // 2**(B-i-1)) % 2**(B-i) > 0    for i in range(B)
```

Equivalent in C/Java/Rust/Go (operands are non-negative):

```
bit[i] = ((v >> (B-i-1)) & ((1 << (B-i)) - 1)) > 0    for i in 0..B
```

**This is not standard binary.** Worked table for B=4:

| v | bits[0..3] | hex |
|---|---|---|
| 0 | 0,0,0,0 | 0 |
| 1 | 0,0,0,1 | 1 |
| 2 | 0,0,1,0 | 2 |
| 4 | **0,1,1,0** | **6** *(not 4)* |
| 7 | 0,1,1,1 | 7 |
| 8 | **1,1,0,0** | **c** *(not 8)* |
| 15 | 1,1,1,1 | f |

Group-1 test: `test_colorhash_bin_encoding`.

## Step 9 — Error semantics

Every algorithm validates inputs and raises a port-idiomatic error:

| Condition | Algorithms | Error |
|---|---|---|
| `hash_size < 2` | ahash, dhash, phash | `ValueError`-equivalent |
| `hash_size` not power of 2, or `level > ll_max_level` | whash | `ValueError`-equivalent |
| `binbits < 1` | colorhash | `ValueError`-equivalent |
| Invalid hex chars; non-square length for `hex_to_hash` | hex parsers | `ValueError`-equivalent |

Port-idiomatic errors:
- Java: `IllegalArgumentException`
- Rust: `Result<_, ImageHashError>`
- Go: `error` return value
- JS/TS: `throw new Error(...)`
- Swift: `throws`

Image-type validation (e.g., "image is a PIL.Image") is **out of scope** — the
port's type system handles that. Group-5 test exercises numerical-input errors.

## Algorithm definitions

Each algorithm composes the pipeline steps above.

### `average_hash(img, hash_size=N)`

1. Convert to grayscale (step 2) → uint8 array.
2. Lanczos resize (step 4) to `(N, N)` → uint8 array.
3. `avg = mean(pixels)` as float64 (numpy widens uint8 sum to int64; non-Python ports must accumulate in u32+ or float64, **not** u8).
4. `bit[y][x] = pixels[y][x] > avg` (strict `>`).
5. Pack bits to hex (step 7).

### `dhash(img, hash_size=N)`

1. Convert to grayscale (step 2).
2. Lanczos resize (step 4) to **`(width=N+1, height=N)`**. After `numpy.asarray(image)` shape is `(H=N, W=N+1)`.
3. `bit[y][x] = pixel[y][x+1] > pixel[y][x]` for `y in [0,N), x in [0,N)`.
4. Pack bits to hex.

### `dhash_vertical(img, hash_size=N)` — pre-3.0 back-compat

Same as `dhash` but compares **vertically adjacent** pixels instead of horizontally adjacent. Python `imagehash` exposes this to preserve the pre-3.0 (buggy) dhash direction for users with stored hashes from that era. New callers should always use `dhash`.

1. Convert to grayscale.
2. Lanczos resize to **`(width=N, height=N+1)`**. After `numpy.asarray(image)` shape is `(H=N+1, W=N)`.
3. `bit[y][x] = pixel[y+1][x] > pixel[y][x]` for `y in [0,N), x in [0,N)`.
4. Pack bits to hex.

### `phash(img, hash_size=N, highfreq_factor=4)`

1. Convert to grayscale.
2. Lanczos resize to `(N*4, N*4)`.
3. 2-D DCT-II: `dct(dct(pixels, axis=0), axis=1)` — column-wise then row-wise.
4. Take top-left `N×N` block: `dct[0:N, 0:N]`.
5. `med = median(block)` (step 6).
6. `bit = block > med` (strict `>`).
7. Pack bits to hex.

### `phash_simple(img, hash_size=N, highfreq_factor=4)`

**Differs from `phash` in three ways** (not just thresholding — verified against `imagehash` 4.3.2 source):

1. Convert to grayscale.
2. Lanczos resize to `(N*highfreq_factor, N*highfreq_factor)` (same as `phash`).
3. **1-D DCT-II row-wise only** — `dct = scipy.fftpack.dct(pixels)`. This is `dct(pixels, axis=-1)` — applied to each row independently. **NOT** the column-then-row 2-D DCT used by `phash`. Each row of the input becomes a row of DCT coefficients.
4. Slice `dctlowfreq = dct[0:N, 1:N+1]` — rows 0..N, columns **1..N+1** (skip the DC column at index 0).
5. `m = mean(dctlowfreq)` — float64.
6. `bit = dctlowfreq > m` (strict `>`).
7. Pack bits to hex.

The slicing+mean choice mirrors the original "Looks Like It" blog-post algorithm, which uses the 1-D DCT and discards the DC term to be robust against overall brightness shifts. `phash` (median + 2-D DCT) is a refinement; `phash_simple` is the original. Both algorithms exist in Python `imagehash` and they produce **different** hashes for the same input.

### `whash(img, hash_size=N)` — v1 only `mode='haar'`, `remove_max_haar_ll=True`

Mirroring Python source exactly:

1. `image_natural_scale = 2**int(log2(min(W, H)))`.
2. `image_scale = max(image_natural_scale, hash_size)`.
3. `ll_max_level = int(log2(image_scale))`, `level = int(log2(hash_size))`, `dwt_level = ll_max_level - level`.
4. Validate: `hash_size` is power of 2 and `level <= ll_max_level`; else raise.
5. `image = image.convert('L').resize((image_scale, image_scale), LANCZOS)` (steps 2 + 4).
6. `pixels = numpy.asarray(image) / 255.0` → float64, shape `(image_scale, image_scale)`.
7. `coeffs = pywt.wavedec2(pixels, 'haar', level=ll_max_level, mode='symmetric')`. This returns a Python list `[cA_n, (cH_n, cV_n, cD_n), (cH_{n-1}, cV_{n-1}, cD_{n-1}), …, (cH_1, cV_1, cD_1)]` — element 0 is a 2-D ndarray (shape `(1,1)` at full decomposition for power-of-2 input), every other element is a 3-tuple of 2-D ndarrays. Port equivalents model this as a list-of-(array-or-3-tuple).
8. `coeffs[0] *= 0` — zero the LL band in place.
9. `modified_pixels = pywt.waverec2(coeffs, 'haar', mode='symmetric')` — output shape equals step-6 input.
10. `coeffs2 = pywt.wavedec2(modified_pixels, 'haar', level=dwt_level, mode='symmetric')`.
11. `ll = coeffs2[0]` — shape `(N, N)`.
12. `med = median(ll)`; `bit = ll > med`; pack to hex.

**Fixtures smaller than `hash_size`**: `goldens.json` stores `null` for `whash_haar` entries where the fixture's natural scale is smaller than the requested `hash_size` (which causes Python imagehash to assert). Ports must skip rather than fail on `null` entries when consuming the goldens. This is rare in the v1 corpus but the safety net exists.

`'symmetric'` boundary mode is pywt's default; explicit here so v2 (db4) doesn't
accidentally inherit. For Haar specifically (filter length 2) the output size is
always exactly N/2 per level regardless of boundary mode, but coefficient values
near edges differ for longer wavelets.

Haar filter coefficients: lowpass `[1/√2, 1/√2]`, highpass `[1/√2, -1/√2]`.

### `whash(img, hash_size=N, mode='db4')` — Daubechies-4 variant

**Important — the `mode` parameter is partial:** Python `imagehash.whash` hardcodes Haar for the `remove_max_haar_ll` LL-zeroing step regardless of `mode`. Only the *second* `wavedec2` (after LL removal) uses the requested wavelet basis. Pipeline:

1. Convert to grayscale, resize to `(image_scale, image_scale)` via Lanczos (steps 1–6 of `whash_haar`).
2. `pixels = numpy.asarray(image) / 255.0` — float64.
3. **Haar (not db4)** wavedec2 at `ll_max_level`; zero the LL band; **Haar** waverec2 back. This is the `remove_max_haar_ll` step — `whash` hardcodes Haar here.
4. **db4** `pywt.wavedec2(pixels, 'db4', level=dwt_level)` — this is where the mode parameter applies.
5. `ll = coeffs[0]` — the LL band after `dwt_level` db4 decompositions.
6. `med = median(ll)`; `bit = ll > med`; pack to hex.

Daubechies-4 filter coefficients (from pywt's `Wavelet('db4')`):
- Lowpass `h0..h7` (length 8, not 4 — db4 means "4-vanishing-moments Daubechies", which has an 8-tap filter)
- Highpass derived from lowpass via quadrature mirror
- Read coefficients from `spec/db4_cases.json` (`filter_coefficients_lowpass`, `filter_coefficients_highpass`, `synthesis_lowpass`, `synthesis_highpass`) for byte-exact reproduction

Boundary handling:
- `'symmetric'` mode (pywt default): whole-sample symmetric. Input `[a, b, c, d]` extended as `[…, c, b, a, a, b, c, d, d, c, b, …]` with period `2*len`. (The earlier spec version incorrectly described this as half-sample symmetric — pywt's default is whole-sample.)
- Per-level output length: `pywt.dwt_coeff_len(input_len, filter_len=8, 'symmetric') = (input_len + 7) // 2`

`wavedec2` recursively applies `dwt2` `dwt_level` times to the LL band; output structure is `[cA_n, (cH_n, cV_n, cD_n), …, (cH_1, cV_1, cD_1)]`. `waverec2` is the synthesis inverse.

**Fixtures with `null` goldens** for `whash_db4`: same rule as `whash_haar` — when `hash_size` exceeds the fixture's natural decomposition depth, the Python reference raises and we record `null`. Ports skip those entries.

**Known ULP-level numerical noise:** the db4 LL-median comparison on certain inputs (e.g. `checker-256.png` at `hash_size=16`) lands within ~1e-16 of zero. PyWavelets' C inner loop with SIMD/FMA may resolve the sign differently than a port's high-level double accumulation. Bit flips at exactly the median tie-point are acceptable: ports tracking such cases should document them in `DECODER_NOTES.md`. The remaining 21 × 2 − 2 = ~40 cases pass byte-exact.

### `colorhash(img, binbits=B)`

For every pixel, compute `L` (step 2) and `(H, S, V)` (step 3).

**Bin assignment, all using strict `<` / strict `>`:**

| Pixel | Condition | Bin |
|---|---|---|
| black | `L < 32`             *(32 = 256 // 8)*       | black |
| gray | else `S < 85`         *(85 = 256 // 3)*       | gray |
| faint hue | else `S < 170`   *(170 = 256 * 2 // 3)* | `faint_bins[min(5, int(H * 6.0 / 255.0))]` |
| bright hue | else `S > 170` | `bright_bins[min(5, int(H * 6.0 / 255.0))]` |
| (neither) | `S == 170` (and not black/gray) | counted as colorful (denominator) but **lands in no histogram bin** |

Boundary cases (each verified with a dedicated fixture):
- `L == 32` → **not** black; falls into gray or colorful branch.
- `S == 85` (with `L >= 32`) → **not** gray; falls into colorful branch.
- `S == 170` (with `L >= 32`, `S >= 85`) → colorful denominator but neither faint nor bright.

**Quantize each of 14 bins to an integer in `[0, 2^B)`:**

```
n = W * H  (total pixel count)
c = max(1, colorful_count)
values[0]  = min(2^B - 1, int(black_count       / n * 2^B))
values[1]  = min(2^B - 1, int(gray_not_black_count / n * 2^B))
for i in 0..6:
    values[2 + i] = min(2^B - 1, int(faint_bins[i]  / c * 2^B))
    values[8 + i] = min(2^B - 1, int(bright_bins[i] / c * 2^B))
```

Bit-encode each value via step 8 → flatten → hex of width `ceil(14*B / 4)`.

### `hex_to_hash(hex)`

Inverse of step 7 for square `hash_size × hash_size` shape:
1. `hash_size = isqrt(len(hex) * 4)`. Raise if not exact (input not square).
2. Parse hex as big-endian unsigned integer; expand to `hash_size² = N²` MSB-first bits.
3. Reshape row-major into `(hash_size, hash_size)` boolean array.

### `hex_to_flathash(hex, hashsize)`

For colorhash-style flat shape `(14, hashsize)` where the caller supplies the second axis:
1. Total bits = `14 * hashsize`.
2. Parse hex as big-endian unsigned integer; expand to that many MSB-first bits.
3. Reshape into a 2-D boolean array of shape `(14, hashsize)`.

## Validation matrix

Each port's test suite reads the following files and asserts byte-exact equality:

| Test group | File(s) consumed | Number of assertions |
|---|---|---|
| Group 1 (pipeline steps) | `grayscale_cases.json`, `hsv_cases.json`, `dct_cases.json`, `haar_cases.json`, `lanczos_cases/*.bin` | ~120 |
| Group 2 (algorithms, decoded input) | `goldens.json` + `decoded/*.rgb.bin` | ~500 |
| Group 3 (algorithms, PNG input) | `goldens.json` + `fixtures/*.png` | ~500 (modulo exemptions) |
| Group 4 (hex round-trip) | round-trip every Group-2 hash | ~500 |
| Group 5 (distance / error semantics) | fixed bit patterns; invalid inputs | ~30 |
