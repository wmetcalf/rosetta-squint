import { Hash, ImageHashError, type RgbImage } from "./hash.js";
import { rgbToGray } from "./averageHash.js";
import { dct1d } from "./internal/dct.js";
import { toRgb } from "./internal/imgRgb.js";
import { resize } from "./internal/lanczos.js";

/**
 * phashSimple: perceptual hash using 1-D row-wise DCT, mean threshold.
 *
 * Unlike phash (which uses a 2-D DCT and median), phashSimple:
 *  1. Grayscale → Lanczos resize to (N*4, N*4).
 *  2. Apply 1-D DCT to each ROW (scipy.fftpack.dct(pixels) default axis=last=rows).
 *  3. Take top-left N rows but skip the DC column: block = dct[0:N, 1:N+1].
 *  4. avg = mean(block) as float64.
 *  5. bit = block > avg (strict >).
 *
 * This matches Python imagehash.phash_simple exactly (imagehash 4.3.2).
 */
export function phashSimple(
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

  // Apply 1-D DCT to each row of the resized image
  // resized is row-major: row y starts at y*imgSize
  const dctRows: Float64Array[] = [];
  for (let y = 0; y < imgSize; y++) {
    const row = new Float64Array(imgSize);
    for (let x = 0; x < imgSize; x++) {
      row[x] = resized[y * imgSize + x];
    }
    dctRows.push(dct1d(row));
  }

  // Extract block: rows 0..hashSize-1, columns 1..hashSize (skip DC at col 0)
  const blockLen = hashSize * hashSize;
  const block = new Float64Array(blockLen);
  let k = 0;
  for (let y = 0; y < hashSize; y++) {
    for (let x = 1; x <= hashSize; x++) {
      block[k++] = dctRows[y][x];
    }
  }

  // Mean threshold
  let sum = 0;
  for (let i = 0; i < blockLen; i++) sum += block[i];
  const avg = sum / blockLen;

  const bits: boolean[][] = [];
  let bi = 0;
  for (let y = 0; y < hashSize; y++) {
    const row: boolean[] = new Array(hashSize);
    for (let x = 0; x < hashSize; x++) {
      row[x] = block[bi++] > avg;
    }
    bits.push(row);
  }
  return new Hash(bits);
}
