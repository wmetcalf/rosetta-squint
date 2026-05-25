import { Hash, ImageHashError, type RgbImage } from "./hash.js";
import { toRgb, validateRgbImage } from "./internal/imgRgb.js";
import { resize } from "./internal/lanczos.js";
import { toGray } from "./internal/pilGray.js";

/**
 * ahash: convert to grayscale, Lanczos resize to NxN, threshold against mean.
 */
export function averageHash(img: RgbImage, hashSize: number): Hash {
  validateRgbImage(img);
  if (!Number.isInteger(hashSize)) {
    throw new ImageHashError("InvalidHashSize", `hashSize must be an integer, got ${hashSize}`);
  }
  if (hashSize < 2) {
    throw new ImageHashError("InvalidHashSize", `hashSize must be >= 2, got ${hashSize}`);
  }
  const rgb = toRgb(img);
  const gray = rgbToGray(rgb.data, rgb.width, rgb.height);
  const resized = resize(gray, rgb.width, rgb.height, hashSize, hashSize);

  let sum = 0;
  for (let i = 0; i < resized.length; i++) sum += resized[i];
  const avg = sum / (hashSize * hashSize);

  const bits: boolean[][] = [];
  for (let y = 0; y < hashSize; y++) {
    const row: boolean[] = new Array(hashSize);
    for (let x = 0; x < hashSize; x++) {
      row[x] = resized[y * hashSize + x] > avg;
    }
    bits.push(row);
  }
  return new Hash(bits);
}

/** Shared helper: convert a flat RGB Uint8Array (row-major triples) to a flat grayscale Uint8Array. */
export function rgbToGray(rgb: Uint8Array, width: number, height: number): Uint8Array {
  const out = new Uint8Array(width * height);
  let si = 0;
  for (let i = 0; i < width * height; i++) {
    out[i] = toGray(rgb[si], rgb[si + 1], rgb[si + 2]);
    si += 3;
  }
  return out;
}
