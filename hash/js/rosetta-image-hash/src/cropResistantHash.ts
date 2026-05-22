/**
 * crop_resistant_hash — byte-exact port of Python imagehash.crop_resistant_hash.
 *
 * Pipeline:
 *  1. Keep orig (deep copy of pixel data).
 *  2. Convert to grayscale, Lanczos resize to 300×300.
 *  3. PIL GaussianBlur(radius=2) on the grayscale image.
 *  4. PIL MedianFilter(size=3) on the blurred image.
 *  5. Float32 pixel array.
 *  6. findAllSegments(pixels, threshold=128, minSegmentSize=500).
 *  7. If no segments, synthesize a single whole-image fallback segment.
 *  8. (limit_segments not implemented here — defaults to null/disabled.)
 *  9. For each segment: compute bbox in seg-space → scale → crop orig → dhash.
 * 10. Return ImageMultiHash.
 *
 * Parity notes:
 *  - PIL crop float→int: Pillow 10.4.0 uses int(round(x)) with banker's rounding, NOT trunc.
 *  - scale_w = orig_w / 300, scale_h = orig_h / 300 (float division).
 *  - Bounding box from segment: min_y, min_x, max_y+1, max_x+1 (inclusive max).
 *  - Crop coordinates: min_x * scale_w, min_y * scale_h, (max_x+1) * scale_w, (max_y+1) * scale_h.
 */

import type { RgbImage } from "./hash.js";
import { rgbToGray } from "./averageHash.js";
import { toRgb } from "./internal/imgRgb.js";
import { resize as lanczosResize } from "./internal/lanczos.js";
import { pilGaussianBlur } from "./internal/pilGaussianBlur.js";
import { pilMedianFilter } from "./internal/pilMedianFilter.js";
import { findAllSegments } from "./internal/findSegments.js";
import { dhash } from "./dhash.js";
import { ImageMultiHash } from "./multiHash.js";

const SEG_SIZE = 300;
const SEGMENT_THRESHOLD = 128;
const MIN_SEGMENT_SIZE = 500;

/**
 * Crop an RgbImage to the given bounding box (float coordinates, rounded to int).
 *
 * Pillow 10.4.0 Image.crop() converts floats via `int(round(x))` (not trunc).
 * Python's round() uses banker's rounding (round-half-to-even), so 0.5 rounds
 * to the nearest even integer.  For non-half values Math.round() matches.
 *
 * For exact-0.5 values we apply banker's rounding to match Python precisely.
 */
function pilRound(x: number): number {
  const floored = Math.floor(x);
  const frac = x - floored;
  if (frac < 0.5) return floored;
  if (frac > 0.5) return floored + 1;
  // Exactly 0.5: round to even
  return floored % 2 === 0 ? floored : floored + 1;
}

function cropImage(img: RgbImage, left: number, top: number, right: number, bottom: number): RgbImage {
  // Pillow 10.4.0 _crop: int(round(coord)) with banker's rounding
  const x0 = pilRound(left);
  const y0 = pilRound(top);
  const x1 = pilRound(right);
  const y1 = pilRound(bottom);

  // Clamp to image bounds
  const cx0 = Math.max(0, Math.min(img.width, x0));
  const cy0 = Math.max(0, Math.min(img.height, y0));
  const cx1 = Math.max(0, Math.min(img.width, x1));
  const cy1 = Math.max(0, Math.min(img.height, y1));

  const newW = Math.max(0, cx1 - cx0);
  const newH = Math.max(0, cy1 - cy0);
  const ch = img.channels;

  const out = new Uint8Array(newW * newH * ch);
  for (let y = 0; y < newH; y++) {
    const srcRow = (cy0 + y) * img.width * ch + cx0 * ch;
    const dstRow = y * newW * ch;
    out.set(img.data.subarray(srcRow, srcRow + newW * ch), dstRow);
  }

  return { width: newW, height: newH, data: out, channels: img.channels };
}

/**
 * cropResistantHash: implements the cropping-resistant image hashing algorithm.
 *
 * @param img           The source RgbImage.
 * @param limitSegments Optional: keep only the N largest segments.
 */
export function cropResistantHash(
  img: RgbImage,
  limitSegments?: number,
): ImageMultiHash {
  // 1. Keep original for per-segment cropping
  const orig = img;

  // 2. Grayscale + Lanczos resize to 300×300
  const rgb = toRgb(img);
  let gray = rgbToGray(rgb.data, rgb.width, rgb.height);
  gray = lanczosResize(gray, rgb.width, rgb.height, SEG_SIZE, SEG_SIZE);

  // 3. PIL GaussianBlur(radius=2)
  gray = pilGaussianBlur(gray, SEG_SIZE, SEG_SIZE);

  // 4. PIL MedianFilter(size=3)
  gray = pilMedianFilter(gray, SEG_SIZE, SEG_SIZE);

  // 5. Float32 pixel array (shape: 300×300, row-major)
  const pixels = new Float32Array(SEG_SIZE * SEG_SIZE);
  for (let i = 0; i < SEG_SIZE * SEG_SIZE; i++) {
    pixels[i] = gray[i];
  }

  // 6. Find segments
  let segments = findAllSegments(pixels, SEG_SIZE, SEG_SIZE, SEGMENT_THRESHOLD, MIN_SEGMENT_SIZE);

  // 7. Whole-image fallback if no segments
  if (segments.length === 0) {
    segments = [[[0, 0], [SEG_SIZE - 1, SEG_SIZE - 1]]];
  }

  // 8. (optional) limit to M largest segments — stable sort by size descending
  if (limitSegments !== undefined && limitSegments > 0) {
    segments = segments
      .map((seg, i) => ({ seg, i }))
      .sort((a, b) => b.seg.length - a.seg.length || a.i - b.i)
      .slice(0, limitSegments)
      .map(({ seg }) => seg);
  }

  // 9. Per-segment: bbox → scale → crop → dhash
  const origW = orig.width;
  const origH = orig.height;
  const scaleW = origW / SEG_SIZE;
  const scaleH = origH / SEG_SIZE;

  const hashes = segments.map(segment => {
    let minY = Infinity, maxY = -Infinity;
    let minX = Infinity, maxX = -Infinity;
    for (const [y, x] of segment) {
      if (y < minY) minY = y;
      if (y > maxY) maxY = y;
      if (x < minX) minX = x;
      if (x > maxX) maxX = x;
    }

    // Scale to original image coords (float)
    const left  = minX * scaleW;
    const top   = minY * scaleH;
    const right  = (maxX + 1) * scaleW;
    const bottom = (maxY + 1) * scaleH;

    const cropped = cropImage(orig, left, top, right, bottom);
    return dhash(cropped, 8);
  });

  return new ImageMultiHash(hashes);
}
