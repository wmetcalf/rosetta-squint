//! 2-D Daubechies-4 (db4, 8-tap) DWT with pywt's 'symmetric' boundary mode.
//!
//! This implements the same wavelet as PyWavelets' `pywt.wavedec2(x, 'db4', mode='symmetric')`.
//! Note: pywt 'db4' is 4-vanishing-moment Daubechies with 8 filter taps.
//!
//! ## Accumulation order
//!
//! The forward DWT inner loop accumulates as:
//!   `sum_{k=0}^{7} dec_lo[k] * x_sym[2*n + L - k]`
//! where `x_sym` has L-1=7 elements of symmetric padding on each side, and L=8.
//! This order matches PyWavelets' C implementation to within floating-point noise.
//!
//! ## Output length
//!
//! For input length `n` and filter length `L=8`:
//!   `out_len = (n + L - 1) / 2 = (n + 7) / 2`
//!
//! ## Inverse DWT
//!
//! The IDWT upsamples (values at odd indices), convolves with `rec_lo` and `rec_hi`,
//! adds, and trims to the original length. Used for reconstruction in whash_db4.

/// pywt 'db4' analysis lowpass filter (dec_lo), 8 taps.
/// From `pywt.Wavelet('db4').dec_lo` — values are exactly representable as f64.
const DEC_LO: [f64; 8] = [
    -0.010597401785069032,
     0.0328830116668852,
     0.030841381835560764,
    -0.18703481171909309,
    -0.027983769416859854,
     0.6308807679298589,
     0.7148465705529157,
     0.2303778133088965,
];

/// pywt 'db4' analysis highpass filter (dec_hi), 8 taps.
/// From `pywt.Wavelet('db4').dec_hi`.
const DEC_HI: [f64; 8] = [
    -0.2303778133088965,
     0.7148465705529157,
    -0.6308807679298589,
    -0.027983769416859854,
     0.18703481171909309,
     0.030841381835560764,
    -0.0328830116668852,
    -0.010597401785069032,
];

/// pywt 'db4' synthesis lowpass filter (rec_lo), 8 taps.
/// From `pywt.Wavelet('db4').rec_lo`.
const REC_LO: [f64; 8] = [
     0.2303778133088965,
     0.7148465705529157,
     0.6308807679298589,
    -0.027983769416859854,
    -0.18703481171909309,
     0.030841381835560764,
     0.0328830116668852,
    -0.010597401785069032,
];

/// pywt 'db4' synthesis highpass filter (rec_hi), 8 taps.
/// From `pywt.Wavelet('db4').rec_hi`.
const REC_HI: [f64; 8] = [
    -0.010597401785069032,
    -0.0328830116668852,
     0.030841381835560764,
     0.18703481171909309,
    -0.027983769416859854,
    -0.6308807679298589,
     0.7148465705529157,
    -0.2303778133088965,
];

const FILTER_LEN: usize = 8;

/// Output length for one level of db4 DWT with symmetric mode.
/// Formula: `(n + filter_len - 1) / 2 = (n + 7) / 2`.
pub fn dwt_coeff_len(n: usize) -> usize {
    (n + FILTER_LEN - 1) / 2
}

/// Symmetric (whole-sample) extension: get the signal value at position `pos`
/// (which may be negative or beyond `n-1`) using the same rule as
/// `numpy.pad(x, pad, 'symmetric')`.
///
/// The signal is extended with period `2*n`, reflecting at both boundaries
/// WITH the boundary value repeated. For pos in [0, n): x[pos].
/// For pos negative or >= n: fold using `pos.rem_euclid(2n)`, then if < n: x[pos], else x[2n-1-pos].
#[inline]
fn sym_ext(x: &[f64], pos: isize) -> f64 {
    let n = x.len() as isize;
    let period = 2 * n;
    let p = pos.rem_euclid(period) as usize;
    if p < x.len() {
        x[p]
    } else {
        x[(2 * x.len()) - 1 - p]
    }
}

/// 1-D forward DWT with db4 and symmetric boundary mode.
///
/// Returns (lowpass, highpass) coefficient vectors.
///
/// Accumulation order matches pywt: for output n,
/// compute `sum_{k=0}^{7} dec_lo[k] * x_sym[2*n + L - k]`
/// where `x_sym` is the signal padded with L-1=7 elements on each side
/// using numpy's 'symmetric' (whole-sample symmetric) extension.
fn dwt1d(x: &[f64]) -> (Vec<f64>, Vec<f64>) {
    let n = x.len();
    let pad = FILTER_LEN - 1; // 7 elements on each side
    let sym_len = n + 2 * pad;

    // Build symmetrically-padded signal using whole-sample symmetric extension.
    // x_sym[i] corresponds to position (i - pad) in the original signal,
    // extended periodically with period 2n.
    let mut x_sym = Vec::with_capacity(sym_len);
    for i in 0..sym_len {
        let pos = i as isize - pad as isize;
        x_sym.push(sym_ext(x, pos));
    }

    let n_out = dwt_coeff_len(n);
    let mut lo = vec![0.0_f64; n_out];
    let mut hi = vec![0.0_f64; n_out];

    // For output n: sum_{k=0}^{L-1} filter[k] * x_sym[2*n + L - k]
    // = filter[0]*x_sym[2n+L] + filter[1]*x_sym[2n+L-1] + ... + filter[L-1]*x_sym[2n+1]
    // This matches pywt's C accumulation order for byte-exact parity.
    for out_n in 0..n_out {
        let mut lo_sum = 0.0_f64;
        let mut hi_sum = 0.0_f64;
        for k in 0..FILTER_LEN {
            let idx = 2 * out_n + FILTER_LEN - k;
            let val = x_sym[idx];
            lo_sum += DEC_LO[k] * val;
            hi_sum += DEC_HI[k] * val;
        }
        lo[out_n] = lo_sum;
        hi[out_n] = hi_sum;
    }

    (lo, hi)
}

/// 1-D inverse DWT with db4.
///
/// `lo` and `hi` are the lowpass and highpass coefficients.
/// `n_orig` is the length of the original signal before forward DWT.
///
/// Sparse upsample + accumulate:
/// For each position `n` in `lo`/`hi`, it contributes to output positions
/// `2*n+1+k` (for k=0..L-1) via `rec_lo[k]*lo[n]` and `rec_hi[k]*hi[n]`.
/// Trimmed to `[L-1 .. L-1+n_orig]`.
fn idwt1d(lo: &[f64], hi: &[f64], n_orig: usize) -> Vec<f64> {
    let n = lo.len();
    let conv_len = 2 * n + FILTER_LEN - 1;
    let mut out = vec![0.0_f64; conv_len];

    for i in 0..n {
        let lo_val = lo[i];
        let hi_val = hi[i];
        let base = 2 * i + 1;
        for k in 0..FILTER_LEN {
            out[base + k] += REC_LO[k] * lo_val + REC_HI[k] * hi_val;
        }
    }

    let start = FILTER_LEN - 1;
    out[start..start + n_orig].to_vec()
}

/// 2-D forward DWT (one level) with db4.
///
/// Returns (cA, cH, cV, cD) where each is a 2-D array.
/// Column-pass first, then row-pass (matching pywt's evaluation order).
pub fn dwt2(
    x: &[Vec<f64>],
) -> (Vec<Vec<f64>>, Vec<Vec<f64>>, Vec<Vec<f64>>, Vec<Vec<f64>>) {
    let h = x.len();
    let w = x[0].len();
    let col_h = dwt_coeff_len(h);
    let out_w = dwt_coeff_len(w);

    // Column pass: apply 1D DWT to each column
    let mut col_lo = vec![vec![0.0_f64; w]; col_h];
    let mut col_hi = vec![vec![0.0_f64; w]; col_h];
    for xc in 0..w {
        let col: Vec<f64> = (0..h).map(|yr| x[yr][xc]).collect();
        let (lo, hi) = dwt1d(&col);
        for yr in 0..col_h {
            col_lo[yr][xc] = lo[yr];
            col_hi[yr][xc] = hi[yr];
        }
    }

    // Row pass: apply 1D DWT to each row of col_lo and col_hi
    let mut c_a = vec![vec![0.0_f64; out_w]; col_h];
    let mut c_h = vec![vec![0.0_f64; out_w]; col_h];
    let mut c_v = vec![vec![0.0_f64; out_w]; col_h];
    let mut c_d = vec![vec![0.0_f64; out_w]; col_h];
    for yr in 0..col_h {
        let (lo_l, hi_l) = dwt1d(&col_lo[yr]);
        let (lo_h, hi_h) = dwt1d(&col_hi[yr]);
        for xc in 0..out_w {
            c_a[yr][xc] = lo_l[xc]; // LL
            c_v[yr][xc] = hi_l[xc]; // LH
            c_h[yr][xc] = lo_h[xc]; // HL
            c_d[yr][xc] = hi_h[xc]; // HH
        }
    }

    (c_a, c_h, c_v, c_d)
}

/// 2-D inverse DWT (one level) with db4.
pub fn idwt2(
    c_a: &[Vec<f64>],
    c_h: &[Vec<f64>],
    c_v: &[Vec<f64>],
    c_d: &[Vec<f64>],
    orig_h: usize,
    orig_w: usize,
) -> Vec<Vec<f64>> {
    let sh = c_a.len();

    // Inverse row pass first
    let mut col_lo_rows = Vec::with_capacity(sh);
    let mut col_hi_rows = Vec::with_capacity(sh);
    for yr in 0..sh {
        col_lo_rows.push(idwt1d(&c_a[yr], &c_v[yr], orig_w));
        col_hi_rows.push(idwt1d(&c_h[yr], &c_d[yr], orig_w));
    }

    // Inverse column pass
    let mut out = vec![vec![0.0_f64; orig_w]; orig_h];
    for xc in 0..orig_w {
        let col_lo: Vec<f64> = (0..sh).map(|yr| col_lo_rows[yr][xc]).collect();
        let col_hi: Vec<f64> = (0..sh).map(|yr| col_hi_rows[yr][xc]).collect();
        let recovered = idwt1d(&col_lo, &col_hi, orig_h);
        for yr in 0..orig_h {
            out[yr][xc] = recovered[yr];
        }
    }
    out
}

/// Result of a multi-level 2-D db4 DWT decomposition.
pub struct WavedecResult {
    pub ca: Vec<Vec<f64>>,
    /// Detail bands per level (highest level first), each is [cH, cV, cD].
    pub details: Vec<[Vec<Vec<f64>>; 3]>,
    /// Original input shape at each level (for reconstruction), highest level first.
    pub shapes: Vec<(usize, usize)>,
}

/// Multi-level 2-D forward DWT (wavedec2) with db4.
pub fn wavedec2(x: &[Vec<f64>], level: usize) -> WavedecResult {
    let mut current: Vec<Vec<f64>> = x.iter().cloned().collect();
    let mut details: Vec<[Vec<Vec<f64>>; 3]> = Vec::with_capacity(level);
    let mut shapes: Vec<(usize, usize)> = Vec::with_capacity(level);
    for _ in 0..level {
        let h = current.len();
        let w = current[0].len();
        shapes.push((h, w));
        let (c_a, c_h, c_v, c_d) = dwt2(&current);
        details.push([c_h, c_v, c_d]);
        current = c_a;
    }
    details.reverse();
    shapes.reverse();
    WavedecResult { ca: current, details, shapes }
}

/// Multi-level 2-D inverse DWT (waverec2) with db4.
pub fn waverec2(d: &WavedecResult) -> Vec<Vec<f64>> {
    let mut current: Vec<Vec<f64>> = d.ca.clone();
    for (level, shape) in d.details.iter().zip(d.shapes.iter()) {
        current = idwt2(&current, &level[0], &level[1], &level[2], shape.0, shape.1);
    }
    current
}

#[cfg(test)]
mod tests {
    use super::*;
    use serde::Deserialize;
    use std::fs;

    const TOL: f64 = 1e-10;

    #[derive(Deserialize)]
    struct Db4Cases {
        dwt2_cases: Vec<Dwt2Case>,
        wavedec2_cases: Vec<Wavedec2Case>,
        roundtrip_cases: Vec<RoundtripCase>,
    }

    #[derive(Deserialize)]
    struct Dwt2Case {
        input: Vec<Vec<f64>>,
        #[serde(rename = "cA")]
        c_a: Vec<Vec<f64>>,
        #[serde(rename = "cH")]
        c_h: Vec<Vec<f64>>,
        #[serde(rename = "cV")]
        c_v: Vec<Vec<f64>>,
        #[serde(rename = "cD")]
        c_d: Vec<Vec<f64>>,
    }

    #[derive(Deserialize)]
    struct Wavedec2Case {
        input: Vec<Vec<f64>>,
        level: usize,
        /// coeffs[0] is the LL subband (cA); further elements are detail tuples.
        /// We only check coeffs[0] (the LL/cA band).
        coeffs: serde_json::Value,
    }

    #[derive(Deserialize)]
    struct RoundtripCase {
        input: Vec<Vec<f64>>,
        level: usize,
        reconstructed: Vec<Vec<f64>>,
    }

    fn assert_close_2d(expected: &[Vec<f64>], actual: &[Vec<f64>], label: &str) {
        assert_eq!(expected.len(), actual.len(), "{label}: rows");
        for y in 0..expected.len() {
            assert_eq!(expected[y].len(), actual[y].len(), "{label}: cols at row {y}");
            for x in 0..expected[y].len() {
                let diff = (expected[y][x] - actual[y][x]).abs();
                assert!(
                    diff < TOL,
                    "{label} ({y},{x}): expected {} got {} diff {}",
                    expected[y][x],
                    actual[y][x],
                    diff
                );
            }
        }
    }

    fn load() -> Db4Cases {
        let data = fs::read_to_string("../../spec/db4_cases.json").expect("read db4_cases.json");
        serde_json::from_str(&data).expect("parse db4_cases.json")
    }

    fn parse_2d_array(v: &serde_json::Value) -> Vec<Vec<f64>> {
        v.as_array()
            .unwrap()
            .iter()
            .map(|row| {
                row.as_array()
                    .unwrap()
                    .iter()
                    .map(|x| x.as_f64().unwrap())
                    .collect()
            })
            .collect()
    }

    #[test]
    fn dwt2d_matches_pywt() {
        let cases = load();
        for (i, c) in cases.dwt2_cases.iter().enumerate() {
            let (ca_actual, ch_actual, cv_actual, cd_actual) = dwt2(&c.input);
            assert_close_2d(&c.c_a, &ca_actual, &format!("case{i} cA"));
            assert_close_2d(&c.c_h, &ch_actual, &format!("case{i} cH"));
            assert_close_2d(&c.c_v, &cv_actual, &format!("case{i} cV"));
            assert_close_2d(&c.c_d, &cd_actual, &format!("case{i} cD"));
        }
    }

    #[test]
    fn wavedec2_ca_matches_pywt() {
        let cases = load();
        for (i, c) in cases.wavedec2_cases.iter().enumerate() {
            let result = wavedec2(&c.input, c.level);
            // coeffs[0] has {"kind": "approx", "data": [[...]]}
            let ca_expected = parse_2d_array(&c.coeffs[0]["data"]);
            assert_close_2d(&ca_expected, &result.ca, &format!("case{i} (level={}) cA", c.level));
        }
    }

    #[test]
    fn round_trip_matches_pywt() {
        let cases = load();
        for (i, c) in cases.roundtrip_cases.iter().enumerate() {
            let dec = wavedec2(&c.input, c.level);
            let recon = waverec2(&dec);
            assert_close_2d(
                &c.reconstructed,
                &recon,
                &format!("case{i} (level={}) roundtrip", c.level),
            );
        }
    }

    #[test]
    fn round_trip_reconstruction() {
        // Additional round-trip tests with various sizes
        let sizes = [(4, 4), (8, 8), (16, 16)];
        for (h, w) in sizes {
            let x: Vec<Vec<f64>> = (0..h)
                .map(|y| (0..w).map(|xc| (y * w + xc) as f64 / (h * w) as f64).collect())
                .collect();
            let level = 1;
            let dec = wavedec2(&x, level);
            let recon = waverec2(&dec);
            assert_eq!(recon.len(), h, "rows {h}x{w}");
            assert_eq!(recon[0].len(), w, "cols {h}x{w}");
            for y in 0..h {
                for xc in 0..w {
                    let diff = (x[y][xc] - recon[y][xc]).abs();
                    assert!(
                        diff < 1e-12,
                        "({y},{xc}) expected {} got {} diff {}",
                        x[y][xc],
                        recon[y][xc],
                        diff
                    );
                }
            }
        }
    }
}
