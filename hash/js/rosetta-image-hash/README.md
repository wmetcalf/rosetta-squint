# rosetta-image-hash — JS/TS port

Byte-exact port of Python `imagehash==4.3.2` algorithms to TypeScript (ESM, Node 18+).

## Build + test

```
cd ~/rosetta-image-hash/js/rosetta-image-hash
npm install
npm test
```

Tests resolve fixtures and goldens from `../../spec/`. Run `npm test` from the package root.

## v1 algorithms

`averageHash`, `dhash`, `phash`, `whashHaar`, `colorhash`, plus `hexToHash` and `hexToFlathash`. All take an `RgbImage` (`{ width, height, data: Uint8Array, channels: 3 | 4 }`) and return a `Hash` (or throw `ImageHashError`).

For PNG callers, a `decodePng(bytes: Uint8Array): RgbImage` helper is exported.

## Dependencies

- Runtime: `pngjs` (pure-JS PNG decoder; Node + browser via bundlers)
- Dev-only: `vitest`, `typescript`, `@types/node`, `@types/pngjs`

## License

BSD-2-Clause.
