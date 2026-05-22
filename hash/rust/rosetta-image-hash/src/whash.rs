//! whash with mode='haar' or mode='db4', remove_max_haar_ll=true, image_scale=None.

use image::DynamicImage;

use crate::average::rgb_to_gray;
use crate::internal::{db4, haar, img_rgb, lanczos};
use crate::{Hash, ImageHashError};

pub fn whash_haar(img: &DynamicImage, hash_size: usize) -> Result<Hash, ImageHashError> {
    if hash_size < 2 {
        return Err(ImageHashError::InvalidHashSize(hash_size));
    }
    if !hash_size.is_power_of_two() {
        return Err(ImageHashError::NotPowerOfTwo(hash_size));
    }

    let rgb = img_rgb::to_rgb(img);
    let gray = rgb_to_gray(&rgb);
    let h = gray.len();
    let w = gray[0].len();

    let min_side = w.min(h);
    let image_natural_scale = 1usize << (min_side as f64).log2().floor() as usize;
    let image_scale = image_natural_scale.max(hash_size);

    let ll_max_level = (image_scale as f64).log2() as usize;
    let level = (hash_size as f64).log2() as usize;
    if level > ll_max_level {
        return Err(ImageHashError::HashSizeTooLarge {
            level,
            ll_max_level,
        });
    }
    let dwt_level = ll_max_level - level;

    let resized = lanczos::resize(&gray, image_scale, image_scale);
    let mut pixels: Vec<Vec<f64>> = Vec::with_capacity(image_scale);
    for y in 0..image_scale {
        let mut row = Vec::with_capacity(image_scale);
        for x in 0..image_scale {
            row.push(f64::from(resized[y][x]) / 255.0);
        }
        pixels.push(row);
    }

    let mut full_dec = haar::wavedec2(&pixels, ll_max_level);
    for row in full_dec.ca.iter_mut() {
        for v in row.iter_mut() {
            *v = 0.0;
        }
    }
    let modified = haar::waverec2(&full_dec);

    let dec = haar::wavedec2(&modified, dwt_level);
    let ll = &dec.ca;

    let n = ll.len() * ll[0].len();
    let mut flat: Vec<f64> = Vec::with_capacity(n);
    for row in ll {
        flat.extend_from_slice(row);
    }
    let mut sorted = flat.clone();
    sorted.sort_by(|a, b| a.partial_cmp(b).unwrap());
    let median = if n % 2 == 1 {
        sorted[n / 2]
    } else {
        (sorted[n / 2 - 1] + sorted[n / 2]) / 2.0
    };

    let mut bits: Vec<Vec<bool>> = Vec::with_capacity(ll.len());
    for row in ll {
        let row_bits: Vec<bool> = row.iter().map(|v| *v > median).collect();
        bits.push(row_bits);
    }
    Ok(Hash::from_bits_unchecked(bits))
}

/// whash with mode='db4' (Daubechies-4, 8-tap wavelet), remove_max_haar_ll=True.
///
/// Same pipeline as `whash_haar` but the second decomposition uses pywt 'db4'.
/// The LL-zeroing step still uses Haar (matching imagehash's source).
/// The db4 forward DWT output length per level is `(n + 7) / 2` (not exactly n/2).
pub fn whash_db4(img: &DynamicImage, hash_size: usize) -> Result<Hash, ImageHashError> {
    if hash_size < 2 {
        return Err(ImageHashError::InvalidHashSize(hash_size));
    }
    if !hash_size.is_power_of_two() {
        return Err(ImageHashError::NotPowerOfTwo(hash_size));
    }

    let rgb = img_rgb::to_rgb(img);
    let gray = rgb_to_gray(&rgb);
    let h = gray.len();
    let w = gray[0].len();

    let min_side = w.min(h);
    let image_natural_scale = 1usize << (min_side as f64).log2().floor() as usize;
    let image_scale = image_natural_scale.max(hash_size);

    let ll_max_level = (image_scale as f64).log2() as usize;
    let level = (hash_size as f64).log2() as usize;
    if level > ll_max_level {
        return Err(ImageHashError::HashSizeTooLarge {
            level,
            ll_max_level,
        });
    }
    let dwt_level = ll_max_level - level;

    let resized = lanczos::resize(&gray, image_scale, image_scale);
    let mut pixels: Vec<Vec<f64>> = Vec::with_capacity(image_scale);
    for y in 0..image_scale {
        let mut row = Vec::with_capacity(image_scale);
        for x in 0..image_scale {
            row.push(f64::from(resized[y][x]) / 255.0);
        }
        pixels.push(row);
    }

    // Step 1: Haar full decomposition, zero LL, reconstruct (matches imagehash)
    let mut full_dec = haar::wavedec2(&pixels, ll_max_level);
    for row in full_dec.ca.iter_mut() {
        for v in row.iter_mut() {
            *v = 0.0;
        }
    }
    let modified = haar::waverec2(&full_dec);

    // Step 2: db4 decomposition to dwt_level
    let dec = db4::wavedec2(&modified, dwt_level);
    let ll = &dec.ca;

    let n = ll.len() * ll[0].len();
    let mut flat: Vec<f64> = Vec::with_capacity(n);
    for row in ll {
        flat.extend_from_slice(row);
    }
    let mut sorted = flat.clone();
    sorted.sort_by(|a, b| a.partial_cmp(b).unwrap());
    let median = if n % 2 == 1 {
        sorted[n / 2]
    } else {
        (sorted[n / 2 - 1] + sorted[n / 2]) / 2.0
    };

    let mut bits: Vec<Vec<bool>> = Vec::with_capacity(ll.len());
    for row in ll {
        let row_bits: Vec<bool> = row.iter().map(|v| *v > median).collect();
        bits.push(row_bits);
    }
    Ok(Hash::from_bits_unchecked(bits))
}
