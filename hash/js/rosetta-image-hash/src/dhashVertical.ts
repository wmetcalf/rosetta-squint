import { Hash, ImageHashError, type RgbImage } from "./hash.js";
import { rgbToGray } from "./averageHash.js";
import { toRgb } from "./internal/imgRgb.js";
import { resize } from "./internal/lanczos.js";

/**
 * dhashVertical: pre-3.0 back-compat vertical dhash.
 *
 * Compares vertically adjacent pixels instead of horizontally adjacent.
 * Resize to (width=N, height=N+1), then bit[y][x] = pixel[y+1][x] > pixel[y][x].
 *
 * This preserves the pre-3.0 (buggy) dhash direction for backward compatibility.
 */
export function dhashVertical(img: RgbImage, hashSize: number): Hash {
  if (hashSize < 2) {
    throw new ImageHashError("InvalidHashSize", `hashSize must be >= 2, got ${hashSize}`);
  }
  const rgb = toRgb(img);
  const gray = rgbToGray(rgb.data, rgb.width, rgb.height);
  // Resize to (width=N, height=N+1) — note: resize(src, srcW, srcH, dstW, dstH)
  const resized = resize(gray, rgb.width, rgb.height, hashSize, hashSize + 1);
  // resized is [(hashSize+1) rows][hashSize cols] flattened row-major.

  const bits: boolean[][] = [];
  for (let y = 0; y < hashSize; y++) {
    const row: boolean[] = new Array(hashSize);
    for (let x = 0; x < hashSize; x++) {
      // pixel[y+1][x] > pixel[y][x]
      row[x] = resized[(y + 1) * hashSize + x] > resized[y * hashSize + x];
    }
    bits.push(row);
  }
  return new Hash(bits);
}
