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
use std::io::Read;
use std::path::Path;

use image::{DynamicImage, ImageBuffer, Rgb, Rgba};
use rosetta_squint_decode::{decode as rid_decode, Channels, DecodeError, DecodedImage};
use rosetta_squint_hash::ImageHashError;

pub use rosetta_squint_hash::{
    hex_to_flathash, hex_to_hash, hex_to_multihash, Hash, ImageMultiHash,
};

/// Reject path-based decode of files that are too large or are non-regular
/// (e.g., `/dev/zero`, named pipes, character devices) BEFORE reading bytes.
/// Callers that genuinely need to process images larger than this threshold
/// should decode via rosetta-squint-decode directly after explicit validation.
pub const MAX_FILE_SIZE: u64 = 256 * 1024 * 1024; // 256 MiB

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
    #[error("not a regular file: {path}")]
    NotRegularFile { path: String },
    #[error("symlink not allowed: {path}")]
    SymlinkNotAllowed { path: String },
    #[error(
        "input file too large: {size} bytes (max {max} bytes / 256 MiB). \
         For images above this threshold, decode via rosetta-squint-decode \
         directly after explicit validation."
    )]
    FileTooLarge { size: u64, max: u64 },
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

/// Decode raw image bytes via rosetta-squint-decode and return a `DynamicImage`.
pub fn decode_to_image(bytes: &[u8]) -> Result<DynamicImage, RosettaError> {
    let decoded = rid_decode(bytes)?;
    decoded_to_dynamic(decoded)
}

/// Open `path` such that the open fails if the final path component is a
/// symlink. On Unix this uses `O_NOFOLLOW`. On non-Unix targets there is
/// no equivalent open flag, so we pre-check via `symlink_metadata().is_symlink()`
/// before opening — there is a narrow TOCTOU window between the lstat and
/// the open on those platforms, but it is much narrower than the symlink
/// target itself being swapped.
fn open_no_follow(p: &Path) -> Result<fs::File, RosettaError> {
    #[cfg(unix)]
    {
        use std::os::unix::fs::OpenOptionsExt;
        fs::OpenOptions::new()
            .read(true)
            .custom_flags(libc::O_NOFOLLOW)
            .open(p)
            .map_err(|e| {
                // ELOOP is what O_NOFOLLOW raises when the final path
                // component is a symlink. Translate to a clearer variant
                // so callers can distinguish symlink rejection from a
                // generic I/O error.
                if e.raw_os_error() == Some(libc::ELOOP) {
                    RosettaError::SymlinkNotAllowed {
                        path: p.display().to_string(),
                    }
                } else {
                    RosettaError::Io(e)
                }
            })
    }
    #[cfg(not(unix))]
    {
        match fs::symlink_metadata(p) {
            Ok(meta) if meta.file_type().is_symlink() => {
                Err(RosettaError::SymlinkNotAllowed {
                    path: p.display().to_string(),
                })
            }
            Ok(_) => Ok(fs::File::open(p)?),
            Err(e) => Err(RosettaError::Io(e)),
        }
    }
}

/// Read a file from disk and decode it to a `DynamicImage`.
///
/// Refuses symlinks (via `O_NOFOLLOW` on Unix / `symlink_metadata` on
/// non-Unix), non-regular files (FIFOs, `/dev/zero`, character devices,
/// etc.) and files larger than [`MAX_FILE_SIZE`] BEFORE reading bytes —
/// without these guards `fs::read("/dev/zero")` would loop until OOM and
/// a 300 MiB sparse file would allocate 300 MiB even though it contains
/// no image. Callers who genuinely want symlink resolution must do it
/// explicitly (e.g. `std::fs::canonicalize`) before calling this function.
///
/// The file is opened once and both the regular-file and size checks run
/// against `File::metadata()` (which queries the open fd, not the path),
/// closing the obvious TOCTOU window between stat and read. The read uses
/// a `MAX_FILE_SIZE + 1` `take` limiter so a concurrent writer that grows
/// the file after the size check is still rejected rather than allowed to
/// silently exceed the cap.
pub fn decode_file<P: AsRef<Path>>(path: P) -> Result<DynamicImage, RosettaError> {
    let p = path.as_ref();
    let mut f = open_no_follow(p)?;
    // `File::metadata()` calls fstat() on the open fd, not stat() on the
    // path — this is what defeats the TOCTOU.
    let metadata = f.metadata()?;
    if !metadata.is_file() {
        return Err(RosettaError::NotRegularFile {
            path: p.display().to_string(),
        });
    }
    if metadata.len() > MAX_FILE_SIZE {
        return Err(RosettaError::FileTooLarge {
            size: metadata.len(),
            max: MAX_FILE_SIZE,
        });
    }
    // Pre-size by min(metadata, MAX) to avoid re-allocations on the common
    // path. `take(MAX+1)` ensures we detect post-stat growth: if more than
    // MAX_FILE_SIZE bytes are read, error out.
    let cap = std::cmp::min(metadata.len(), MAX_FILE_SIZE) as usize;
    let mut bytes: Vec<u8> = Vec::with_capacity(cap);
    f.by_ref()
        .take(MAX_FILE_SIZE + 1)
        .read_to_end(&mut bytes)?;
    if bytes.len() as u64 > MAX_FILE_SIZE {
        return Err(RosettaError::FileTooLarge {
            size: bytes.len() as u64,
            max: MAX_FILE_SIZE,
        });
    }
    decode_to_image(&bytes)
}

// ── phash ────────────────────────────────────────────────────────────────────

/// Compute phash from a file path.
pub fn phash<P: AsRef<Path>>(path: P, hash_size: usize) -> Result<Hash, RosettaError> {
    let img = decode_file(path)?;
    Ok(rosetta_squint_hash::phash(&img, hash_size)?)
}

/// Compute phash from raw bytes.
pub fn phash_bytes(bytes: &[u8], hash_size: usize) -> Result<Hash, RosettaError> {
    let img = decode_to_image(bytes)?;
    Ok(rosetta_squint_hash::phash(&img, hash_size)?)
}

// ── phash_simple ─────────────────────────────────────────────────────────────

/// Compute phash_simple from a file path.
pub fn phash_simple<P: AsRef<Path>>(path: P, hash_size: usize) -> Result<Hash, RosettaError> {
    let img = decode_file(path)?;
    Ok(rosetta_squint_hash::phash_simple(&img, hash_size)?)
}

/// Compute phash_simple from raw bytes.
pub fn phash_simple_bytes(bytes: &[u8], hash_size: usize) -> Result<Hash, RosettaError> {
    let img = decode_to_image(bytes)?;
    Ok(rosetta_squint_hash::phash_simple(&img, hash_size)?)
}

// ── dhash ────────────────────────────────────────────────────────────────────

/// Compute dhash from a file path.
pub fn dhash<P: AsRef<Path>>(path: P, hash_size: usize) -> Result<Hash, RosettaError> {
    let img = decode_file(path)?;
    Ok(rosetta_squint_hash::dhash(&img, hash_size)?)
}

/// Compute dhash from raw bytes.
pub fn dhash_bytes(bytes: &[u8], hash_size: usize) -> Result<Hash, RosettaError> {
    let img = decode_to_image(bytes)?;
    Ok(rosetta_squint_hash::dhash(&img, hash_size)?)
}

// ── dhash_vertical ───────────────────────────────────────────────────────────

/// Compute dhash_vertical from a file path.
pub fn dhash_vertical<P: AsRef<Path>>(path: P, hash_size: usize) -> Result<Hash, RosettaError> {
    let img = decode_file(path)?;
    Ok(rosetta_squint_hash::dhash_vertical(&img, hash_size)?)
}

/// Compute dhash_vertical from raw bytes.
pub fn dhash_vertical_bytes(bytes: &[u8], hash_size: usize) -> Result<Hash, RosettaError> {
    let img = decode_to_image(bytes)?;
    Ok(rosetta_squint_hash::dhash_vertical(&img, hash_size)?)
}

// ── average_hash ─────────────────────────────────────────────────────────────

/// Compute average_hash from a file path.
pub fn average_hash<P: AsRef<Path>>(path: P, hash_size: usize) -> Result<Hash, RosettaError> {
    let img = decode_file(path)?;
    Ok(rosetta_squint_hash::average_hash(&img, hash_size)?)
}

/// Compute average_hash from raw bytes.
pub fn average_hash_bytes(bytes: &[u8], hash_size: usize) -> Result<Hash, RosettaError> {
    let img = decode_to_image(bytes)?;
    Ok(rosetta_squint_hash::average_hash(&img, hash_size)?)
}

// ── whash_haar ───────────────────────────────────────────────────────────────

/// Compute whash_haar from a file path.
pub fn whash_haar<P: AsRef<Path>>(path: P, hash_size: usize) -> Result<Hash, RosettaError> {
    let img = decode_file(path)?;
    Ok(rosetta_squint_hash::whash_haar(&img, hash_size)?)
}

/// Compute whash_haar from raw bytes.
pub fn whash_haar_bytes(bytes: &[u8], hash_size: usize) -> Result<Hash, RosettaError> {
    let img = decode_to_image(bytes)?;
    Ok(rosetta_squint_hash::whash_haar(&img, hash_size)?)
}

// ── whash_db4 ────────────────────────────────────────────────────────────────

/// Compute whash_db4 from a file path.
pub fn whash_db4<P: AsRef<Path>>(path: P, hash_size: usize) -> Result<Hash, RosettaError> {
    let img = decode_file(path)?;
    Ok(rosetta_squint_hash::whash_db4(&img, hash_size)?)
}

/// Compute whash_db4 from raw bytes.
pub fn whash_db4_bytes(bytes: &[u8], hash_size: usize) -> Result<Hash, RosettaError> {
    let img = decode_to_image(bytes)?;
    Ok(rosetta_squint_hash::whash_db4(&img, hash_size)?)
}

// ── whash_db4_robust ─────────────────────────────────────────────────────────

/// Compute whash_db4_robust from a file path.
pub fn whash_db4_robust<P: AsRef<Path>>(path: P, hash_size: usize) -> Result<Hash, RosettaError> {
    let img = decode_file(path)?;
    Ok(rosetta_squint_hash::whash_db4_robust(&img, hash_size)?)
}

/// Compute whash_db4_robust from raw bytes.
pub fn whash_db4_robust_bytes(bytes: &[u8], hash_size: usize) -> Result<Hash, RosettaError> {
    let img = decode_to_image(bytes)?;
    Ok(rosetta_squint_hash::whash_db4_robust(&img, hash_size)?)
}

// ── colorhash ────────────────────────────────────────────────────────────────

/// Compute colorhash from a file path.
///
/// `binbits` controls the number of bits per histogram bin (default is 3 in Python).
pub fn colorhash<P: AsRef<Path>>(path: P, binbits: usize) -> Result<Hash, RosettaError> {
    let img = decode_file(path)?;
    Ok(rosetta_squint_hash::colorhash(&img, binbits)?)
}

/// Compute colorhash from raw bytes.
pub fn colorhash_bytes(bytes: &[u8], binbits: usize) -> Result<Hash, RosettaError> {
    let img = decode_to_image(bytes)?;
    Ok(rosetta_squint_hash::colorhash(&img, binbits)?)
}

// ── crop_resistant_hash ───────────────────────────────────────────────────────

/// Compute crop_resistant_hash from a file path.
///
/// `limit_segments`: optional cap on the number of segments to keep
/// (largest first). Pass `None` to keep all segments — matches Python's
/// default `limit_segments=None`.
pub fn crop_resistant_hash<P: AsRef<Path>>(
    path: P,
    limit_segments: Option<usize>,
) -> Result<ImageMultiHash, RosettaError> {
    let img = decode_file(path)?;
    Ok(rosetta_squint_hash::crop_resistant_hash(&img, limit_segments)?)
}

/// Compute crop_resistant_hash from raw bytes.
///
/// See [`crop_resistant_hash`] for the `limit_segments` semantics.
pub fn crop_resistant_hash_bytes(
    bytes: &[u8],
    limit_segments: Option<usize>,
) -> Result<ImageMultiHash, RosettaError> {
    let img = decode_to_image(bytes)?;
    Ok(rosetta_squint_hash::crop_resistant_hash(&img, limit_segments)?)
}
