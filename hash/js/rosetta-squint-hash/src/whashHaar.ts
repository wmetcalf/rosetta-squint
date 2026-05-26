import { Hash, ImageHashError, type RgbImage } from "./hash.js";
import { rgbToGray } from "./averageHash.js";
import { wavedec2, waverec2 } from "./internal/haar.js";
import { toRgb, validateRgbImage } from "./internal/imgRgb.js";
import { resize } from "./internal/lanczos.js";

function isPowerOfTwo(n: number): boolean {
  return n > 0 && (n & (n - 1)) === 0;
}

/**
 * whash with mode='haar', remove_max_haar_ll=true, image_scale=None.
 */
export function whashHaar(img: RgbImage, hashSize: number): Hash {
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

  // remove_max_haar_ll: full decomp, zero LL, reconstruct
  const fullDec = wavedec2(pixels, llMaxLevel);
  for (let y = 0; y < fullDec.cA.length; y++) {
    for (let x = 0; x < fullDec.cA[y].length; x++) {
      fullDec.cA[y][x] = 0;
    }
  }
  const modified = waverec2(fullDec);

  const dec = wavedec2(modified, dwtLevel);
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
