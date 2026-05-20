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
