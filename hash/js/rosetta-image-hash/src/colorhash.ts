import { Hash, ImageHashError, type RgbImage } from "./hash.js";
import { toRgb } from "./internal/imgRgb.js";
import { toGray } from "./internal/pilGray.js";
import { toHsv } from "./internal/pilHsv.js";

/**
 * colorhash: HSV-binned histogram hash.
 *
 * Bins: black (L<32), gray (L>=32 and S<85), 6 faint hue bins (85<=S<170),
 * 6 bright hue bins (S>170). S==170 increments colorful denominator but
 * lands in neither hue histogram.
 *
 * Quirky bin encoding (SPEC.md §8): v=8, B=4 → [true,true,false,false] = 0xc.
 */
export function colorhash(img: RgbImage, binbits: number): Hash {
  if (binbits < 1) {
    throw new ImageHashError("InvalidBinbits", `binbits must be >= 1, got ${binbits}`);
  }
  const rgb = toRgb(img);
  const data = rgb.data;
  const w = rgb.width;
  const h = rgb.height;
  const n = w * h;

  let blackCount = 0;
  let grayCount = 0;
  let colorfulCount = 0;
  const faintBins = [0, 0, 0, 0, 0, 0];
  const brightBins = [0, 0, 0, 0, 0, 0];

  let si = 0;
  for (let i = 0; i < n; i++) {
    const r = data[si];
    const g = data[si + 1];
    const b = data[si + 2];
    si += 3;
    const l = toGray(r, g, b);
    if (l < 32) {
      blackCount++;
      continue;
    }
    const [hue, s] = toHsv(r, g, b);
    if (s < 85) {
      grayCount++;
      continue;
    }
    colorfulCount++;
    let hueBin = Math.trunc((hue * 6) / 255);
    if (hueBin > 5) hueBin = 5;
    if (s < 170) {
      faintBins[hueBin]++;
    } else if (s > 170) {
      brightBins[hueBin]++;
    }
  }

  const maxVal = 1 << binbits;
  const c = Math.max(1, colorfulCount);
  const clip = (v: number): number => (v > maxVal - 1 ? maxVal - 1 : v);

  const values: number[] = new Array(14);
  values[0] = clip(Math.trunc((blackCount / n) * maxVal));
  values[1] = clip(Math.trunc((grayCount / n) * maxVal));
  for (let i = 0; i < 6; i++) {
    values[2 + i] = clip(Math.trunc((faintBins[i] * maxVal) / c));
    values[8 + i] = clip(Math.trunc((brightBins[i] * maxVal) / c));
  }

  const bits: boolean[][] = [];
  for (let i = 0; i < 14; i++) {
    bits.push(colorhashBinEncode(values[i], binbits));
  }
  return new Hash(bits);
}

/**
 * SPEC.md §8 quirky bin encoding.
 *
 * bit[i] = (v >> (B-i-1)) & ((1 << (B-i)) - 1) > 0
 *
 * Worked: v=4 → [0,1,1,0] (0x6), v=8 → [1,1,0,0] (0xc). NOT standard binary.
 */
export function colorhashBinEncode(v: number, binbits: number): boolean[] {
  const bits: boolean[] = new Array(binbits);
  for (let i = 0; i < binbits; i++) {
    const shifted = v >>> (binbits - i - 1);
    const masked = shifted & ((1 << (binbits - i)) - 1);
    bits[i] = masked > 0;
  }
  return bits;
}
