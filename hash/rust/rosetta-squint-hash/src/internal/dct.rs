//! Type-II DCT, no normalization. Matches scipy.fftpack.dct(x, type=2, norm=None).
//!
//! y[k] = 2 * Σ_{n=0..N-1} x[n] * cos(π * k * (2n+1) / (2N))
//!
//! For power-of-2 N, uses Makhoul's FFT trick to keep exact zeros where the
//! continuous DCT produces them. Direct O(N²) summation accumulates ~1e-11
//! noise that flips pHash median bits on uniform fixtures (verified Java/Go).

use std::f64::consts::PI;

pub fn dct1d(x: &[f64]) -> Vec<f64> {
    let n = x.len();
    if n == 0 {
        return Vec::new();
    }
    if n.is_power_of_two() {
        makhoul_dct(x)
    } else {
        direct_dct(x)
    }
}

pub fn dct2d(pixels: &[Vec<f64>]) -> Vec<Vec<f64>> {
    let h = pixels.len();
    let w = pixels[0].len();
    let mut mid: Vec<Vec<f64>> = vec![vec![0.0; w]; h];
    let mut col = vec![0.0_f64; h];
    for x in 0..w {
        for y in 0..h {
            col[y] = pixels[y][x];
        }
        let c = dct1d(&col);
        for y in 0..h {
            mid[y][x] = c[y];
        }
    }
    let mut out: Vec<Vec<f64>> = Vec::with_capacity(h);
    for y in 0..h {
        out.push(dct1d(&mid[y]));
    }
    out
}

fn makhoul_dct(x: &[f64]) -> Vec<f64> {
    let n = x.len();
    let mut re = vec![0.0_f64; n];
    let mut im = vec![0.0_f64; n];
    for i in 0..n / 2 {
        re[i] = x[2 * i];
        re[n - 1 - i] = x[2 * i + 1];
    }
    if n % 2 == 1 {
        re[n / 2] = x[n - 1];
    }
    fft(&mut re, &mut im);
    let mut out = vec![0.0_f64; n];
    for k in 0..n {
        let angle = -PI * k as f64 / (2.0 * n as f64);
        out[k] = 2.0 * (re[k] * angle.cos() - im[k] * angle.sin());
    }
    out
}

fn direct_dct(x: &[f64]) -> Vec<f64> {
    let n = x.len();
    let mut y = vec![0.0_f64; n];
    let factor = PI / (2.0 * n as f64);
    for k in 0..n {
        let mut sum = 0.0;
        for i in 0..n {
            sum += x[i] * (factor * k as f64 * (2 * i + 1) as f64).cos();
        }
        y[k] = 2.0 * sum;
    }
    y
}

fn fft(re: &mut [f64], im: &mut [f64]) {
    let n = re.len();
    let mut j = 0;
    for i in 1..n {
        let mut bit = n >> 1;
        while (j & bit) != 0 {
            j ^= bit;
            bit >>= 1;
        }
        j ^= bit;
        if i < j {
            re.swap(i, j);
            im.swap(i, j);
        }
    }
    let mut length = 2;
    while length <= n {
        let ang = -2.0 * PI / length as f64;
        let w_re = ang.cos();
        let w_im = ang.sin();
        let mut i = 0;
        while i < n {
            let mut cur_re = 1.0_f64;
            let mut cur_im = 0.0_f64;
            for k in 0..length / 2 {
                let u_re = re[i + k];
                let u_im = im[i + k];
                let v_re = re[i + k + length / 2] * cur_re - im[i + k + length / 2] * cur_im;
                let v_im = re[i + k + length / 2] * cur_im + im[i + k + length / 2] * cur_re;
                re[i + k] = u_re + v_re;
                im[i + k] = u_im + v_im;
                re[i + k + length / 2] = u_re - v_re;
                im[i + k + length / 2] = u_im - v_im;
                let n_re = cur_re * w_re - cur_im * w_im;
                cur_im = cur_re * w_im + cur_im * w_re;
                cur_re = n_re;
            }
            i += length;
        }
        length <<= 1;
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use serde::Deserialize;
    use std::collections::BTreeMap;
    use std::fs;

    const TOL: f64 = 1e-9;

    #[derive(Deserialize)]
    struct Doc {
        n: usize,
        cases: BTreeMap<String, Case>,
    }
    #[derive(Deserialize)]
    struct Case {
        input: Vec<f64>,
        output: Vec<f64>,
    }

    #[test]
    fn dct1d_matches_scipy() {
        let path = "../../spec/dct_cases.json";
        let data = fs::read_to_string(path).expect("read");
        let doc: Doc = serde_json::from_str(&data).expect("parse");
        for (name, c) in &doc.cases {
            let got = dct1d(&c.input);
            assert_eq!(got.len(), doc.n, "{name} length");
            for k in 0..doc.n {
                assert!(
                    (got[k] - c.output[k]).abs() < TOL,
                    "{name} k={k} got {} want {} diff {}",
                    got[k],
                    c.output[k],
                    got[k] - c.output[k]
                );
            }
        }
    }

    #[test]
    fn arange_first_output_is_992() {
        let x: Vec<f64> = (0..32).map(|i| i as f64).collect();
        let y = dct1d(&x);
        assert!((y[0] - 992.0).abs() < 1e-9, "y[0] = {}", y[0]);
    }
}
