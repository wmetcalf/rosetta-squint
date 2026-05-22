/**
 * Browser entry point for rosetta-image-hash.
 *
 * Re-exports the pure-TS hash algorithms (no Node imports) for use in
 * browsers. Skips the `decodePng` helper from the main entry because pngjs
 * relies on Node's Buffer; for PNG decoding in browser, use one of:
 *
 *   - `createImageBitmap(blob)` + `OffscreenCanvas` → `getImageData()`
 *   - A pure-ESM browser PNG decoder (e.g. `@jsquash/png` or `upng-js`)
 *
 * Then wrap the resulting RGB(A) bytes in an `RgbImage` and pass to any
 * hash function:
 *
 *   import { phash, type RgbImage } from "rosetta-image-hash/browser";
 *
 *   const bitmap = await createImageBitmap(blob);
 *   const canvas = new OffscreenCanvas(bitmap.width, bitmap.height);
 *   const ctx = canvas.getContext("2d")!;
 *   ctx.drawImage(bitmap, 0, 0);
 *   const { data, width, height } = ctx.getImageData(0, 0, bitmap.width, bitmap.height);
 *   const img: RgbImage = { width, height, data, channels: 4 };
 *   const h = phash(img, 8);
 */

export type { Hash } from "./hash.js";
export type { RgbImage, ImageHashErrorKind } from "./hash.js";
export { ImageHashError } from "./hash.js";
export { hexToHash, hexToFlathash } from "./hex.js";
export { averageHash } from "./averageHash.js";
export { dhash } from "./dhash.js";
export { dhashVertical } from "./dhashVertical.js";
export { phash } from "./phash.js";
export { phashSimple } from "./phashSimple.js";
export { whashHaar } from "./whashHaar.js";
export { whashDb4 } from "./whashDb4.js";
export { whashDb4Robust } from "./whashDb4Robust.js";
export { colorhash, colorhashBinEncode } from "./colorhash.js";
export { ImageMultiHash, hexToMultiHash } from "./multiHash.js";
export { cropResistantHash } from "./cropResistantHash.js";
// Intentionally NOT re-exported in browser entry: `decodePng` (depends on pngjs/Buffer).
