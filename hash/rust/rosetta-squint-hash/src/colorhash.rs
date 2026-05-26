//! colorhash: HSV-binned histogram hash.
//!
//! Bins: black (L<32), gray (L>=32 and S<85), 6 faint hue bins (85<=S<170),
//! 6 bright hue bins (S>170). S==170 increments colorful denominator but
//! lands in neither hue histogram.
//!
//! Quirky bin encoding (SPEC.md §8): v=8, B=4 → [true,true,false,false] = 0xc.

use image::DynamicImage;

use crate::internal::{img_rgb, pil_gray, pil_hsv};
use crate::{Hash, ImageHashError};

pub fn colorhash(img: &DynamicImage, binbits: usize) -> Result<Hash, ImageHashError> {
    if binbits < 1 {
        return Err(ImageHashError::InvalidBinbits(binbits));
    }
    // (1u64 << binbits) overflows for binbits >= 64. Cross-port practical limit is
    // 30 (JS bitwise int range), so cap there for parity. Real use is binbits 3-8.
    if binbits > 30 {
        return Err(ImageHashError::InvalidBinbits(binbits));
    }
    let rgb = img_rgb::to_rgb(img);
    let h = rgb.len();
    let w = rgb[0].len();
    let n: u64 = (w as u64) * (h as u64);

    let mut black_count: u64 = 0;
    let mut gray_count: u64 = 0;
    let mut colorful_count: u64 = 0;
    let mut faint_bins = [0u64; 6];
    let mut bright_bins = [0u64; 6];

    for y in 0..h {
        for x in 0..w {
            let r = rgb[y][x][0];
            let g = rgb[y][x][1];
            let b = rgb[y][x][2];
            let l = pil_gray::to_gray(r, g, b) as i32;
            if l < 32 {
                black_count += 1;
                continue;
            }
            let (hue, s, _) = pil_hsv::to_hsv(r, g, b);
            let s = s as i32;
            if s < 85 {
                gray_count += 1;
                continue;
            }
            colorful_count += 1;
            let hue_bin = ((hue as usize) * 6 / 255).min(5);
            if s < 170 {
                faint_bins[hue_bin] += 1;
            } else if s > 170 {
                bright_bins[hue_bin] += 1;
            }
        }
    }

    let max_val: u64 = 1u64 << binbits;
    let c = colorful_count.max(1);
    let clip = |v: u64| -> usize { v.min(max_val - 1) as usize };

    let mut values = vec![0usize; 14];
    values[0] = clip((black_count as f64 / n as f64 * max_val as f64) as u64);
    values[1] = clip((gray_count as f64 / n as f64 * max_val as f64) as u64);
    for i in 0..6 {
        values[2 + i] = clip((faint_bins[i] as f64 * max_val as f64 / c as f64) as u64);
        values[8 + i] = clip((bright_bins[i] as f64 * max_val as f64 / c as f64) as u64);
    }

    let mut bits: Vec<Vec<bool>> = Vec::with_capacity(14);
    for i in 0..14 {
        bits.push(colorhash_bin_encode(values[i], binbits));
    }
    Ok(Hash::from_bits_unchecked(bits))
}

/// SPEC.md §8 quirky bin encoding. Worked: v=4 → [0,1,1,0] (0x6), v=8 → [1,1,0,0] (0xc).
pub fn colorhash_bin_encode(v: usize, binbits: usize) -> Vec<bool> {
    let mut bits = vec![false; binbits];
    for i in 0..binbits {
        let shifted = v >> (binbits - i - 1);
        let masked = shifted & ((1 << (binbits - i)) - 1);
        bits[i] = masked > 0;
    }
    bits
}
