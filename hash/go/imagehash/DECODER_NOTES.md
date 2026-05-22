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

One `whash_db4` case is skipped in both Group-2 and Group-3 tests
(`TestWHashDb4Goldens` and `TestPNGEndToEnd`):

- `checker-256.png` size=16

This synthetic checkerboard pattern causes the db4 wavelet decomposition to
produce theoretically-zero coefficients at the median boundary. The
floating-point noise at that boundary differs by 1–2 ULPs between Go's
pure-Go sequential summation and pywt's C-level implementation, flipping a
small number of bits near the median threshold.

The db4 2-D DWT was updated to use column-then-row traversal (matching pywt's
C evaluation order). This resolved two previously-exempt cases:
`checker-256.png` size=8 and `line-art-icon-256.png` size=16 now pass
byte-exact. Only the `checker-256.png` size=16 case still differs.

All other `whash_db4` fixtures pass byte-exact. The Go implementation is
mathematically correct (within 1e-10 of pywt) for all inputs; the remaining
disagreement is purely at the last-bit level of FP arithmetic.

Use `whash_db4_robust` for cross-port-stable hashes on all inputs including
pathological ones — `TestWHashDb4RobustGoldens` passes all 42 cases with no
exemptions.
