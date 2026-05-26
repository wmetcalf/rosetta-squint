//! Pillow-compatible Lanczos3 resize on uint8 grayscale (Vec<Vec<u8>> in [0,255]).
//!
//! Reproduces libImaging/Resample.c precompute_coeffs precisely:
//!   - center = (idx + 0.5) * scale
//!   - filter_scale = max(1.0, scale)
//!   - support = 3.0 * filter_scale
//!   - kernel = sinc(x) * sinc(x/3) for |x| < 3
//!   - xmin = (center - support + 0.5) as i32, clamped to [0, src_size)
//!   - xmax = (center + support + 0.5) as i32, clamped to (xmin, src_size] EXCLUSIVE upper bound
//!   - weights normalized per output pixel
//!   - PRECISION_BITS = 32 - 8 - 2 = 22 for fixed-point coefficients (NOT 32; 1<<32 overflows i32)
//!   - acc accumulated in i64

const PRECISION_BITS: u32 = 32 - 8 - 2; // = 22 — matches Pillow's #define PRECISION_BITS
const SUPPORT: f64 = 3.0;

/// Resize src[H][W] uint8 to a new [dst_h][dst_w] uint8 buffer via Lanczos3.
pub fn resize(src: &[Vec<u8>], dst_w: usize, dst_h: usize) -> Vec<Vec<u8>> {
    let src_h = src.len();
    let src_w = src[0].len();

    let (offs_h, lens_h, weights_h) = precompute_coeffs(src_w, dst_w);
    let mut mid: Vec<Vec<u8>> = Vec::with_capacity(src_h);
    for y in 0..src_h {
        let row = &src[y];
        let mut out = vec![0u8; dst_w];
        for xd in 0..dst_w {
            let mut acc: i64 = 0;
            let w = &weights_h[xd];
            let off = offs_h[xd];
            for i in 0..lens_h[xd] {
                acc += i64::from(w[i]) * i64::from(row[off + i]);
            }
            out[xd] = clip8(acc);
        }
        mid.push(out);
    }

    let (offs_v, lens_v, weights_v) = precompute_coeffs(src_h, dst_h);
    let mut result: Vec<Vec<u8>> = Vec::with_capacity(dst_h);
    for yd in 0..dst_h {
        let w = &weights_v[yd];
        let off = offs_v[yd];
        let mut out = vec![0u8; dst_w];
        for x in 0..dst_w {
            let mut acc: i64 = 0;
            for i in 0..lens_v[yd] {
                acc += i64::from(w[i]) * i64::from(mid[off + i][x]);
            }
            out[x] = clip8(acc);
        }
        result.push(out);
    }
    result
}

fn clip8(acc: i64) -> u8 {
    let rounded = (acc + (1_i64 << (PRECISION_BITS - 1))) >> PRECISION_BITS;
    rounded.clamp(0, 255) as u8
}

fn precompute_coeffs(src_size: usize, dst_size: usize) -> (Vec<usize>, Vec<usize>, Vec<Vec<i32>>) {
    let scale = src_size as f64 / dst_size as f64;
    let filter_scale = scale.max(1.0);
    let support = SUPPORT * filter_scale;

    let mut offsets = vec![0usize; dst_size];
    let mut lengths = vec![0usize; dst_size];
    let mut weights: Vec<Vec<i32>> = Vec::with_capacity(dst_size);

    for xd in 0..dst_size {
        let center = (xd as f64 + 0.5) * scale;
        let xmin_f = center - support + 0.5;
        let xmax_f = center + support + 0.5;
        let mut xmin = xmin_f as i32;
        if xmin < 0 {
            xmin = 0;
        }
        let mut xmax = xmax_f as i32;
        if xmax > src_size as i32 {
            xmax = src_size as i32;
        }
        let n = (xmax - xmin).max(0) as usize;
        let xmin = xmin as usize;

        let mut tmp = vec![0.0_f64; n];
        let mut wsum = 0.0;
        for i in 0..n {
            let dx = ((xmin + i) as f64 + 0.5 - center) / filter_scale;
            let w = lanczos_kernel(dx);
            tmp[i] = w;
            wsum += w;
        }
        if wsum != 0.0 {
            for w in tmp.iter_mut() {
                *w /= wsum;
            }
        }
        let mut q = vec![0i32; n];
        for i in 0..n {
            q[i] = (tmp[i] * ((1u32 << PRECISION_BITS) as f64)).round() as i32;
        }
        offsets[xd] = xmin;
        lengths[xd] = n;
        weights.push(q);
    }
    (offsets, lengths, weights)
}

fn lanczos_kernel(x: f64) -> f64 {
    if x == 0.0 {
        return 1.0;
    }
    let ax = x.abs();
    if ax >= SUPPORT {
        return 0.0;
    }
    let px = std::f64::consts::PI * x;
    (px.sin() / px) * ((px / SUPPORT).sin() / (px / SUPPORT))
}
