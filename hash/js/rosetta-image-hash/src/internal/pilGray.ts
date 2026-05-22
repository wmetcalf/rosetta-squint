/**
 * PIL 'L' (grayscale) conversion via the fixed-point ITU-R 601 luma formula.
 *
 * Matches Pillow `Image.convert('L')` exactly. Uses Math.trunc-equivalent
 * (>>> 0) to handle the right-shift in 32-bit unsigned int space.
 */

export function toGray(r: number, g: number, b: number): number {
  // (R*19595 + G*38470 + B*7471 + 32768) >> 16
  // The pre-shift value fits in 32-bit unsigned int (max 65535*65536 ~= 4.3e9 > 2^32).
  // Use Math.trunc(.. / 65536) instead of >>> to avoid int32 truncation issues.
  const acc = r * 19595 + g * 38470 + b * 7471 + 32768;
  return Math.trunc(acc / 65536);
}
