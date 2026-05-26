import { Hash, ImageHashError, type RgbImage } from "./hash.js";
import { rgbToGray } from "./averageHash.js";
import { wavedec2, waverec2 } from "./internal/haar.js";
import { db4Wavedec2WithShape } from "./internal/db4Dwt.js";
import { toRgb, validateRgbImage } from "./internal/imgRgb.js";
import { resize } from "./internal/lanczos.js";
import { SNAP_EPS } from "./phash.js";

function isPowerOfTwo(n: number): boolean {
  return n > 0 && (n & (n - 1)) === 0;
}

/** ε threshold for whash_db4_robust snap-to-zero. See spec/SPEC.md. */
export const WHASH_DB4_ROBUST_EPS = 1e-12;

/**
 * Cross-port-stable variant of whashDb4. Same pipeline up to LL band, then
 * snaps |c| < WHASH_DB4_ROBUST_EPS to 0 before median + threshold. Real-world
 * photos produce the same hash as whashDb4. Pathological symmetric inputs
 * collapse to a deterministic hash across all ports. NOT byte-exact-compatible
 * with Python imagehash on pathological inputs.
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
 * 9. ll = coeffs[0]; snap |c| < WHASH_DB4_ROBUST_EPS to 0; median threshold; pack to hex.
 */
export function whashDb4Robust(img: RgbImage, hashSize: number): Hash {
  validateRgbImage(img);
  if (!Number.isInteger(hashSize)) {
    throw new ImageHashError("InvalidHashSize", `hashSize must be an integer, got ${hashSize}`);
  }
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

  // snap near-zero coefficients to exactly zero for cross-port stability
  for (let y = 0; y < ll.length; y++) {
    for (let x = 0; x < ll[y].length; x++) {
      if (Math.abs(ll[y][x]) < WHASH_DB4_ROBUST_EPS) {
        ll[y][x] = 0;
      }
    }
  }

  const flat: number[] = [];
  for (const row of ll) flat.push(...row);
  const sorted = flat.slice().sort((a, b) => a - b);
  const n = sorted.length;
  const median = n % 2 === 1 ? sorted[(n - 1) / 2] : (sorted[n / 2 - 1] + sorted[n / 2]) / 2;

  // Snap-to-threshold tie-break (on top of snap-to-zero): deterministic
  // bit 0 on ties. See spec/SPEC.md §"Threshold tie-break".
  const threshold = median + SNAP_EPS;
  const bits: boolean[][] = [];
  for (const row of ll) {
    bits.push(row.map(v => v > threshold));
  }
  return new Hash(bits);
}
