# rosetta-image-hash — Java port

Byte-exact port of Python `imagehash==4.3.2` algorithms to Java 17.

## Build + test

```
cd ~/rosetta-image-hash/java
mvn test
```

Tests read fixtures and goldens from `../spec/`. Run `mvn` from this directory so the relative path resolves.

## v1 algorithms

`AverageHash`, `DHash`, `PHash`, `WHashHaar`, `ColorHash`, plus `Hex.hexToHash` and `Hex.hexToFlathash`. All entry points take `BufferedImage` of any type (non-`TYPE_INT_RGB` inputs are normalized via `Graphics2D.drawImage`, composite-on-black, matching PIL `convert('RGB')`).

## Test groups

| Group | Purpose |
|---|---|
| 1 | Per-kernel unit tests against `../spec/*_cases.json` and `lanczos_cases/*.bin`. |
| 2 | Each algorithm × fixture × size from `goldens.json`, using pre-decoded RGB buffers (decoder-bypassed). |
| 3 | Same as Group 2 but loads PNG via `ImageIO.read` (end-to-end). PNG decoder exemptions documented in `DECODER_NOTES.md`. |
| 4 | Hex round-trip on every Group-2 hash. |
| 5 | Hamming distance + error semantics. |

## Parity guarantee

Every test in Groups 1–4 asserts byte-exact equality with Python `imagehash 4.3.2`. Any Group-3 failure that passes Group 2 is a PNG decoder discrepancy; see `DECODER_NOTES.md` for documented exemptions.

## License

BSD-2-Clause.
