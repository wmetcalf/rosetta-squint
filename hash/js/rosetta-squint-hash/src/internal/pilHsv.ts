/**
 * PIL 'HSV' conversion using the integer formula from libImaging/Convert.c rgb2hsv_row.
 *
 * IMPORTANT: PIL uses INTEGER arithmetic on uint8 RGB directly, not a float HSV
 * scaled by 255. Naive Math.round(float_hsv * 255) diverges on .5 boundaries.
 * Example: RGB(100,150,200) → PIL (148,127,200); round (149,128,200).
 *
 * The negative-h_pre case (r === maxc and bc < gc) is wrapped via `+= 6 * 255`
 * so the final division is on a non-negative integer. All integer divisions
 * use Math.trunc() since JS `/` is float division.
 */

export function toHsv(r: number, g: number, b: number): [number, number, number] {
  const maxc = Math.max(r, g, b);
  const minc = Math.min(r, g, b);
  const v = maxc;
  if (maxc === 0) return [0, 0, v];
  const s = Math.trunc((255 * (maxc - minc)) / maxc);
  if (minc === maxc) return [0, s, v];
  const delta = maxc - minc;
  const rc = Math.trunc(((maxc - r) * 255) / delta);
  const gc = Math.trunc(((maxc - g) * 255) / delta);
  const bc = Math.trunc(((maxc - b) * 255) / delta);
  let hPre: number;
  if (r === maxc) {
    hPre = bc - gc;
  } else if (g === maxc) {
    hPre = 2 * 255 + rc - bc;
  } else {
    hPre = 4 * 255 + gc - rc;
  }
  if (hPre < 0) hPre += 6 * 255;
  const h = Math.trunc(hPre / 6);
  return [h, s, v];
}
