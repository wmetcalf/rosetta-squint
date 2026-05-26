/**
 * PIL GaussianBlur(radius=2) — byte-exact port of Pillow 10.4.0 BoxBlur.c.
 *
 * Pillow approximates a Gaussian by repeated box filtering. For radius=2 and
 * n=3 passes, the effective float box-radius is ~1.375 (from _gaussian_blur_radius).
 *
 * Algorithm:
 *   1. Compute floatRadius = _gaussian_blur_radius(sigma, passes).
 *   2. Compute integer weights ww/fw from floatRadius.
 *   3. Apply ALL n horizontal passes on the image rows.
 *   4. Transpose, apply ALL n horizontal passes (= vertical passes), transpose back.
 *
 * Boundary: edge replication (clamp). Intermediates stay in float/int64 range;
 * each 8-bit output pixel is (bulk + 2^23) >> 24, clamped to [0,255].
 *
 * Reference: spec/gaussian_blur_cases.json  (6 cases, all must pass byte-exact).
 */

/** _gaussian_blur_radius from BoxBlur.c */
function gaussianBlurRadius(sigma: number, passes: number): number {
  const sigma2 = (sigma * sigma) / passes;
  const L = Math.sqrt(12.0 * sigma2 + 1.0);
  const l = Math.floor((L - 1.0) / 2.0);
  const a =
    (2 * l + 1) * (l * (l + 1) - 3 * sigma2) /
    (6 * (sigma2 - (l + 1) * (l + 1)));
  return l + a;
}

/**
 * ImagingLineBoxBlur8 — one 1-D box-blur pass on a uint8 line.
 * lineIn must be Uint8Array or number[]; lineOut is a plain number[] of uint8.
 */
function boxBlurLine8(
  lineIn: Uint8Array | number[],
  n: number,
  radius: number,
  ww: number,
  fw: number,
  edgeA: number,
  edgeB: number,
): number[] {
  const lastx = n - 1;
  const lineOut: number[] = new Array(n);

  // Initial accumulator: from -(radius+1) to radius-1 with clamping
  let acc =
    lineIn[0] * (radius + 1);
  for (let x = 0; x < edgeA - 1; x++) {
    acc += lineIn[x];
  }
  acc += lineIn[lastx] * (radius - edgeA + 1);

  if (edgeA <= edgeB) {
    for (let x = 0; x < edgeA; x++) {
      acc += lineIn[x + radius] - lineIn[0];
      const bulk = acc * ww + (lineIn[0] + lineIn[x + radius + 1]) * fw;
      // Use >>> 24 (unsigned) to match C's UINT32 >> 24 — bulk can exceed 2^31.
      lineOut[x] = (bulk + (1 << 23)) >>> 24;
    }
    for (let x = edgeA; x < edgeB; x++) {
      acc += lineIn[x + radius] - lineIn[x - radius - 1];
      const bulk =
        acc * ww + (lineIn[x - radius - 1] + lineIn[x + radius + 1]) * fw;
      lineOut[x] = (bulk + (1 << 23)) >>> 24;
    }
    for (let x = edgeB; x <= lastx; x++) {
      acc += lineIn[lastx] - lineIn[x - radius - 1];
      const bulk =
        acc * ww + (lineIn[x - radius - 1] + lineIn[lastx]) * fw;
      lineOut[x] = (bulk + (1 << 23)) >>> 24;
    }
  } else {
    for (let x = 0; x < edgeB; x++) {
      acc += lineIn[x + radius] - lineIn[0];
      const bulk = acc * ww + (lineIn[0] + lineIn[x + radius + 1]) * fw;
      lineOut[x] = (bulk + (1 << 23)) >>> 24;
    }
    for (let x = edgeB; x < edgeA; x++) {
      acc += lineIn[lastx] - lineIn[0];
      const bulk = acc * ww + (lineIn[0] + lineIn[lastx]) * fw;
      lineOut[x] = (bulk + (1 << 23)) >>> 24;
    }
    for (let x = edgeA; x <= lastx; x++) {
      acc += lineIn[lastx] - lineIn[x - radius - 1];
      const bulk =
        acc * ww + (lineIn[x - radius - 1] + lineIn[lastx]) * fw;
      lineOut[x] = (bulk + (1 << 23)) >>> 24;
    }
  }

  return lineOut;
}

/**
 * Apply PIL GaussianBlur(radius=2) to a flat uint8 grayscale array (row-major, H×W).
 * Returns a new Uint8Array of the same size.
 *
 * @param src    Flat grayscale uint8 pixels, length = width * height.
 * @param width  Image width in pixels.
 * @param height Image height in pixels.
 */
export function pilGaussianBlur(
  src: Uint8Array,
  width: number,
  height: number,
): Uint8Array {
  const SIGMA = 2.0;
  const PASSES = 3;

  const floatRadius = gaussianBlurRadius(SIGMA, PASSES);
  const radius = Math.trunc(floatRadius); // (int) cast in C
  const ww = Math.trunc((1 << 24) / (floatRadius * 2 + 1)); // UINT32 division
  const fw = Math.trunc(((1 << 24) - (radius * 2 + 1) * ww) / 2);

  const edgeA_h = Math.min(radius + 1, width);
  const edgeB_h = Math.max(width - radius - 1, 0);
  const edgeA_v = Math.min(radius + 1, height);
  const edgeB_v = Math.max(height - radius - 1, 0);

  // Working buffer as number[] for arithmetic safety (avoid uint8 overflow mid-pass)
  let arr: number[] = Array.from(src);

  // All n horizontal passes
  for (let p = 0; p < PASSES; p++) {
    const tmp: number[] = new Array(width * height);
    for (let y = 0; y < height; y++) {
      const row = arr.slice(y * width, y * width + width);
      const out = boxBlurLine8(row, width, radius, ww, fw, edgeA_h, edgeB_h);
      for (let x = 0; x < width; x++) {
        tmp[y * width + x] = out[x];
      }
    }
    arr = tmp;
  }

  // All n vertical passes (Pillow transposes, blurs rows, transposes back)
  // Transposed array: shape (width, height) — transposed[x][y] = arr[y][x]
  let transposed: number[] = new Array(width * height);
  for (let y = 0; y < height; y++) {
    for (let x = 0; x < width; x++) {
      transposed[x * height + y] = arr[y * width + x];
    }
  }

  for (let p = 0; p < PASSES; p++) {
    const tmp: number[] = new Array(width * height);
    for (let x = 0; x < width; x++) {
      const col = transposed.slice(x * height, x * height + height);
      const out = boxBlurLine8(col, height, radius, ww, fw, edgeA_v, edgeB_v);
      for (let y = 0; y < height; y++) {
        tmp[x * height + y] = out[y];
      }
    }
    transposed = tmp;
  }

  // Transpose back
  const result = new Uint8Array(width * height);
  for (let y = 0; y < height; y++) {
    for (let x = 0; x < width; x++) {
      result[y * width + x] = transposed[x * height + y];
    }
  }

  return result;
}
