# rosetta-squint — JS/TS

Point at a file path or pass raw image bytes; get back the perceptual hash hex string that every other `rosetta-squint` port produces for the same input.

## Install (Node)

```bash
npm install rosetta-squint
```

```ts
import { phash, phashBytes } from "rosetta-squint";

const h1 = await phash("photo.jpg", 8);                      // file path
const h2 = await phashBytes(new Uint8Array(jpegBytes), 8);   // raw bytes in memory
console.log(h1.toString());                                  // "c3f8a1b27d0e4f96"
```

## Install (Browser)

```ts
import { phashBytes } from "rosetta-squint/browser";

const resp = await fetch("/photo.jpg");
const bytes = new Uint8Array(await resp.arrayBuffer());
const h = await phashBytes(bytes, 8);
console.log(h.toString());                                   // "c3f8a1b27d0e4f96"
```

The `/browser` sub-export omits the path-based functions (which use `node:fs`) and is otherwise identical. All 10 algorithms are available with the same names plus a `Bytes` suffix:

`averageHashBytes`, `phashBytes`, `phashSimpleBytes`, `dhashBytes`, `dhashVerticalBytes`, `whashHaarBytes`, `whashDb4Bytes`, `whashDb4RobustBytes`, `colorhashBytes`, `cropResistantHashBytes`.

### Via CDN

Once on npm:

```html
<script type="module">
  // esm.sh auto-builds browser bundles for any npm package
  import { phashBytes } from "https://esm.sh/rosetta-squint/browser";

  const resp = await fetch("/photo.jpg");
  const bytes = new Uint8Array(await resp.arrayBuffer());
  console.log((await phashBytes(bytes, 8)).toString());
</script>
```

esm.sh handles the @jsquash WASM + libheif-js bundling automatically. Other CDN options (`unpkg`, `jsdelivr`) ship the raw `dist/` and assume your bundler handles CJS→ESM for the libheif-js and utif2 transitive deps.

### Bundler notes

The browser entry uses dynamic `import("libheif-js")` and `import("utif2")` which are CommonJS packages. Modern bundlers handle the CJS→ESM interop automatically:

| Bundler | What you need |
|---|---|
| Vite | works out of the box |
| esbuild | works out of the box |
| webpack 5+ | works out of the box |
| rollup | add `@rollup/plugin-commonjs` |
| Parcel | works out of the box |

WASM (mozjpeg, libwebp) is loaded via `WebAssembly.compileStreaming(fetch(url))` in the browser. Most bundlers emit the WASM blobs as static assets and rewrite the import URL — no manual config required for Vite/esbuild/webpack 5+.

The `loadWasm` helper detects browser vs. Node at runtime and uses the right loader path. The `node:fs` import in the Node branch is dynamic (`await import("node:fs")`) so browser bundlers tree-shake it.

## API

```ts
// Path entry (Node only)
phash(path: string, hashSize: number): Promise<Hash>
phashSimple(path: string, hashSize: number): Promise<Hash>
dhash(path: string, hashSize: number): Promise<Hash>
dhashVertical(path: string, hashSize: number): Promise<Hash>
averageHash(path: string, hashSize: number): Promise<Hash>
whashHaar(path: string, hashSize: number): Promise<Hash>
whashDb4(path: string, hashSize: number): Promise<Hash>
whashDb4Robust(path: string, hashSize: number): Promise<Hash>
colorhash(path: string, binbits: number): Promise<Hash>
cropResistantHash(path: string): Promise<ImageMultiHash>

// Bytes entry (Node + browser)
phashBytes(bytes: Uint8Array, hashSize: number): Promise<Hash>
// ... same suffix-Bytes pattern for every algorithm above ...

// Lower level
decodeFile(path: string): Promise<RgbImage>        // Node only
decodeBytes(bytes: Uint8Array): Promise<RgbImage>  // Node + browser
```

## Cross-port verification

`phashBytes(imagehash.png, 8) === "ba8c84536bd3c366"` in this package's browser entry, this package's Node entry, the Go squint port, the Java squint port, and the Python `rosetta_squint` port. Same hex everywhere.

## License

BSD-2-Clause.
