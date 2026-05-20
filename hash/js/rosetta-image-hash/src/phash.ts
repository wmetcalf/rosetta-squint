import { Hash, ImageHashError, type RgbImage } from "./hash.js";
import { rgbToGray } from "./averageHash.js";
import { dct2d } from "./internal/dct.js";
import { toRgb } from "./internal/imgRgb.js";
import { resize } from "./internal/lanczos.js";

/**
 * phash: grayscale → Lanczos to (N*F, N*F) → 2-D DCT → top-left NxN block → median threshold.
 */
export function phash(
  img: RgbImage,
  hashSize: number,
  highfreqFactor: number = 4,
): Hash {
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

  const bits: boolean[][] = [];
  for (let y = 0; y < hashSize; y++) {
    const row: boolean[] = new Array(hashSize);
    for (let x = 0; x < hashSize; x++) {
      row[x] = dctOut[y * imgSize + x] > median;
    }
    bits.push(row);
  }
  return new Hash(bits);
}
