//! dhash: grayscale → Lanczos resize to (W=N+1, H=N) → row-wise adjacent-column diff.

use image::DynamicImage;

use crate::average::rgb_to_gray;
use crate::internal::{img_rgb, lanczos};
use crate::{Hash, ImageHashError};

pub fn dhash(img: &DynamicImage, hash_size: usize) -> Result<Hash, ImageHashError> {
	if hash_size < 2 {
		return Err(ImageHashError::InvalidHashSize(hash_size));
	}
	let rgb = img_rgb::to_rgb(img);
	let gray = rgb_to_gray(&rgb);
	let resized = lanczos::resize(&gray, hash_size + 1, hash_size);

	let mut bits: Vec<Vec<bool>> = Vec::with_capacity(hash_size);
	for y in 0..hash_size {
		let mut row = Vec::with_capacity(hash_size);
		for x in 0..hash_size {
			row.push(resized[y][x + 1] > resized[y][x]);
		}
		bits.push(row);
	}
	Ok(Hash::from_bits_unchecked(bits))
}
