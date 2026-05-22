/**
 * Browser entry for rosetta-squint.
 *
 * Same convenience API as the main entry except the path-based functions
 * (which read from disk via `node:fs`) are omitted. Use the `_Bytes`
 * variants in browsers; obtain bytes from `fetch(url).then(r => r.arrayBuffer())`,
 * `FileReader`, drag-and-drop, etc.
 *
 *   import { phashBytes } from "rosetta-squint/browser";
 *
 *   const resp = await fetch("/photo.jpg");
 *   const bytes = new Uint8Array(await resp.arrayBuffer());
 *   const hash = await phashBytes(bytes, 8);
 *   console.log(hash.toString());   // "c3f8a1b27d0e4f96"
 *
 * Underlying decoders (mozjpeg, libwebp, libheif) run in browser via WASM.
 * TIFF (utif2) and HEIC (libheif-js) are CommonJS — your bundler must
 * support CJS→ESM interop (esbuild, vite, webpack 5+, rollup with
 * @rollup/plugin-commonjs all do).
 */

import { decode, type DecodedImage } from "rosetta-image-decode";
import * as rih from "rosetta-image-hash/browser";

export type { Hash, RgbImage } from "rosetta-image-hash/browser";
export type { Format } from "rosetta-image-decode";
export {
  ImageMultiHash,
  hexToHash,
  hexToFlathash,
  hexToMultiHash,
} from "rosetta-image-hash/browser";

function decodedToRgbImage(d: DecodedImage): rih.RgbImage {
  return {
    width: d.width,
    height: d.height,
    data: d.data,
    channels: d.channels,
  };
}

export async function decodeBytes(bytes: Uint8Array): Promise<rih.RgbImage> {
  const decoded = await decode(bytes);
  return decodedToRgbImage(decoded);
}

// Bytes-based convenience hash functions (no path variants in browser).

export async function averageHashBytes(bytes: Uint8Array, hashSize: number): Promise<rih.Hash> {
  return rih.averageHash(await decodeBytes(bytes), hashSize);
}
export async function phashBytes(bytes: Uint8Array, hashSize: number): Promise<rih.Hash> {
  return rih.phash(await decodeBytes(bytes), hashSize);
}
export async function phashSimpleBytes(bytes: Uint8Array, hashSize: number): Promise<rih.Hash> {
  return rih.phashSimple(await decodeBytes(bytes), hashSize);
}
export async function dhashBytes(bytes: Uint8Array, hashSize: number): Promise<rih.Hash> {
  return rih.dhash(await decodeBytes(bytes), hashSize);
}
export async function dhashVerticalBytes(bytes: Uint8Array, hashSize: number): Promise<rih.Hash> {
  return rih.dhashVertical(await decodeBytes(bytes), hashSize);
}
export async function whashHaarBytes(bytes: Uint8Array, hashSize: number): Promise<rih.Hash> {
  return rih.whashHaar(await decodeBytes(bytes), hashSize);
}
export async function whashDb4Bytes(bytes: Uint8Array, hashSize: number): Promise<rih.Hash> {
  return rih.whashDb4(await decodeBytes(bytes), hashSize);
}
export async function whashDb4RobustBytes(bytes: Uint8Array, hashSize: number): Promise<rih.Hash> {
  return rih.whashDb4Robust(await decodeBytes(bytes), hashSize);
}
export async function colorhashBytes(bytes: Uint8Array, binbits: number): Promise<rih.Hash> {
  return rih.colorhash(await decodeBytes(bytes), binbits);
}
export async function cropResistantHashBytes(bytes: Uint8Array) {
  return rih.cropResistantHash(await decodeBytes(bytes));
}
