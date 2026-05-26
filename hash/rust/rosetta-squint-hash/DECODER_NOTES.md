# PNG decoder exemptions

If a Group-3 (end-to-end PNG) test fails on a specific fixture but the
corresponding Group-2 test passes, the port's PNG decoder produced
different RGB bytes than Pillow. List those fixtures here with a
documented reason.

Format:

```
<fixture-name>.png — exempt because <reason>
```

Currently empty — all fixtures pass Group 3 against the `image` crate's PNG decoder.

# Known ULP-level numerical noise

## whash_db4: checker-256.png hash_size=16

This Golden test case (1 of 42 `whash_db4` cases) has CA values in the range
~1e-17 that land exactly at the median tie-point (threshold = 0.0 exactly).
PyWavelets' C inner loop with potential SIMD/FMA operations resolves the sign
of these values differently than the Rust f64 accumulation loop.

The Java port has the identical failure on this case. Per `spec/SPEC.md`:
> Bit flips at exactly the median tie-point are acceptable: ports tracking
> such cases should document them in DECODER_NOTES.md.

All other 41 `whash_db4` cases pass byte-exact.
