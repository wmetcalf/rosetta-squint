//! Hex string ↔ Hash conversion (square shape and 14×B colorhash shape).

use crate::internal::bitpack;
use crate::{Hash, ImageHashError};

pub fn hex_to_hash(hex: &str) -> Result<Hash, ImageHashError> {
    let bits = bitpack::unpack_square(hex)?;
    Ok(Hash::from_bits_unchecked(bits))
}

pub fn hex_to_flathash(hex: &str, hash_size: usize) -> Result<Hash, ImageHashError> {
    if hash_size < 1 {
        return Err(ImageHashError::InvalidBinbits(hash_size));
    }
    let bits = bitpack::unpack_flat(hex, hash_size)?;
    Ok(Hash::from_bits_unchecked(bits))
}
