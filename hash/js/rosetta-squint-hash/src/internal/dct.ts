/**
 * Type-II DCT, no normalization. Matches scipy.fftpack.dct(x, type=2, norm=None).
 *
 * y[k] = 2 * Σ_{n=0..N-1} x[n] * cos(π * k * (2n+1) / (2N))
 *
 * For power-of-2 N, uses Makhoul's FFT trick to keep exact zeros where the
 * continuous DCT produces them. Direct O(N²) summation accumulates ~1e-11
 * noise that flips pHash median bits on uniform fixtures (verified Java/Go/Rust).
 */

export function dct1d(x: Float64Array): Float64Array {
  const n = x.length;
  if (n === 0) return new Float64Array(0);
  if ((n & (n - 1)) === 0) return makhoulDct(x);
  return directDct(x);
}

/** 2-D DCT-II via column-wise then row-wise 1-D DCT. */
export function dct2d(x: Float64Array, n: number): Float64Array {
  // x is shape [n][n] flattened row-major
  const mid = new Float64Array(n * n);
  const col = new Float64Array(n);
  for (let xCol = 0; xCol < n; xCol++) {
    for (let y = 0; y < n; y++) col[y] = x[y * n + xCol];
    const c = dct1d(col);
    for (let y = 0; y < n; y++) mid[y * n + xCol] = c[y];
  }
  const out = new Float64Array(n * n);
  const row = new Float64Array(n);
  for (let y = 0; y < n; y++) {
    for (let xCol = 0; xCol < n; xCol++) row[xCol] = mid[y * n + xCol];
    const r = dct1d(row);
    for (let xCol = 0; xCol < n; xCol++) out[y * n + xCol] = r[xCol];
  }
  return out;
}

function makhoulDct(x: Float64Array): Float64Array {
  const n = x.length;
  const re = new Float64Array(n);
  const im = new Float64Array(n);
  for (let i = 0; i < n / 2; i++) {
    re[i] = x[2 * i];
    re[n - 1 - i] = x[2 * i + 1];
  }
  if (n % 2 === 1) re[(n / 2) | 0] = x[n - 1];
  fft(re, im);
  const out = new Float64Array(n);
  for (let k = 0; k < n; k++) {
    const angle = (-Math.PI * k) / (2.0 * n);
    out[k] = 2.0 * (re[k] * Math.cos(angle) - im[k] * Math.sin(angle));
  }
  return out;
}

function directDct(x: Float64Array): Float64Array {
  const n = x.length;
  const y = new Float64Array(n);
  const factor = Math.PI / (2.0 * n);
  for (let k = 0; k < n; k++) {
    let sum = 0;
    for (let i = 0; i < n; i++) {
      sum += x[i] * Math.cos(factor * k * (2 * i + 1));
    }
    y[k] = 2.0 * sum;
  }
  return y;
}

/** In-place iterative radix-2 Cooley-Tukey FFT. Requires len(re) == len(im) and power-of-2 length. */
function fft(re: Float64Array, im: Float64Array): void {
  const n = re.length;
  let j = 0;
  for (let i = 1; i < n; i++) {
    let bit = n >>> 1;
    while ((j & bit) !== 0) {
      j ^= bit;
      bit >>>= 1;
    }
    j ^= bit;
    if (i < j) {
      let t = re[i]; re[i] = re[j]; re[j] = t;
      t = im[i]; im[i] = im[j]; im[j] = t;
    }
  }
  for (let length = 2; length <= n; length <<= 1) {
    const ang = (-2.0 * Math.PI) / length;
    const wRe = Math.cos(ang);
    const wIm = Math.sin(ang);
    for (let i = 0; i < n; i += length) {
      let curRe = 1.0;
      let curIm = 0.0;
      for (let k = 0; k < length / 2; k++) {
        const uRe = re[i + k];
        const uIm = im[i + k];
        const vRe = re[i + k + length / 2] * curRe - im[i + k + length / 2] * curIm;
        const vIm = re[i + k + length / 2] * curIm + im[i + k + length / 2] * curRe;
        re[i + k] = uRe + vRe;
        im[i + k] = uIm + vIm;
        re[i + k + length / 2] = uRe - vRe;
        im[i + k + length / 2] = uIm - vIm;
        const nRe = curRe * wRe - curIm * wIm;
        curIm = curRe * wIm + curIm * wRe;
        curRe = nRe;
      }
    }
  }
}
