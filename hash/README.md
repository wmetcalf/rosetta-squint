# rosetta-image-hash

Byte-exact ports of the Python `imagehash` library (v4.3.2) to other languages.

## Goal

Every language port produces the **same hex string** as Python `imagehash` for the
same input image, for the same algorithm and size. This means you can hash an image
in Java, store the hex, and compare it byte-for-byte against a hex produced by the
Python or Rust port — no library involved.

## Status

| Port | Status |
|---|---|
| Python (reference) | `imagehash==4.3.2` upstream |
| Java | In progress |
| Go | Not started |
| Rust | Not started |
| JS/TS | Not started |
| Swift | Not started |

## Algorithms (v1)

- `average_hash`, `dhash`, `phash`, `whash` (haar mode only, `remove_max_haar_ll=True`), `colorhash`
- `hex_to_hash`, `hex_to_flathash`

Deferred to v2: `phash_simple`, `dhash_vertical`, `whash` db4 mode, `crop_resistant_hash`.

## Layout

- `/spec/` — bit-level rules (`SPEC.md`), fixture corpus, canonical decoded buffers,
  golden hex values, Group-1 unit-test reference vectors, JSON Schema. Every port
  consumes this directory to validate parity.
- `/java/` — Java port (Maven, Java 17).
- *Other ports added when their port plan lands.*

## Working on a port

```
git clone <this-repo>
cd spec && pip install -r requirements.txt && python regenerate.py --check   # sanity
cd ../<your-port> && <your test runner>
```

If `regenerate.py --check` fails, the committed goldens drifted from what Python
produces. Re-run `python regenerate.py` to refresh, commit, retry.

## License

BSD-2-Clause, matching upstream `JohannesBuchner/imagehash`.
