import { Hash, ImageHashError, type RgbImage } from "./hash.js";
import { rgbToGray } from "./averageHash.js";
import { dct2d } from "./internal/dct.js";
import { toRgb, validateRgbImage } from "./internal/imgRgb.js";
import { resize } from "./internal/lanczos.js";

/**
 * ε threshold for the snap-to-threshold tie-break used by `phash`,
 * `phashSimple`, `whashDb4`, and `whashDb4Robust`. Coefficients within
 * `SNAP_EPS` of the threshold map deterministically to bit 0 across all
 * ports. Fixed across all 6 ports. See spec/SPEC.md §"Threshold tie-break".
 */
export const SNAP_EPS = 1e-10;

/**
 * phash: grayscale → Lanczos to (N*F, N*F) → 2-D DCT → top-left NxN block
 * → bit = (coefficient > median + {@link SNAP_EPS}).
 *
 * The snap-to-threshold tie-break (ε = 1e-10) deterministically maps
 * coefficients within ε of the median to bit 0, eliminating cross-port
 * FP-noise divergence at large hash sizes.
 */
export function phash(
  img: RgbImage,
  hashSize: number,
  highfreqFactor: number = 4,
): Hash {
  validateRgbImage(img);
  if (!Number.isInteger(hashSize)) {
    throw new ImageHashError("InvalidHashSize", `hashSize must be an integer, got ${hashSize}`);
  }
  if (hashSize < 2) {
    throw new ImageHashError("InvalidHashSize", `hashSize must be >= 2, got ${hashSize}`);
  }
  const imgSize = hashSize * highfreqFactor;

  const rgb = toRgb(img);
  const gray = rgbToGray(rgb.data, rgb.width, rgb.height);
  const resized = resize(gray, rgb.width, rgb.height, imgSize, imgSize);

  const doubles = new Float64Array(imgSize * imgSize);
  for (let i = 0; i < resized.length; i++) doubles[i] = resized[i];

  const dctOut = dct2d(doubles, imgSize);

  // Extract top-left hashSize×hashSize block
  const block = new Float64Array(hashSize * hashSize);
  let k = 0;
  for (let y = 0; y < hashSize; y++) {
    for (let x = 0; x < hashSize; x++) {
      block[k++] = dctOut[y * imgSize + x];
    }
  }
  const sorted = block.slice().sort();
  const n = sorted.length;
  const median = n % 2 === 1 ? sorted[(n - 1) / 2] : (sorted[n / 2 - 1] + sorted[n / 2]) / 2;

  // Snap-to-threshold tie-break: deterministic bit 0 on ties.
  const threshold = median + SNAP_EPS;
  const bits: boolean[][] = [];
  for (let y = 0; y < hashSize; y++) {
    const row: boolean[] = new Array(hashSize);
    for (let x = 0; x < hashSize; x++) {
      row[x] = dctOut[y * imgSize + x] > threshold;
    }
    bits.push(row);
  }
  return new Hash(bits);
}
