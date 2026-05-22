import { Hash, ImageHashError, type RgbImage } from "./hash.js";
import { rgbToGray } from "./averageHash.js";
import { wavedec2, waverec2 } from "./internal/haar.js";
import { db4Wavedec2WithShape, db4Waverec2 } from "./internal/db4Dwt.js";
import { toRgb } from "./internal/imgRgb.js";
import { resize } from "./internal/lanczos.js";

function isPowerOfTwo(n: number): boolean {
  return n > 0 && (n & (n - 1)) === 0;
}

/**
 * whashDb4: same pipeline as whashHaar but using the Daubechies-4 wavelet
 * for the final decomposition (steps 10-12). Steps 1-9 (including the
 * Haar LL-zeroing trick) are identical to whashHaar.
 *
 * Pipeline:
 * 1. image_natural_scale = 2**floor(log2(min(W,H)))
 * 2. image_scale = max(image_natural_scale, hash_size)
 * 3. ll_max_level = floor(log2(image_scale)), level = floor(log2(hash_size))
 *    dwt_level = ll_max_level - level
 * 4. Validate: hash_size is power of 2 and level <= ll_max_level.
 * 5. Grayscale + Lanczos resize to (image_scale, image_scale).
 * 6. pixels = uint8 / 255.0 (float64).
 * 7. Haar wavedec2 at ll_max_level, zero LL, Haar waverec2.
 * 8. db4 wavedec2 at dwt_level (mode='symmetric').
 * 9. ll = coeffs[0]; median threshold; pack to hex.
 */
export function whashDb4(img: RgbImage, hashSize: number): Hash {
  if (hashSize < 2) {
    throw new ImageHashError("InvalidHashSize", `hashSize must be >= 2, got ${hashSize}`);
  }
  if (!isPowerOfTwo(hashSize)) {
    throw new ImageHashError("NotPowerOfTwo", `hashSize must be a power of 2 for whash, got ${hashSize}`);
  }

  const rgb = toRgb(img);
  const gray = rgbToGray(rgb.data, rgb.width, rgb.height);

  const minSide = Math.min(rgb.width, rgb.height);
  const imageNaturalScale = 1 << Math.floor(Math.log2(minSide));
  const imageScale = Math.max(imageNaturalScale, hashSize);

  const llMaxLevel = Math.floor(Math.log2(imageScale));
  const level = Math.floor(Math.log2(hashSize));
  if (level > llMaxLevel) {
    throw new ImageHashError(
      "HashSizeTooLarge",
      `hashSize too large for image (level=${level} > ll_max_level=${llMaxLevel})`,
    );
  }
  const dwtLevel = llMaxLevel - level;

  const resized = resize(gray, rgb.width, rgb.height, imageScale, imageScale);
  const pixels: number[][] = [];
  for (let y = 0; y < imageScale; y++) {
    const row: number[] = new Array(imageScale);
    for (let x = 0; x < imageScale; x++) {
      row[x] = resized[y * imageScale + x] / 255.0;
    }
    pixels.push(row);
  }

  // remove_max_haar_ll: full Haar decomp, zero LL, reconstruct
  const fullDec = wavedec2(pixels, llMaxLevel);
  for (let y = 0; y < fullDec.cA.length; y++) {
    for (let x = 0; x < fullDec.cA[y].length; x++) {
      fullDec.cA[y][x] = 0;
    }
  }
  const modified = waverec2(fullDec);

  // db4 decomposition
  const dec = db4Wavedec2WithShape(modified, dwtLevel);
  const ll = dec.cA;

  const flat: number[] = [];
  for (const row of ll) flat.push(...row);
  const sorted = flat.slice().sort((a, b) => a - b);
  const n = sorted.length;
  const median = n % 2 === 1 ? sorted[(n - 1) / 2] : (sorted[n / 2 - 1] + sorted[n / 2]) / 2;

  const bits: boolean[][] = [];
  for (const row of ll) {
    bits.push(row.map(v => v > median));
  }
  return new Hash(bits);
}
