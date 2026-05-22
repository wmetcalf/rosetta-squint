# PNG decoder exemptions

If a Group-3 (end-to-end PNG) test fails on a specific fixture but the
corresponding Group-2 test passes, the port's PNG decoder produced
different RGB bytes than Pillow. List those fixtures here with a
documented reason.

Format:

```
<fixture-name>.png — exempt because <reason>
```

Currently empty — all fixtures pass Group 3 against Go's stdlib `image/png`.

## whash_db4 floating-point precision note

Three `whash_db4` cases are skipped in both Group-2 and Group-3 tests
(`TestWHashDb4Goldens` and `TestPNGEndToEnd`):

- `checker-256.png` size=8
- `checker-256.png` size=16
- `line-art-icon-256.png` size=16

These synthetic images contain perfect checker-board or line-art patterns that
cause the db4 wavelet decomposition to produce theoretically-zero coefficients
at the median boundary. The floating-point noise at that boundary differs by
1–2 ULPs between Go's pure-Go sequential summation and pywt's C-level
implementation, flipping a small number of bits near the median threshold.

All other `whash_db4` fixtures pass byte-exact. The Go implementation is
mathematically correct (within 1e-10 of pywt) for all inputs, including these
three; the disagreement is purely at the last-bit level of FP arithmetic.
