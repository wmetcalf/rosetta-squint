/**
 * PIL MedianFilter(size=3) — byte-exact port.
 *
 * For each output pixel at (y, x), gather the 9 values in the 3×3 window
 * (with edge-replication / clamp for out-of-bounds coordinates), sort them,
 * and take the median at index 4.
 *
 * Reference: spec/median_filter_cases.json.
 */

/**
 * Apply PIL MedianFilter(size=3) to a flat uint8 grayscale array (row-major, H×W).
 * Returns a new Uint8Array of the same size.
 *
 * @param src    Flat grayscale uint8 pixels, length = width * height.
 * @param width  Image width.
 * @param height Image height.
 */
export function pilMedianFilter(
  src: Uint8Array,
  width: number,
  height: number,
): Uint8Array {
  const result = new Uint8Array(width * height);
  const window: number[] = new Array(9);

  for (let y = 0; y < height; y++) {
    for (let x = 0; x < width; x++) {
      let k = 0;
      for (let dy = -1; dy <= 1; dy++) {
        const ry = Math.max(0, Math.min(height - 1, y + dy));
        for (let dx = -1; dx <= 1; dx++) {
          const rx = Math.max(0, Math.min(width - 1, x + dx));
          window[k++] = src[ry * width + rx];
        }
      }
      // Sort 9 values and take index 4 (the median)
      window.sort((a, b) => a - b);
      result[y * width + x] = window[4];
    }
  }

  return result;
}
