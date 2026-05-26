/**
 * Normalize an RgbImage (3 or 4 channels) to a flat Uint8Array of RGB triples
 * (row-major, length = width * height * 3).
 *
 * For channels=4, alpha is composited against opaque black via
 * `out_c = floor(src_c * alpha / 255)`, matching PIL `convert('RGB')`.
 * Fully transparent → (0,0,0); fully opaque → src RGB unchanged.
 */

import { ImageHashError, type RgbImage } from "../hash.js";

export interface Rgb3 {
  width: number;
  height: number;
  /** Length = width * height * 3, row-major. */
  data: Uint8Array;
}

/**
 * Validates that an RgbImage's data buffer has the expected length and that
 * dimensions/channels are valid. Throws ImageHashError("ShapeMismatch") on
 * mismatch. Every public hash function must call this at its entry point.
 */
export function validateRgbImage(img: RgbImage): void {
  if (!Number.isInteger(img.width) || img.width <= 0) {
    throw new ImageHashError(
      "ShapeMismatch",
      `width must be a positive integer; got ${img.width}`,
    );
  }
  if (!Number.isInteger(img.height) || img.height <= 0) {
    throw new ImageHashError(
      "ShapeMismatch",
      `height must be a positive integer; got ${img.height}`,
    );
  }
  if (img.channels !== 3 && img.channels !== 4) {
    throw new ImageHashError(
      "ShapeMismatch",
      `channels must be 3 or 4; got ${img.channels}`,
    );
  }
  // Use BigInt arithmetic for the expected-length comparison so the result
  // is exact even if width*height*channels exceeds JavaScript's 2^53 safe-
  // integer limit. With MAX_PIXELS this is unreachable for any well-formed
  // caller, but a hostile (width, height) pair fabricated outside the
  // decode pipeline can still reach this validator — keep it overflow-safe.
  const expected = BigInt(img.width) * BigInt(img.height) * BigInt(img.channels);
  if (BigInt(img.data.length) !== expected) {
    throw new ImageHashError(
      "ShapeMismatch",
      `data length ${img.data.length} does not match width*height*channels = ` +
        `${img.width}*${img.height}*${img.channels} = ${expected}`,
    );
  }
}

export function toRgb(img: RgbImage): Rgb3 {
  const { width, height, channels, data } = img;
  if (channels === 3) {
    return { width, height, data };
  }
  // channels === 4
  const out = new Uint8Array(width * height * 3);
  let si = 0;
  let di = 0;
  for (let i = 0; i < width * height; i++) {
    const r = data[si];
    const g = data[si + 1];
    const b = data[si + 2];
    const a = data[si + 3];
    out[di] = Math.trunc((r * a) / 255);
    out[di + 1] = Math.trunc((g * a) / 255);
    out[di + 2] = Math.trunc((b * a) / 255);
    si += 4;
    di += 3;
  }
  return { width, height, data: out };
}
