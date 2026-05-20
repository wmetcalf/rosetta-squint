//! phash: grayscale → Lanczos resize to (N*F, N*F) → 2-D DCT → top-left NxN → median threshold.

use image::DynamicImage;

use crate::average::rgb_to_gray;
use crate::internal::{dct, img_rgb, lanczos};
use crate::{Hash, ImageHashError};

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
    sorted.sort_by(|a, b| a.partial_cmp(b).unwrap());
    let n = sorted.len();
    let median = if n % 2 == 1 {
        sorted[n / 2]
    } else {
        (sorted[n / 2 - 1] + sorted[n / 2]) / 2.0
    };

    let mut bits: Vec<Vec<bool>> = Vec::with_capacity(hash_size);
    for y in 0..hash_size {
        let mut row = Vec::with_capacity(hash_size);
        for x in 0..hash_size {
            row.push(dct_out[y][x] > median);
        }
        bits.push(row);
    }
    Ok(Hash::from_bits_unchecked(bits))
}
