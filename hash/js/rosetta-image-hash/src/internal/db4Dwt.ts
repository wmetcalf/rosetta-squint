/**
 * 2-D Daubechies-4 DWT/IDWT with pywt's 'symmetric' boundary mode.
 *
 * Matches PyWavelets `pywt.wavedec2(x, 'db4', mode='symmetric')`.
 *
 * Filter bank (length 8, from pywt.Wavelet('db4')):
 *   dec_lo, dec_hi: analysis (decomposition) filters.
 *   rec_lo, rec_hi: synthesis (reconstruction) filters.
 *
 * 'symmetric' boundary mode (half-sample symmetric, boundary value repeated):
 *   x[-1]=x[0], x[-2]=x[1], ..., x[N]=x[N-1], x[N+1]=x[N-2], ...
 *   Period = 2*N.
 *
 * Forward 1-D DWT formula:
 *   pad = FILTER_LEN - 1 = 7 samples on each side.
 *   ext = symExtend(x, pad, pad)  // length = pad + N + pad
 *   out_len = (N + FILTER_LEN - 1) >> 1 = (N + 7) >> 1
 *   cA[i] = dot(dec_lo_reversed, ext[2i+1 .. 2i+1+8])
 *   cD[i] = dot(dec_hi_reversed, ext[2i+1 .. 2i+1+8])
 *   (Note: start = 2i+1, not 2i; dec_lo used in reversed order = convolution.)
 *
 * Inverse 1-D DWT:
 *   1. Upsample cA, cD (insert 0 between each sample).
 *   2. y = linearConv(cA_up, rec_lo) + linearConv(cD_up, rec_hi)
 *   3. x = y[FILTER_LEN-2 .. FILTER_LEN-2+N] = y[6 .. 6+N]
 *
 * 2-D DWT: column pass first (axis 0), then row pass (axis 1) — matching pywt.
 * 2-D IDWT: row pass first (undo axis 1), then column pass (undo axis 0).
 */

// pywt Wavelet('db4') filter coefficients (8 taps each, IEEE-754 exact).
const DEC_LO: readonly number[] = [
  -0.010597401785069032,
  0.0328830116668852,
  0.030841381835560764,
  -0.18703481171909309,
  -0.027983769416859854,
  0.6308807679298589,
  0.7148465705529157,
  0.2303778133088965,
];
const DEC_HI: readonly number[] = [
  -0.2303778133088965,
  0.7148465705529157,
  -0.6308807679298589,
  -0.027983769416859854,
  0.18703481171909309,
  0.030841381835560764,
  -0.0328830116668852,
  -0.010597401785069032,
];
const REC_LO: readonly number[] = [
  0.2303778133088965,
  0.7148465705529157,
  0.6308807679298589,
  -0.027983769416859854,
  -0.18703481171909309,
  0.030841381835560764,
  0.0328830116668852,
  -0.010597401785069032,
];
const REC_HI: readonly number[] = [
  -0.010597401785069032,
  -0.0328830116668852,
  0.030841381835560764,
  0.18703481171909309,
  -0.027983769416859854,
  -0.6308807679298589,
  0.7148465705529157,
  -0.2303778133088965,
];

const FILTER_LEN = 8;
const TRIM_LEFT = FILTER_LEN - 2; // = 6, for reconstruction

export interface Db4WavedecResult {
  cA: number[][];
  /** Each entry is [cH, cV, cD] for one level. Outer-to-inner = deepest-first. */
  details: number[][][][];
  /** Original (height, width) of the input at each level (index 0 = outermost). */
  inputSizes: [number, number][];
}

// --- Utility ---

/** Output length for one level of db4 DWT on input of length N. */
export function db4OutLen(N: number): number {
  return (N + FILTER_LEN - 1) >> 1; // (N + 7) >> 1
}

/**
 * Map index i into [0, N) via half-sample-symmetric (boundary-included) extension.
 * Period = 2*N; if i >= N, mirror as 2*N - 1 - i.
 */
function reflectIdx(i: number, N: number): number {
  if (N === 1) return 0;
  const period = 2 * N;
  let p = i % period;
  if (p < 0) p += period;
  if (p >= N) p = period - 1 - p;
  return p;
}

// --- 1-D forward DWT ---

/**
 * 1-D db4 DWT with symmetric boundary extension.
 * Extends x by FILTER_LEN-1 = 7 samples on each side.
 * For output index i: start = 2*i+1, dot with dec_lo/dec_hi reversed.
 * Returns [cA, cD].
 */
function dwt1d(x: number[]): [number[], number[]] {
  const N = x.length;
  const outLen = db4OutLen(N);
  const pad = FILTER_LEN - 1; // 7
  const extLen = pad + N + pad; // pad + N + pad

  // Build extended signal
  const ext = new Array<number>(extLen);
  for (let i = 0; i < extLen; i++) {
    ext[i] = x[reflectIdx(i - pad, N)];
  }

  const cA = new Array<number>(outLen);
  const cD = new Array<number>(outLen);
  for (let i = 0; i < outLen; i++) {
    const start = 2 * i + 1; // Java-style offset (+1)
    let a = 0;
    let d = 0;
    for (let k = 0; k < FILTER_LEN; k++) {
      const v = ext[start + k];
      // dec_lo/hi reversed = convolution (not correlation)
      a += DEC_LO[FILTER_LEN - 1 - k] * v;
      d += DEC_HI[FILTER_LEN - 1 - k] * v;
    }
    cA[i] = a;
    cD[i] = d;
  }
  return [cA, cD];
}

// --- 1-D inverse DWT ---

/**
 * 1-D db4 IDWT.
 * 1. Upsample cA, cD (zeros at odd positions).
 * 2. Linear convolution with rec_lo/rec_hi; add results.
 * 3. Slice [FILTER_LEN-2 .. FILTER_LEN-2+N].
 */
function idwt1d(cA: number[], cD: number[], N: number): number[] {
  const M = cA.length;
  const convLen = 2 * M + FILTER_LEN - 1;
  const conv = new Array<number>(convLen).fill(0);

  for (let i = 0; i < M; i++) {
    const a = cA[i];
    const d = cD[i];
    const base = 2 * i; // position of sample in upsampled array
    for (let k = 0; k < FILTER_LEN; k++) {
      conv[base + k] += REC_LO[k] * a + REC_HI[k] * d;
    }
  }

  // Slice [TRIM_LEFT .. TRIM_LEFT+N]
  const out = new Array<number>(N);
  for (let i = 0; i < N; i++) {
    out[i] = conv[TRIM_LEFT + i];
  }
  return out;
}

// --- 2-D DWT ---

/**
 * Single-level 2-D db4 DWT.
 * Column pass first (axis 0), then row pass (axis 1) — matching pywt.
 */
function dwt2(x: number[][]): { cA: number[][]; cH: number[][]; cV: number[][]; cD: number[][] } {
  const H = x.length;
  const W = x[0].length;
  const Hc = db4OutLen(H);
  const Wc = db4OutLen(W);

  // Pass 1: column-wise DWT (along axis 0)
  const colLo: number[][] = Array.from({ length: Hc }, () => new Array<number>(W));
  const colHi: number[][] = Array.from({ length: Hc }, () => new Array<number>(W));
  for (let xi = 0; xi < W; xi++) {
    const col = new Array<number>(H);
    for (let yi = 0; yi < H; yi++) col[yi] = x[yi][xi];
    const [lo, hi] = dwt1d(col);
    for (let yi = 0; yi < Hc; yi++) {
      colLo[yi][xi] = lo[yi];
      colHi[yi][xi] = hi[yi];
    }
  }

  // Pass 2: row-wise DWT (along axis 1) on each colLo / colHi row
  const cA: number[][] = Array.from({ length: Hc }, () => new Array<number>(Wc));
  const cH: number[][] = Array.from({ length: Hc }, () => new Array<number>(Wc));
  const cV: number[][] = Array.from({ length: Hc }, () => new Array<number>(Wc));
  const cD: number[][] = Array.from({ length: Hc }, () => new Array<number>(Wc));

  for (let yi = 0; yi < Hc; yi++) {
    const [loLo, hiLo] = dwt1d(colLo[yi]);
    const [loHi, hiHi] = dwt1d(colHi[yi]);
    for (let xi = 0; xi < Wc; xi++) {
      cA[yi][xi] = loLo[xi];
      cV[yi][xi] = hiLo[xi]; // colLo → high → cV
      cH[yi][xi] = loHi[xi]; // colHi → low  → cH
      cD[yi][xi] = hiHi[xi];
    }
  }

  return { cA, cH, cV, cD };
}

/**
 * Single-level 2-D db4 IDWT.
 * Row pass first (undo axis 1), then column pass (undo axis 0).
 */
function idwt2(
  cA: number[][],
  cH: number[][],
  cV: number[][],
  cD: number[][],
  H: number,
  W: number,
): number[][] {
  const Hc = cA.length;
  const Wc = cA[0].length;

  // Undo row pass: recover colLo (cA+cV rows) and colHi (cH+cD rows) at length W
  const colLo: number[][] = Array.from({ length: Hc }, () => new Array<number>(W));
  const colHi: number[][] = Array.from({ length: Hc }, () => new Array<number>(W));
  for (let yi = 0; yi < Hc; yi++) {
    colLo[yi] = idwt1d(cA[yi], cV[yi], W);
    colHi[yi] = idwt1d(cH[yi], cD[yi], W);
  }

  // Undo column pass: recover x at height H for each column
  const out: number[][] = Array.from({ length: H }, () => new Array<number>(W));
  for (let xi = 0; xi < W; xi++) {
    const lo = new Array<number>(Hc);
    const hi = new Array<number>(Hc);
    for (let yi = 0; yi < Hc; yi++) {
      lo[yi] = colLo[yi][xi];
      hi[yi] = colHi[yi][xi];
    }
    const col = idwt1d(lo, hi, H);
    for (let yi = 0; yi < H; yi++) {
      out[yi][xi] = col[yi];
    }
  }
  return out;
}

// --- Multi-level decomposition / reconstruction ---

/**
 * Multi-level 2-D db4 DWT (equivalent to pywt.wavedec2(x, 'db4', mode='symmetric')).
 *
 * Returns Db4WavedecResult where:
 *   cA = the deepest LL band
 *   details[0] = outermost (shallowest) level bands [cH, cV, cD]  ← pywt order
 *   inputSizes[0] = (H, W) of the input to the shallowest decomp level  ← pywt order
 */
export function db4Wavedec2WithShape(
  x: number[][],
  level: number,
): Db4WavedecResult {
  let current: number[][] = x.map(row => row.slice());
  const details: number[][][][] = [];
  const inputSizes: [number, number][] = [];

  for (let l = 0; l < level; l++) {
    inputSizes.push([current.length, current[0].length]);
    const { cA, cH, cV, cD } = dwt2(current);
    details.push([cH, cV, cD]);
    current = cA;
  }

  // Reverse to match pywt order (outermost = shallowest level = index 0)
  details.reverse();
  inputSizes.reverse();

  return { cA: current, details, inputSizes };
}

/**
 * Multi-level 2-D db4 IDWT (equivalent to pywt.waverec2(coeffs, 'db4', mode='symmetric')).
 *
 * Reconstructs original x from a Db4WavedecResult. Processes from
 * outermost (deepest in pywt's reversed list = details[0]) inward.
 */
export function db4Waverec2(d: Db4WavedecResult): number[][] {
  let current = d.cA;
  for (let i = 0; i < d.details.length; i++) {
    const [cH, cV, cD] = d.details[i];
    const [H, W] = d.inputSizes[i];
    current = idwt2(current, cH, cV, cD, H, W);
  }
  return current;
}
