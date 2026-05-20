//! rosetta-image-hash — Byte-exact Rust port of Python imagehash 4.3.2.
//!
//! Public API: `Hash`, `ImageHashError`, `hex_to_hash`, `hex_to_flathash`, and
//! the five algorithm functions. All return `Result<Hash, ImageHashError>`.

#[doc(hidden)]
pub mod internal;

mod hex;
pub use hex::{hex_to_flathash, hex_to_hash};

mod average;
pub use average::average_hash;

use std::fmt;

#[derive(Debug, thiserror::Error)]
pub enum ImageHashError {
    #[error("hash_size must be >= 2, got {0}")]
    InvalidHashSize(usize),
    #[error("hash_size must be a power of 2 for whash, got {0}")]
    NotPowerOfTwo(usize),
    #[error("hash_size too large for image: level={level} > ll_max_level={ll_max_level}")]
    HashSizeTooLarge { level: usize, ll_max_level: usize },
    #[error("binbits must be >= 1, got {0}")]
    InvalidBinbits(usize),
    #[error("invalid hex: {0}")]
    InvalidHex(String),
    #[error("shapes don't match: this=({h1},{w1}), other=({h2},{w2})")]
    ShapeMismatch { h1: usize, w1: usize, h2: usize, w2: usize },
    #[error("bits must be non-empty")]
    EmptyBits,
    #[error("bits must be rectangular; got widths {expected} and {got}")]
    NonRectangular { expected: usize, got: usize },
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Hash {
    bits: Vec<Vec<bool>>,
}

impl Hash {
    pub fn new(bits: Vec<Vec<bool>>) -> Result<Self, ImageHashError> {
        if bits.is_empty() || bits[0].is_empty() {
            return Err(ImageHashError::EmptyBits);
        }
        let w = bits[0].len();
        for row in &bits {
            if row.len() != w {
                return Err(ImageHashError::NonRectangular { expected: w, got: row.len() });
            }
        }
        Ok(Self { bits })
    }

    pub(crate) fn from_bits_unchecked(bits: Vec<Vec<bool>>) -> Self {
        Self { bits }
    }

    pub fn to_hex(&self) -> String {
        internal::bitpack::pack(&self.bits)
    }

    pub fn subtract(&self, other: &Hash) -> Result<usize, ImageHashError> {
        let h1 = self.bits.len();
        let w1 = self.bits[0].len();
        let h2 = other.bits.len();
        let w2 = other.bits[0].len();
        if h1 != h2 || w1 != w2 {
            return Err(ImageHashError::ShapeMismatch { h1, w1, h2, w2 });
        }
        let mut diff = 0;
        for y in 0..h1 {
            for x in 0..w1 {
                if self.bits[y][x] != other.bits[y][x] {
                    diff += 1;
                }
            }
        }
        Ok(diff)
    }

    pub fn bit_count(&self) -> usize {
        if self.bits.is_empty() {
            return 0;
        }
        self.bits.len() * self.bits[0].len()
    }
}

impl fmt::Display for Hash {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(&self.to_hex())
    }
}
