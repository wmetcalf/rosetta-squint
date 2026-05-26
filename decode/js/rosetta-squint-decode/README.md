# rosetta-squint-decode — JS/TS port

Byte-exact PIL-compatible image decoder library, JS/TS port (ESM, Node 18+).

Decodes BMP, PNG, GIF (first frame), JPEG, WebP, TIFF, and HEIC to a raw RGB or RGBA byte buffer matching what `PIL.Image.open(...).tobytes()` produces. **No system libraries required** — everything bundles natively or as WASM.

## Quick start

```ts
import { readFileSync } from "node:fs";
import {
    decode, detectFormat, supportedFormats, DecodeError,
} from "rosetta-squint-decode";

const bytes = new Uint8Array(readFileSync("photo.jpg"));

const sniff = detectFormat(bytes);              // "jpeg" | "png" | ... | null
console.log("detected:", sniff);

try {
    const img = await decode(bytes);            // async — WASM init
    console.log(`${img.width}x${img.height} channels=${img.channels} format=${img.format}`);
    // img.data is Uint8Array, length = width * height * (3 or 4)
} catch (e) {
    if (e instanceof DecodeError) {
        console.error("decode failed:", e.kind, e.detail);
    } else {
        throw e;
    }
}
```

**Note: `decode()` is async.** The JPEG, WebP, and HEIC backends are WASM modules that initialize asynchronously. Always `await`.

## Build + test

```
npm install
npm test                    # 44 tests via vitest, all passing on Linux x86-64
npm run build               # tsc → dist/
```

Tests resolve fixtures and goldens from `../../spec/`. Run from this package root.

## API

```ts
function decode(bytes: Uint8Array): Promise<DecodedImage>;
function detectFormat(bytes: Uint8Array): Format | null;
function supportedFormats(): Format[];

interface DecodedImage {
    width: number;
    height: number;
    data: Uint8Array;             // row-major, length = width * height * (3 or 4)
    channels: 3 | 4;
    format: Format;
}

type Format = "bmp" | "png" | "gif" | "jpeg" | "webp" | "tiff" | "heic";
type DecodeErrorKind =
    | "unsupportedFormat"
    | "corruptInput"
    | "truncated"
    | "unsupportedFeature";

class DecodeError extends Error {
    kind: DecodeErrorKind;
    format: Format | null;
    detail: string;
}
```

## Dependencies

Runtime (all in `package.json`):

- `pngjs ^7` — pure-JS PNG
- `omggif ^1` — pure-JS GIF
- `utif2 ^4.1.0` — pure-JS TIFF
- `@jsquash/jpeg ^1.6.0` — mozjpeg compiled to WASM
- `@jsquash/webp ^1.5.0` — libwebp compiled to WASM
- `libheif-js ^1.17.1` — libheif + libde265 compiled to WASM

BMP is implemented inline (no third-party library).

## Install

Not on npm yet. From a sibling project:

```json
{
  "dependencies": {
    "rosetta-squint-decode": "file:../rosetta-squint-decode/js/rosetta-squint-decode"
  }
}
```

## Format support

| Format | Status | Backend |
|---|---|---|
| BMP | byte-exact | hand-written (`src/internal/bmp.ts`) |
| PNG | byte-exact | pngjs |
| GIF | byte-exact, first frame only | omggif |
| JPEG | byte-exact | @jsquash/jpeg WASM |
| WebP | byte-exact | @jsquash/webp WASM |
| TIFF | byte-exact, baseline only | utif2 |
| HEIC | **±2 px tolerance**, single still image | libheif-js WASM |

**HEIC caveat:** the bundled libheif WASM build diverges from system libheif by ±1–2 per pixel due to differing YCbCr→RGB rounding. The JS port's Group 2 HEIC test uses a max-delta tolerance instead of strict byte-exact. Other ports (Rust, Go, Java, Swift) all link to system libheif 1.17.6 directly and are byte-exact. See `DECODER_NOTES.md`.

## See also

- [USAGE.md](../../USAGE.md)
- [STATUS.md](../../STATUS.md)
- [`../../spec/SPEC.md`](../../spec/SPEC.md)

## License

BSD-2-Clause.
