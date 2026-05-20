/**
 * Normalize an RgbImage (3 or 4 channels) to a flat Uint8Array of RGB triples
 * (row-major, length = width * height * 3).
 *
 * For channels=4, alpha is composited against opaque black via
 * `out_c = floor(src_c * alpha / 255)`, matching PIL `convert('RGB')`.
 * Fully transparent → (0,0,0); fully opaque → src RGB unchanged.
 */

import type { RgbImage } from "../hash.js";

export interface Rgb3 {
  width: number;
  height: number;
  /** Length = width * height * 3, row-major. */
  data: Uint8Array;
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
