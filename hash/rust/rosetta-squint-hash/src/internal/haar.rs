//! 2-D Haar DWT/IDWT with pywt's 'symmetric' boundary mode.
//!
//! IMPORTANT: SQRT2_INV = (0.5_f64).sqrt() (NOT 1.0 / 2.0_f64.sqrt()).
//! The latter is one ULP lower and accumulates errors through 8-10 wavedec
//! levels, flipping bits at the whash median boundary.
//!
//! Column-pass before row-pass evaluation order matches pywt's float
//! addition order. Required for byte-exact parity.

/// sqrt(0.5) — one ULP higher than `std::f64::consts::FRAC_1_SQRT_2`.
/// **Do not replace with the std constant.** Java and Go ports both confirmed
/// `1.0 / 2.0_f64.sqrt()` accumulates a one-ULP error across 8-10 wavedec
/// levels, flipping bits at the whash median boundary.
#[allow(clippy::approx_constant)]
const SQRT2_INV: f64 = 0.7071067811865476;

pub struct WavedecResult {
    pub ca: Vec<Vec<f64>>,
    pub details: Vec<[Vec<Vec<f64>>; 3]>,
}

pub fn dwt2(x: &[Vec<f64>]) -> (Vec<Vec<f64>>, Vec<Vec<f64>>, Vec<Vec<f64>>, Vec<Vec<f64>>) {
    let h = x.len();
    let w = x[0].len();

    let mut col_low: Vec<Vec<f64>> = Vec::with_capacity(w);
    let mut col_high: Vec<Vec<f64>> = Vec::with_capacity(w);
    let mut col = vec![0.0_f64; h];
    for x_col in 0..w {
        for y in 0..h {
            col[y] = x[y][x_col];
        }
        let (low, high) = dwt1d(&col);
        col_low.push(low);
        col_high.push(high);
    }
    let out_h = (h + 1) / 2;
    let out_w = (w + 1) / 2;

    let mut c_a = vec![vec![0.0_f64; out_w]; out_h];
    let mut c_h = vec![vec![0.0_f64; out_w]; out_h];
    let mut c_v = vec![vec![0.0_f64; out_w]; out_h];
    let mut c_d = vec![vec![0.0_f64; out_w]; out_h];
    let mut row_low = vec![0.0_f64; w];
    let mut row_high = vec![0.0_f64; w];
    for y in 0..out_h {
        for x_col in 0..w {
            row_low[x_col] = col_low[x_col][y];
            row_high[x_col] = col_high[x_col][y];
        }
        let (low_l, high_l) = dwt1d(&row_low);
        let (low_h, high_h) = dwt1d(&row_high);
        for xd in 0..out_w {
            c_a[y][xd] = low_l[xd];
            c_v[y][xd] = high_l[xd];
            c_h[y][xd] = low_h[xd];
            c_d[y][xd] = high_h[xd];
        }
    }
    (c_a, c_h, c_v, c_d)
}

pub fn idwt2(
    c_a: &[Vec<f64>],
    c_h: &[Vec<f64>],
    c_v: &[Vec<f64>],
    c_d: &[Vec<f64>],
) -> Vec<Vec<f64>> {
    let sh = c_a.len();
    let sw = c_a[0].len();
    let out_h = sh * 2;
    let out_w = sw * 2;

    // First reverse the row-pass: for each y, combine cA[y] (low) + cV[y] (high)
    // to get col_low row, and cH[y] (low) + cD[y] (high) to get col_high row.
    let mut col_low_rows: Vec<Vec<f64>> = Vec::with_capacity(sh);
    let mut col_high_rows: Vec<Vec<f64>> = Vec::with_capacity(sh);
    for y in 0..sh {
        col_low_rows.push(idwt1d(&c_a[y], &c_v[y]));
        col_high_rows.push(idwt1d(&c_h[y], &c_d[y]));
    }

    // Now reverse the column-pass: for each output column, combine the
    // col_low and col_high values (vertically) to get original column data.
    let mut out: Vec<Vec<f64>> = vec![vec![0.0_f64; out_w]; out_h];
    let mut col_low = vec![0.0_f64; sh];
    let mut col_high = vec![0.0_f64; sh];
    for x_col in 0..out_w {
        for y in 0..sh {
            col_low[y] = col_low_rows[y][x_col];
            col_high[y] = col_high_rows[y][x_col];
        }
        let recovered = idwt1d(&col_low, &col_high);
        for yo in 0..out_h {
            out[yo][x_col] = recovered[yo];
        }
    }
    out
}

pub fn wavedec2(x: &[Vec<f64>], level: usize) -> WavedecResult {
    let mut current: Vec<Vec<f64>> = x.iter().cloned().collect();
    let mut details: Vec<[Vec<Vec<f64>>; 3]> = Vec::with_capacity(level);
    for _ in 0..level {
        let (c_a, c_h, c_v, c_d) = dwt2(&current);
        details.push([c_h, c_v, c_d]);
        current = c_a;
    }
    details.reverse();
    WavedecResult {
        ca: current,
        details,
    }
}

pub fn waverec2(d: &WavedecResult) -> Vec<Vec<f64>> {
    let mut current: Vec<Vec<f64>> = d.ca.clone();
    for level in &d.details {
        current = idwt2(&current, &level[0], &level[1], &level[2]);
    }
    current
}

fn dwt1d(x: &[f64]) -> (Vec<f64>, Vec<f64>) {
    let n0 = x.len();
    let (xx, n): (Vec<f64>, usize) = if n0 & 1 != 0 {
        let mut ext = Vec::with_capacity(n0 + 1);
        ext.extend_from_slice(x);
        ext.push(x[n0 - 1]);
        (ext, n0 + 1)
    } else {
        (x.to_vec(), n0)
    };
    let half = n / 2;
    let mut low = vec![0.0_f64; half];
    let mut high = vec![0.0_f64; half];
    let s = SQRT2_INV;
    for i in 0..half {
        let a = xx[2 * i];
        let b = xx[2 * i + 1];
        low[i] = s * a + s * b;
        high[i] = s * a - s * b;
    }
    (low, high)
}

fn idwt1d(low: &[f64], high: &[f64]) -> Vec<f64> {
    let half = low.len();
    let n = half * 2;
    let mut out = vec![0.0_f64; n];
    let s = SQRT2_INV;
    for i in 0..half {
        out[2 * i] = s * low[i] + s * high[i];
        out[2 * i + 1] = s * low[i] - s * high[i];
    }
    out
}

#[cfg(test)]
mod tests {
    use super::*;
    use serde::Deserialize;
    use std::fs;

    const TOL: f64 = 1e-12;

    #[derive(Deserialize)]
    struct Doc {
        input: Vec<Vec<f64>>,
        single_level: SingleLevel,
        multi_level_4: MultiLevel,
    }
    #[derive(Deserialize)]
    struct SingleLevel {
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
    struct MultiLevel {
        #[serde(rename = "cA")]
        c_a: Vec<Vec<f64>>,
        reconstructed: Vec<Vec<f64>>,
    }

    fn assert_close(expected: &[Vec<f64>], actual: &[Vec<f64>], label: &str) {
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

    fn load() -> Doc {
        let data = fs::read_to_string("../../spec/haar_cases.json").expect("read");
        serde_json::from_str(&data).expect("parse")
    }

    #[test]
    fn single_level_matches_pywt() {
        let doc = load();
        let (c_a, c_h, c_v, c_d) = dwt2(&doc.input);
        assert_close(&doc.single_level.c_a, &c_a, "cA");
        assert_close(&doc.single_level.c_h, &c_h, "cH");
        assert_close(&doc.single_level.c_v, &c_v, "cV");
        assert_close(&doc.single_level.c_d, &c_d, "cD");
    }

    #[test]
    fn multi_level_ll_and_reconstruction() {
        let doc = load();
        let dec = wavedec2(&doc.input, 4);
        assert_eq!(dec.ca.len(), 1, "deepest LL row count");
        assert_eq!(dec.ca[0].len(), 1, "deepest LL col count");
        assert_close(&doc.multi_level_4.c_a, &dec.ca, "multi cA");
        let recon = waverec2(&dec);
        assert_close(&doc.multi_level_4.reconstructed, &recon, "reconstructed");
        assert_close(&doc.input, &recon, "round-trip == input");
    }

    #[test]
    fn zero_ll_of_full_decomp_removes_dc() {
        let x: Vec<Vec<f64>> = (0..4).map(|_| vec![7.5; 4]).collect();
        let mut dec = wavedec2(&x, 2);
        dec.ca[0][0] = 0.0;
        let recon = waverec2(&dec);
        for y in 0..4 {
            for x_col in 0..4 {
                assert!(
                    recon[y][x_col].abs() < TOL,
                    "expected 0 at ({y},{x_col}), got {}",
                    recon[y][x_col]
                );
            }
        }
    }
}

