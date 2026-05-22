# rosetta-image-hash — JS/TS port

Byte-exact port of Python `imagehash==4.3.2` algorithms to TypeScript (ESM, Node 18+).

The hex string produced here equals the hex Python `imagehash` produces for the same image, algorithm, and `hashSize`.

## Quick start

```ts
import { readFileSync } from "node:fs";
import {
    phash, averageHash, dhash, whashHaar, colorhash,
    decodePng, hexToHash,
} from "rosetta-image-hash";

const bytes = new Uint8Array(readFileSync("photo.png"));
const img = decodePng(bytes);                          // RgbImage { width, height, data, channels }

const h = phash(img, 8);
console.log(h.toString());                             // "c3f8a1b27d0e4f96"

// Hamming distance
const other = phash(decodePng(new Uint8Array(readFileSync("other.png"))), 8);
console.log("distance:", h.subtract(other));

// Round-trip
const restored = hexToHash(h.toString());
console.log(restored.equals(h));                       // true
```

If you already have RGB pixels from elsewhere (canvas, sharp, browser `ImageData`), construct the `RgbImage` yourself instead of calling `decodePng`:

```ts
const img = { width, height, data: rgbUint8Array, channels: 3 };
const h = phash(img, 8);
```

## Build + test

```
npm install
npm test                    # 52 tests via vitest, all passing on Linux x86-64
npm run build               # tsc → dist/
```

Tests resolve fixtures and goldens from `../../spec/`. Run from the package root.

## API

All hash functions are synchronous and take an `RgbImage` `{ width: number; height: number; data: Uint8Array; channels: 3 | 4 }`:

| Function | Signature |
|---|---|
| `averageHash` | `(img, hashSize: number) => Hash` |
| `dhash` | `(img, hashSize: number) => Hash` |
| `phash` | `(img, hashSize: number, highfreqFactor?: number) => Hash` |
| `whashHaar` | `(img, hashSize: number) => Hash` — `hashSize` must be power of 2 |
| `colorhash` | `(img, binbits: number) => Hash` |
| `colorhashBinEncode` | `(v: number, binbits: number) => boolean[]` |
| `hexToHash` | `(hex: string) => Hash` |
| `hexToFlathash` | `(hex: string, hashSize: number) => Hash` |
| `decodePng` | `(bytes: Uint8Array) => RgbImage` |

`Hash` exposes `.toString()` (hex), `.subtract(other) => number`, `.equals(other) => boolean`. Invalid sizes throw `ImageHashError`.

## Install

Not on npm yet. From a sibling project:

```json
{
  "dependencies": {
    "rosetta-image-hash": "file:../rosetta-image-hash/js/rosetta-image-hash"
  }
}
```

Runtime deps: `pngjs ^7` (pure-JS PNG decoder; works in Node and browser via bundlers).

## See also

- [USAGE.md](../../USAGE.md) — examples for all 5 ports
- [STATUS.md](../../STATUS.md)
- [`../../spec/SPEC.md`](../../spec/SPEC.md)

## License

BSD-2-Clause.
