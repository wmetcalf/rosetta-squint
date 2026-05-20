//! Boolean array ↔ hex string conversion. Row-major MSB-first, zero-padded.

use crate::ImageHashError;

pub fn pack(bits: &[Vec<bool>]) -> String {
    let h = bits.len();
    if h == 0 || bits[0].is_empty() {
        return String::new();
    }
    let w = bits[0].len();
    let total = h * w;
    let width = (total + 3) / 4;
    let mut bytes = vec![0u8; (total + 7) / 8];
    let mut bi = 0;
    for row in bits {
        for &b in row {
            if b {
                bytes[bi / 8] |= 1 << (7 - (bi % 8));
            }
            bi += 1;
        }
    }
    let mut out = String::with_capacity(width);
    for i in 0..width {
        let bit_pos = i * 4;
        let byte_idx = bit_pos / 8;
        let nibble = if bit_pos % 8 == 0 {
            bytes[byte_idx] >> 4
        } else {
            bytes[byte_idx] & 0x0F
        };
        out.push(char::from_digit(nibble as u32, 16).expect("nibble in 0..16"));
    }
    out
}

pub fn unpack_square(hex: &str) -> Result<Vec<Vec<bool>>, ImageHashError> {
    let bits = hex_to_bits(hex)?;
    let total = bits.len();
    let mut n: usize = 0;
    while n * n < total {
        n += 1;
    }
    if n * n != total {
        return Err(ImageHashError::InvalidHex(format!(
            "hex length {} ({total} bits) is not a square shape",
            hex.len()
        )));
    }
    let mut out: Vec<Vec<bool>> = Vec::with_capacity(n);
    let mut idx = 0;
    for _ in 0..n {
        let mut row = Vec::with_capacity(n);
        for _ in 0..n {
            row.push(bits[idx]);
            idx += 1;
        }
        out.push(row);
    }
    Ok(out)
}

pub fn unpack_flat(hex: &str, second_axis: usize) -> Result<Vec<Vec<bool>>, ImageHashError> {
    let bits = hex_to_bits(hex)?;
    let total = 14 * second_axis;
    if bits.len() < total {
        return Err(ImageHashError::InvalidHex(format!(
            "hex too short for 14x{} shape: {} bits",
            second_axis,
            bits.len()
        )));
    }
    let mut idx = bits.len() - total;
    let mut out: Vec<Vec<bool>> = Vec::with_capacity(14);
    for _ in 0..14 {
        let mut row = Vec::with_capacity(second_axis);
        for _ in 0..second_axis {
            row.push(bits[idx]);
            idx += 1;
        }
        out.push(row);
    }
    Ok(out)
}

fn hex_to_bits(hex: &str) -> Result<Vec<bool>, ImageHashError> {
    if hex.is_empty() {
        return Err(ImageHashError::InvalidHex("empty hex".to_string()));
    }
    for c in hex.chars() {
        if !c.is_ascii_digit() && !('a'..='f').contains(&c) {
            return Err(ImageHashError::InvalidHex(format!("invalid char {c:?} in {hex:?}")));
        }
    }
    let total = hex.len() * 4;
    let mut bits = vec![false; total];
    for (i, c) in hex.chars().enumerate() {
        let v = c.to_digit(16).expect("validated above");
        for b in 0..4 {
            bits[i * 4 + b] = (v >> (3 - b)) & 1 == 1;
        }
    }
    Ok(bits)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn pack_4x4_pattern() {
        let bits: Vec<Vec<bool>> = vec![
            vec![true, false, true, false],
            vec![false, true, false, true],
            vec![true, true, true, true],
            vec![false, false, false, false],
        ];
        assert_eq!(pack(&bits), "a5f0");
    }

    #[test]
    fn pack_all_ones_8x8() {
        let bits: Vec<Vec<bool>> = (0..8).map(|_| vec![true; 8]).collect();
        assert_eq!(pack(&bits), "ffffffffffffffff");
    }

    #[test]
    fn pack_all_zeros_8x8() {
        let bits: Vec<Vec<bool>> = (0..8).map(|_| vec![false; 8]).collect();
        assert_eq!(pack(&bits), "0000000000000000");
    }

    #[test]
    fn unpack_square_is_inverse() {
        let expected: Vec<Vec<bool>> = vec![
            vec![true, false, true, false],
            vec![false, true, false, true],
            vec![true, true, true, true],
            vec![false, false, false, false],
        ];
        let got = unpack_square("a5f0").expect("unpack");
        assert_eq!(got.len(), 4);
        for y in 0..4 {
            assert_eq!(got[y], expected[y], "row {y}");
        }
    }

    #[test]
    fn unpack_flat_zeros() {
        let got = unpack_flat("00000000000", 3).expect("unpack");
        assert_eq!(got.len(), 14);
        assert_eq!(got[0].len(), 3);
        for y in 0..14 {
            for x in 0..3 {
                assert!(!got[y][x]);
            }
        }
    }

    #[test]
    fn unpack_square_non_square_errors() {
        assert!(unpack_square("12345").is_err());
    }

    #[test]
    fn unpack_invalid_chars_errors() {
        assert!(unpack_square("xyz!").is_err());
    }
}
