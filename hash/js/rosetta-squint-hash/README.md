# rosetta-squint-hash — JS/TS port

Byte-exact port of Python `imagehash==4.3.2` algorithms to TypeScript (ESM, Node 18+ and modern browsers).

The hex string produced here equals the hex Python `imagehash` produces for the same image, algorithm, and `hashSize`.

## Browser usage

The package has a separate browser entry that excludes the Node-only `decodePng` helper (which depends on `pngjs`'s Buffer usage). In the browser, decode via canvas or another browser-native decoder, then pass the resulting RGB(A) pixel buffer to any hash function:

```ts
import { phash, type RgbImage } from "rosetta-squint-hash/browser";

// Decode in the browser using built-in APIs
const blob = await (await fetch("photo.jpg")).blob();
const bitmap = await createImageBitmap(blob);
const canvas = new OffscreenCanvas(bitmap.width, bitmap.height);
const ctx = canvas.getContext("2d")!;
ctx.drawImage(bitmap, 0, 0);
const { data, width, height } = ctx.getImageData(0, 0, bitmap.width, bitmap.height);

const img: RgbImage = { width, height, data, channels: 4 };
const h = phash(img, 8);
console.log(h.toString());                       // "c3f8a1b27d0e4f96"
```

### Via CDN (once published to npm)

```html
<script type="module">
  // esm.sh — auto-builds ESM bundles for any npm package
  import { phash } from "https://esm.sh/rosetta-squint-hash/browser";

  // jsDelivr / unpkg — serves the published file directly
  // import { phash } from "https://cdn.jsdelivr.net/npm/rosetta-squint-hash@0.1/dist/browser.js";
  // import { phash } from "https://unpkg.com/rosetta-squint-hash@0.1/dist/browser.js";
</script>
```

Pure-TS, zero Node dependencies, zero WASM. Confirmed via `npm test` — `tests/browser-entry.test.ts` verifies the entire transitive import graph has no `node:` references. ~30 KB minified-ish bundle (all 10 hash algorithms + utilities).

## Quick start

```ts
import { readFileSync } from "node:fs";
import {
    phash, averageHash, dhash, whashHaar, colorhash,
    decodePng, hexToHash,
} from "rosetta-squint-hash";

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
    "rosetta-squint-hash": "file:../rosetta-squint-hash/js/rosetta-squint-hash"
  }
}
```

Runtime deps: `pngjs ^7` (pure-JS PNG decoder; works in Node and browser via bundlers).

## See also

- [USAGE.md](../../USAGE.md) — examples for all 6 ports
- [STATUS.md](../../STATUS.md)
- [`../../spec/SPEC.md`](../../spec/SPEC.md)

## License

BSD-2-Clause.
