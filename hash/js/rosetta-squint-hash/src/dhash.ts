import { Hash, ImageHashError, type RgbImage } from "./hash.js";
import { rgbToGray } from "./averageHash.js";
import { toRgb, validateRgbImage } from "./internal/imgRgb.js";
import { resize } from "./internal/lanczos.js";

/**
 * dhash: grayscale → Lanczos to (W=N+1, H=N) → row-wise adjacent-column diff (strict >).
 */
export function dhash(img: RgbImage, hashSize: number): Hash {
  validateRgbImage(img);
  if (!Number.isInteger(hashSize)) {
    throw new ImageHashError("InvalidHashSize", `hashSize must be an integer, got ${hashSize}`);
  }
  if (hashSize < 2) {
    throw new ImageHashError("InvalidHashSize", `hashSize must be >= 2, got ${hashSize}`);
  }
  const rgb = toRgb(img);
  const gray = rgbToGray(rgb.data, rgb.width, rgb.height);
  const resized = resize(gray, rgb.width, rgb.height, hashSize + 1, hashSize);
  // resized is [hashSize rows][hashSize+1 cols] flattened row-major.

  const bits: boolean[][] = [];
  for (let y = 0; y < hashSize; y++) {
    const rowOff = y * (hashSize + 1);
    const row: boolean[] = new Array(hashSize);
    for (let x = 0; x < hashSize; x++) {
      row[x] = resized[rowOff + x + 1] > resized[rowOff + x];
    }
    bits.push(row);
  }
  return new Hash(bits);
}
