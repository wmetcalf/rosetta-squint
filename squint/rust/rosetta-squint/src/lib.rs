//! rosetta-squint — point at an image (path or bytes), get the same hash
//! hex string as every other rosetta-squint port for the same input.
//!
//! # Examples
//!
//! ```no_run
//! use rosetta_squint::{phash, phash_bytes};
//!
//! let h = phash("photo.jpg", 8).unwrap();
//! println!("{}", h);
//! ```

use std::fs;
use std::path::Path;

use image::{DynamicImage, ImageBuffer, Rgb, Rgba};
use rosetta_image_decode::{decode as rid_decode, Channels, DecodeError, DecodedImage};
use rosetta_image_hash::ImageHashError;

pub use rosetta_image_hash::{
    hex_to_flathash, hex_to_hash, hex_to_multihash, Hash, ImageMultiHash,
};

// ── Error type ──────────────────────────────────────────────────────────────

#[derive(Debug, thiserror::Error)]
pub enum RosettaError {
    #[error("io: {0}")]
    Io(#[from] std::io::Error),
    #[error("decode: {0}")]
    Decode(#[from] DecodeError),
    #[error("hash: {0}")]
    Hash(#[from] ImageHashError),
    #[error("internal: failed to construct image from decoded buffer")]
    BadBuffer,
}

// ── DecodedImage → DynamicImage ─────────────────────────────────────────────

fn decoded_to_dynamic(d: DecodedImage) -> Result<DynamicImage, RosettaError> {
    let w = d.width as u32;
    let h = d.height as u32;
    match d.channels {
        Channels::Rgb => {
            let buf: ImageBuffer<Rgb<u8>, Vec<u8>> =
                ImageBuffer::from_raw(w, h, d.data).ok_or(RosettaError::BadBuffer)?;
            Ok(DynamicImage::ImageRgb8(buf))
        }
        Channels::Rgba => {
            let buf: ImageBuffer<Rgba<u8>, Vec<u8>> =
                ImageBuffer::from_raw(w, h, d.data).ok_or(RosettaError::BadBuffer)?;
            Ok(DynamicImage::ImageRgba8(buf))
        }
    }
}

// ── Public decode helpers ────────────────────────────────────────────────────

/// Decode raw image bytes via rosetta-image-decode and return a `DynamicImage`.
pub fn decode_to_image(bytes: &[u8]) -> Result<DynamicImage, RosettaError> {
    let decoded = rid_decode(bytes)?;
    decoded_to_dynamic(decoded)
}

/// Read a file from disk and decode it to a `DynamicImage`.
pub fn decode_file<P: AsRef<Path>>(path: P) -> Result<DynamicImage, RosettaError> {
    let bytes = fs::read(path)?;
    decode_to_image(&bytes)
}

// ── phash ────────────────────────────────────────────────────────────────────

/// Compute phash from a file path.
pub fn phash<P: AsRef<Path>>(path: P, hash_size: usize) -> Result<Hash, RosettaError> {
    let img = decode_file(path)?;
    Ok(rosetta_image_hash::phash(&img, hash_size)?)
}

/// Compute phash from raw bytes.
pub fn phash_bytes(bytes: &[u8], hash_size: usize) -> Result<Hash, RosettaError> {
    let img = decode_to_image(bytes)?;
    Ok(rosetta_image_hash::phash(&img, hash_size)?)
}

// ── phash_simple ─────────────────────────────────────────────────────────────

/// Compute phash_simple from a file path.
pub fn phash_simple<P: AsRef<Path>>(path: P, hash_size: usize) -> Result<Hash, RosettaError> {
    let img = decode_file(path)?;
    Ok(rosetta_image_hash::phash_simple(&img, hash_size)?)
}

/// Compute phash_simple from raw bytes.
pub fn phash_simple_bytes(bytes: &[u8], hash_size: usize) -> Result<Hash, RosettaError> {
    let img = decode_to_image(bytes)?;
    Ok(rosetta_image_hash::phash_simple(&img, hash_size)?)
}

// ── dhash ────────────────────────────────────────────────────────────────────

/// Compute dhash from a file path.
pub fn dhash<P: AsRef<Path>>(path: P, hash_size: usize) -> Result<Hash, RosettaError> {
    let img = decode_file(path)?;
    Ok(rosetta_image_hash::dhash(&img, hash_size)?)
}

/// Compute dhash from raw bytes.
pub fn dhash_bytes(bytes: &[u8], hash_size: usize) -> Result<Hash, RosettaError> {
    let img = decode_to_image(bytes)?;
    Ok(rosetta_image_hash::dhash(&img, hash_size)?)
}

// ── dhash_vertical ───────────────────────────────────────────────────────────

/// Compute dhash_vertical from a file path.
pub fn dhash_vertical<P: AsRef<Path>>(path: P, hash_size: usize) -> Result<Hash, RosettaError> {
    let img = decode_file(path)?;
    Ok(rosetta_image_hash::dhash_vertical(&img, hash_size)?)
}

/// Compute dhash_vertical from raw bytes.
pub fn dhash_vertical_bytes(bytes: &[u8], hash_size: usize) -> Result<Hash, RosettaError> {
    let img = decode_to_image(bytes)?;
    Ok(rosetta_image_hash::dhash_vertical(&img, hash_size)?)
}

// ── average_hash ─────────────────────────────────────────────────────────────

/// Compute average_hash from a file path.
pub fn average_hash<P: AsRef<Path>>(path: P, hash_size: usize) -> Result<Hash, RosettaError> {
    let img = decode_file(path)?;
    Ok(rosetta_image_hash::average_hash(&img, hash_size)?)
}

/// Compute average_hash from raw bytes.
pub fn average_hash_bytes(bytes: &[u8], hash_size: usize) -> Result<Hash, RosettaError> {
    let img = decode_to_image(bytes)?;
    Ok(rosetta_image_hash::average_hash(&img, hash_size)?)
}

// ── whash_haar ───────────────────────────────────────────────────────────────

/// Compute whash_haar from a file path.
pub fn whash_haar<P: AsRef<Path>>(path: P, hash_size: usize) -> Result<Hash, RosettaError> {
    let img = decode_file(path)?;
    Ok(rosetta_image_hash::whash_haar(&img, hash_size)?)
}

/// Compute whash_haar from raw bytes.
pub fn whash_haar_bytes(bytes: &[u8], hash_size: usize) -> Result<Hash, RosettaError> {
    let img = decode_to_image(bytes)?;
    Ok(rosetta_image_hash::whash_haar(&img, hash_size)?)
}

// ── whash_db4 ────────────────────────────────────────────────────────────────

/// Compute whash_db4 from a file path.
pub fn whash_db4<P: AsRef<Path>>(path: P, hash_size: usize) -> Result<Hash, RosettaError> {
    let img = decode_file(path)?;
    Ok(rosetta_image_hash::whash_db4(&img, hash_size)?)
}

/// Compute whash_db4 from raw bytes.
pub fn whash_db4_bytes(bytes: &[u8], hash_size: usize) -> Result<Hash, RosettaError> {
    let img = decode_to_image(bytes)?;
    Ok(rosetta_image_hash::whash_db4(&img, hash_size)?)
}

// ── whash_db4_robust ─────────────────────────────────────────────────────────

/// Compute whash_db4_robust from a file path.
pub fn whash_db4_robust<P: AsRef<Path>>(path: P, hash_size: usize) -> Result<Hash, RosettaError> {
    let img = decode_file(path)?;
    Ok(rosetta_image_hash::whash_db4_robust(&img, hash_size)?)
}

/// Compute whash_db4_robust from raw bytes.
pub fn whash_db4_robust_bytes(bytes: &[u8], hash_size: usize) -> Result<Hash, RosettaError> {
    let img = decode_to_image(bytes)?;
    Ok(rosetta_image_hash::whash_db4_robust(&img, hash_size)?)
}

// ── colorhash ────────────────────────────────────────────────────────────────

/// Compute colorhash from a file path.
///
/// `binbits` controls the number of bits per histogram bin (default is 3 in Python).
pub fn colorhash<P: AsRef<Path>>(path: P, binbits: usize) -> Result<Hash, RosettaError> {
    let img = decode_file(path)?;
    Ok(rosetta_image_hash::colorhash(&img, binbits)?)
}

/// Compute colorhash from raw bytes.
pub fn colorhash_bytes(bytes: &[u8], binbits: usize) -> Result<Hash, RosettaError> {
    let img = decode_to_image(bytes)?;
    Ok(rosetta_image_hash::colorhash(&img, binbits)?)
}

// ── crop_resistant_hash ───────────────────────────────────────────────────────

/// Compute crop_resistant_hash from a file path.
pub fn crop_resistant_hash<P: AsRef<Path>>(path: P) -> Result<ImageMultiHash, RosettaError> {
    let img = decode_file(path)?;
    Ok(rosetta_image_hash::crop_resistant_hash(&img)?)
}

/// Compute crop_resistant_hash from raw bytes.
pub fn crop_resistant_hash_bytes(bytes: &[u8]) -> Result<ImageMultiHash, RosettaError> {
    let img = decode_to_image(bytes)?;
    Ok(rosetta_image_hash::crop_resistant_hash(&img)?)
}
