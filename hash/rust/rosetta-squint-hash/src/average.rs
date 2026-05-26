//! ahash: convert to grayscale, Lanczos resize to NxN, threshold against mean.

use image::DynamicImage;

use crate::internal::{img_rgb, lanczos, pil_gray};
use crate::{Hash, ImageHashError};

pub fn average_hash(img: &DynamicImage, hash_size: usize) -> Result<Hash, ImageHashError> {
	if hash_size < 2 {
		return Err(ImageHashError::InvalidHashSize(hash_size));
	}
	let rgb = img_rgb::to_rgb(img);
	let gray = rgb_to_gray(&rgb);
	let resized = lanczos::resize(&gray, hash_size, hash_size);

	let mut sum: u64 = 0;
	for row in &resized {
		for &p in row {
			sum += u64::from(p);
		}
	}
	let avg = sum as f64 / (hash_size * hash_size) as f64;

	let mut bits: Vec<Vec<bool>> = Vec::with_capacity(hash_size);
	for y in 0..hash_size {
		let mut row = Vec::with_capacity(hash_size);
		for x in 0..hash_size {
			row.push(f64::from(resized[y][x]) > avg);
		}
		bits.push(row);
	}
	Ok(Hash::from_bits_unchecked(bits))
}

/// Helper shared by all luma-based algorithms (dhash, phash, whash).
pub(crate) fn rgb_to_gray(rgb: &[Vec<[u8; 3]>]) -> Vec<Vec<u8>> {
	let h = rgb.len();
	let w = rgb[0].len();
	let mut out: Vec<Vec<u8>> = Vec::with_capacity(h);
	for y in 0..h {
		let mut row = Vec::with_capacity(w);
		for x in 0..w {
			let p = rgb[y][x];
			row.push(pil_gray::to_gray(p[0], p[1], p[2]));
		}
		out.push(row);
	}
	out
}
