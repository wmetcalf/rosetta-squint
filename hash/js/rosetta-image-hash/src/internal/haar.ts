/**
 * 2-D Haar DWT/IDWT with pywt's 'symmetric' boundary mode.
 *
 * IMPORTANT: SQRT2_INV = Math.sqrt(0.5) (NOT 1 / Math.sqrt(2)).
 * The latter is one ULP lower and accumulates errors through 8-10 wavedec
 * levels, flipping bits at the whash median boundary. Confirmed across
 * Java/Go/Rust ports.
 *
 * Column-pass before row-pass evaluation order matches pywt's float
 * addition order. Required for byte-exact parity.
 */

const SQRT2_INV = Math.sqrt(0.5); // 0.7071067811865476 — DO NOT use 1 / Math.sqrt(2)

export interface Dwt2Result {
  cA: number[][];
  cH: number[][];
  cV: number[][];
  cD: number[][];
}

export interface WavedecResult {
  cA: number[][];
  /** Each entry is [cH, cV, cD] for one level. Outer-to-inner = deepest-first. */
  details: number[][][][];
}

export function dwt2(x: number[][]): Dwt2Result {
  const h = x.length;
  const w = x[0].length;

  // Column pass first
  const colLow: number[][] = new Array(w);
  const colHigh: number[][] = new Array(w);
  const col = new Array<number>(h);
  for (let xCol = 0; xCol < w; xCol++) {
    for (let y = 0; y < h; y++) col[y] = x[y][xCol];
    const [low, high] = dwt1d(col);
    colLow[xCol] = low;
    colHigh[xCol] = high;
  }
  const outH = (h + 1) >> 1;
  const outW = (w + 1) >> 1;

  const cA: number[][] = [];
  const cH: number[][] = [];
  const cV: number[][] = [];
  const cD: number[][] = [];
  for (let y = 0; y < outH; y++) {
    cA.push(new Array<number>(outW).fill(0));
    cH.push(new Array<number>(outW).fill(0));
    cV.push(new Array<number>(outW).fill(0));
    cD.push(new Array<number>(outW).fill(0));
  }
  const rowLow = new Array<number>(w);
  const rowHigh = new Array<number>(w);
  for (let y = 0; y < outH; y++) {
    for (let xCol = 0; xCol < w; xCol++) {
      rowLow[xCol] = colLow[xCol][y];
      rowHigh[xCol] = colHigh[xCol][y];
    }
    const [lowL, highL] = dwt1d(rowLow);
    const [lowH, highH] = dwt1d(rowHigh);
    for (let xd = 0; xd < outW; xd++) {
      cA[y][xd] = lowL[xd];
      cV[y][xd] = highL[xd];
      cH[y][xd] = lowH[xd];
      cD[y][xd] = highH[xd];
    }
  }
  return { cA, cH, cV, cD };
}

export function idwt2(
  cA: number[][],
  cH: number[][],
  cV: number[][],
  cD: number[][],
): number[][] {
  const sh = cA.length;
  const sw = cA[0].length;
  const outW = sw * 2;

  // Row-pass first (inverse of col-pass-first in dwt2):
  //   cA and cV share the col-low-pass origin -> combine along rows
  //   cH and cD share the col-high-pass origin -> combine along rows
  const rowLow: number[][] = [];
  const rowHigh: number[][] = [];
  for (let y = 0; y < sh; y++) {
    rowLow.push(idwt1d(cA[y], cV[y]));
    rowHigh.push(idwt1d(cH[y], cD[y]));
  }

  // Column-pass second: for each column, combine rowLow col with rowHigh col
  const out: number[][] = [];
  for (let y2 = 0; y2 < sh * 2; y2++) {
    out.push(new Array<number>(outW).fill(0));
  }
  for (let xCol = 0; xCol < outW; xCol++) {
    const low = new Array<number>(sh);
    const high = new Array<number>(sh);
    for (let y = 0; y < sh; y++) {
      low[y] = rowLow[y][xCol];
      high[y] = rowHigh[y][xCol];
    }
    const col = idwt1d(low, high);
    for (let y2 = 0; y2 < sh * 2; y2++) {
      out[y2][xCol] = col[y2];
    }
  }
  return out;
}

export function wavedec2(x: number[][], level: number): WavedecResult {
  let current: number[][] = x.map(row => row.slice());
  const details: number[][][][] = [];
  for (let l = 0; l < level; l++) {
    const { cA, cH, cV, cD } = dwt2(current);
    details.push([cH, cV, cD]);
    current = cA;
  }
  details.reverse();
  return { cA: current, details };
}

export function waverec2(d: WavedecResult): number[][] {
  let current = d.cA;
  for (const level of d.details) {
    current = idwt2(current, level[0], level[1], level[2]);
  }
  return current;
}

function dwt1d(x: number[]): [number[], number[]] {
  let n = x.length;
  let xx = x;
  if ((n & 1) !== 0) {
    const ext = x.slice();
    ext.push(x[n - 1]);
    xx = ext;
    n = n + 1;
  }
  const half = n >> 1;
  const low = new Array<number>(half);
  const high = new Array<number>(half);
  for (let i = 0; i < half; i++) {
    const a = xx[2 * i];
    const b = xx[2 * i + 1];
    low[i] = SQRT2_INV * a + SQRT2_INV * b;
    high[i] = SQRT2_INV * a - SQRT2_INV * b;
  }
  return [low, high];
}

function idwt1d(low: number[], high: number[]): number[] {
  const half = low.length;
  const n = half * 2;
  const out = new Array<number>(n);
  for (let i = 0; i < half; i++) {
    out[2 * i] = SQRT2_INV * low[i] + SQRT2_INV * high[i];
    out[2 * i + 1] = SQRT2_INV * low[i] - SQRT2_INV * high[i];
  }
  return out;
}
