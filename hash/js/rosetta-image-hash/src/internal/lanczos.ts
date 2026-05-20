/**
 * Pillow-compatible Lanczos3 resize on uint8 grayscale (Uint8Array, row-major).
 *
 * Reproduces libImaging/Resample.c precompute_coeffs precisely:
 *   - center = (idx + 0.5) * scale
 *   - filter_scale = max(1.0, scale)
 *   - support = 3.0 * filter_scale
 *   - kernel = sinc(x) * sinc(x/3) for |x| < 3
 *   - xmin = trunc(center - support + 0.5), clamped to [0, src_size)
 *   - xmax = trunc(center + support + 0.5), clamped to (xmin, src_size] EXCLUSIVE
 *   - weights normalized per output pixel
 *   - PRECISION_BITS = 32 - 8 - 2 = 22 for fixed-point coefficients (NOT 32)
 *
 * IMPORTANT JS-specific note: the accumulator can exceed 2^31, so we CANNOT use
 * bitwise `>>>` (which truncates to int32). Use float arithmetic + Math.floor.
 */

const PRECISION_BITS = 32 - 8 - 2; // = 22 — matches Pillow's #define PRECISION_BITS
const SUPPORT = 3.0;
const PRECISION_SCALE = 1 << PRECISION_BITS; // 4194304, fits in int32

export function resize(
  src: Uint8Array,
  srcW: number,
  srcH: number,
  dstW: number,
  dstH: number,
): Uint8Array {
  const { offsets: offsH, lengths: lensH, weights: weightsH } = precomputeCoeffs(srcW, dstW);
  const mid = new Uint8Array(srcH * dstW);
  for (let y = 0; y < srcH; y++) {
    const rowOff = y * srcW;
    for (let xd = 0; xd < dstW; xd++) {
      let acc = 0;
      const w = weightsH[xd];
      const off = offsH[xd];
      const len = lensH[xd];
      for (let i = 0; i < len; i++) {
        acc += w[i] * src[rowOff + off + i];
      }
      mid[y * dstW + xd] = clip8(acc);
    }
  }

  const { offsets: offsV, lengths: lensV, weights: weightsV } = precomputeCoeffs(srcH, dstH);
  const result = new Uint8Array(dstH * dstW);
  for (let yd = 0; yd < dstH; yd++) {
    const w = weightsV[yd];
    const off = offsV[yd];
    const len = lensV[yd];
    for (let x = 0; x < dstW; x++) {
      let acc = 0;
      for (let i = 0; i < len; i++) {
        acc += w[i] * mid[(off + i) * dstW + x];
      }
      result[yd * dstW + x] = clip8(acc);
    }
  }
  return result;
}

function clip8(acc: number): number {
  // Math.floor matches Java's arithmetic right shift for both positive and negative acc
  // (rounds toward -infinity). Adding (1 << (PRECISION_BITS - 1)) before division
  // is the round-half-up bias used by Pillow.
  const rounded = Math.floor((acc + (1 << (PRECISION_BITS - 1))) / PRECISION_SCALE);
  if (rounded < 0) return 0;
  if (rounded > 255) return 255;
  return rounded;
}

interface CoeffTable {
  offsets: number[];
  lengths: number[];
  weights: Int32Array[]; // per dst pixel, length = lengths[i]
}

function precomputeCoeffs(srcSize: number, dstSize: number): CoeffTable {
  const scale = srcSize / dstSize;
  const filterScale = Math.max(1.0, scale);
  const support = SUPPORT * filterScale;

  const offsets: number[] = new Array(dstSize);
  const lengths: number[] = new Array(dstSize);
  const weights: Int32Array[] = new Array(dstSize);

  for (let xd = 0; xd < dstSize; xd++) {
    const center = (xd + 0.5) * scale;
    // Math.trunc matches Pillow's `(int)(x + 0.5)` for non-negative x (truncates toward zero).
    let xmin = Math.trunc(center - support + 0.5);
    if (xmin < 0) xmin = 0;
    let xmax = Math.trunc(center + support + 0.5);
    if (xmax > srcSize) xmax = srcSize;
    const n = Math.max(0, xmax - xmin);

    const tmp = new Float64Array(n);
    let wsum = 0;
    for (let i = 0; i < n; i++) {
      const dx = ((xmin + i) + 0.5 - center) / filterScale;
      const w = lanczosKernel(dx);
      tmp[i] = w;
      wsum += w;
    }
    if (wsum !== 0) {
      for (let i = 0; i < n; i++) tmp[i] /= wsum;
    }
    const q = new Int32Array(n);
    for (let i = 0; i < n; i++) {
      q[i] = Math.round(tmp[i] * PRECISION_SCALE);
    }
    offsets[xd] = xmin;
    lengths[xd] = n;
    weights[xd] = q;
  }
  return { offsets, lengths, weights };
}

function lanczosKernel(x: number): number {
  if (x === 0) return 1;
  const ax = Math.abs(x);
  if (ax >= SUPPORT) return 0;
  const px = Math.PI * x;
  return (Math.sin(px) / px) * (Math.sin(px / SUPPORT) / (px / SUPPORT));
}
