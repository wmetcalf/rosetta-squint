//! phash / phash_simple: grayscale → Lanczos resize to (N*F, N*F) → DCT → threshold.
//!
//! phash:        2-D DCT → top-left NxN block (cols 0..N) → threshold by median.
//! phash_simple: 1-D DCT per row → cols 1..N+1 (skipping DC column 0) → threshold by mean.
//!
//! The two algorithms differ in:
//!  1. phash uses 2-D DCT (column-then-row); phash_simple uses 1-D DCT per row only.
//!  2. phash takes columns 0..N; phash_simple takes columns 1..N+1.
//!  3. phash thresholds by median; phash_simple thresholds by mean.
//!
//! Both algorithms apply the snap-to-threshold tie-break:
//! `bit = (v > threshold + SNAP_EPS)`. See [`SNAP_EPS`] and `spec/SPEC.md`
//! §"Threshold tie-break".

use image::DynamicImage;

use crate::average::rgb_to_gray;
use crate::internal::{dct, img_rgb, lanczos};
use crate::{Hash, ImageHashError};

/// ε threshold for the snap-to-threshold tie-break used by `phash`,
/// `phash_simple`, `whash_db4`, and `whash_db4_robust`. Coefficients within
/// `SNAP_EPS` of the threshold map deterministically to bit 0 across all
/// ports. See `spec/SPEC.md` §"Threshold tie-break".
pub const SNAP_EPS: f64 = 1e-10;

pub fn phash(img: &DynamicImage, hash_size: usize) -> Result<Hash, ImageHashError> {
    phash_with_factor(img, hash_size, 4)
}

pub fn phash_with_factor(
    img: &DynamicImage,
    hash_size: usize,
    highfreq_factor: usize,
) -> Result<Hash, ImageHashError> {
    if hash_size < 2 {
        return Err(ImageHashError::InvalidHashSize(hash_size));
    }
    let img_size = hash_size * highfreq_factor;

    let rgb = img_rgb::to_rgb(img);
    let gray = rgb_to_gray(&rgb);
    let resized = lanczos::resize(&gray, img_size, img_size);

    let mut doubles: Vec<Vec<f64>> = Vec::with_capacity(img_size);
    for y in 0..img_size {
        let mut row = Vec::with_capacity(img_size);
        for x in 0..img_size {
            row.push(f64::from(resized[y][x]));
        }
        doubles.push(row);
    }
    let dct_out = dct::dct2d(&doubles);

    let mut block: Vec<f64> = Vec::with_capacity(hash_size * hash_size);
    for y in 0..hash_size {
        for x in 0..hash_size {
            block.push(dct_out[y][x]);
        }
    }
    let mut sorted = block.clone();
    sorted.sort_by(|a, b| a.total_cmp(b));
    let n = sorted.len();
    let median = if n % 2 == 1 {
        sorted[n / 2]
    } else {
        (sorted[n / 2 - 1] + sorted[n / 2]) / 2.0
    };

    // Snap-to-threshold tie-break: bit = v > (median + SNAP_EPS). Maps any
    // coefficient within SNAP_EPS of the median deterministically to 0.
    let threshold = median + SNAP_EPS;
    let mut bits: Vec<Vec<bool>> = Vec::with_capacity(hash_size);
    for y in 0..hash_size {
        let mut row = Vec::with_capacity(hash_size);
        for x in 0..hash_size {
            row.push(dct_out[y][x] > threshold);
        }
        bits.push(row);
    }
    Ok(Hash::from_bits_unchecked(bits))
}

pub fn phash_simple(img: &DynamicImage, hash_size: usize) -> Result<Hash, ImageHashError> {
    phash_simple_with_factor(img, hash_size, 4)
}

pub fn phash_simple_with_factor(
    img: &DynamicImage,
    hash_size: usize,
    highfreq_factor: usize,
) -> Result<Hash, ImageHashError> {
    if hash_size < 2 {
        return Err(ImageHashError::InvalidHashSize(hash_size));
    }
    let img_size = hash_size * highfreq_factor;

    let rgb = img_rgb::to_rgb(img);
    let gray = rgb_to_gray(&rgb);
    let resized = lanczos::resize(&gray, img_size, img_size);

    // Convert to f64 pixel rows
    let mut doubles: Vec<Vec<f64>> = Vec::with_capacity(img_size);
    for y in 0..img_size {
        let mut row = Vec::with_capacity(img_size);
        for x in 0..img_size {
            row.push(f64::from(resized[y][x]));
        }
        doubles.push(row);
    }

    // Apply 1-D DCT to each ROW only (scipy.fftpack.dct default behavior).
    // phash_simple does NOT apply column DCT — this differs from phash.
    let mut dct_rows: Vec<Vec<f64>> = Vec::with_capacity(hash_size);
    for y in 0..hash_size {
        dct_rows.push(dct::dct1d(&doubles[y]));
    }

    // Extract block: rows 0..hash_size, cols 1..hash_size+1 (SKIP column 0 = DC component).
    // imagehash: dctlowfreq = dct[:hash_size, 1:hash_size+1]
    let mut block: Vec<f64> = Vec::with_capacity(hash_size * hash_size);
    for y in 0..hash_size {
        for x in 1..=hash_size {
            block.push(dct_rows[y][x]);
        }
    }
    let n = block.len() as f64;
    let mean = block.iter().sum::<f64>() / n;

    // Snap-to-threshold tie-break: bit = v > (mean + SNAP_EPS).
    let threshold = mean + SNAP_EPS;
    let mut bits: Vec<Vec<bool>> = Vec::with_capacity(hash_size);
    for y in 0..hash_size {
        let mut row = Vec::with_capacity(hash_size);
        for x in 1..=hash_size {
            row.push(dct_rows[y][x] > threshold);
        }
        bits.push(row);
    }
    Ok(Hash::from_bits_unchecked(bits))
}
