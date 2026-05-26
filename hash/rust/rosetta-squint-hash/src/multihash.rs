//! ImageMultiHash: variable-length list of Hash objects, output of crop_resistant_hash.
//!
//! Distance is FLOAT (not int) — matches Python `imagehash.ImageMultiHash.__sub__`.

use std::fmt;

use crate::{hex_to_hash, Hash, ImageHashError};

/// A collection of per-segment hashes produced by `crop_resistant_hash`.
///
/// Distance (`subtract`) returns a `f64` — not an integer — because it sums
/// best-match Hamming distances and applies a penalty for unmatched segments.
#[derive(Debug, Clone)]
pub struct ImageMultiHash {
    pub segment_hashes: Vec<Hash>,
}

impl ImageMultiHash {
    /// Returns the float distance between two `ImageMultiHash` objects.
    ///
    /// Matches Python `ImageMultiHash.__sub__`:
    /// ```python
    /// matches, sum_distance = self.hash_diff(other)
    /// max_difference = len(self.segment_hashes)
    /// if matches == 0: return max_difference
    /// max_distance = matches * len(self.segment_hashes[0])
    /// tie_breaker = -(sum_distance / max_distance)
    /// match_score = matches + tie_breaker
    /// return max_difference - match_score
    /// ```
    pub fn subtract(&self, other: &Self) -> f64 {
        self.subtract_with(other, None, None)
    }

    pub fn subtract_with(
        &self,
        other: &Self,
        hamming_cutoff: Option<f64>,
        bit_error_rate: Option<f64>,
    ) -> f64 {
        let (matches, sum_distance) = self.hash_diff(other, hamming_cutoff, bit_error_rate);
        let max_difference = self.segment_hashes.len() as f64;
        if matches == 0 {
            return max_difference;
        }
        let max_distance = matches as f64 * self.segment_hashes[0].bit_count() as f64;
        let tie_breaker = -(sum_distance as f64 / max_distance);
        let match_score = matches as f64 + tie_breaker;
        max_difference - match_score
    }

    /// Returns `(matches, sum_of_hamming_distances)` for segments within cutoff.
    ///
    /// Matches Python `ImageMultiHash.hash_diff`.
    pub fn hash_diff(
        &self,
        other: &Self,
        hamming_cutoff: Option<f64>,
        bit_error_rate: Option<f64>,
    ) -> (usize, usize) {
        if self.segment_hashes.is_empty() {
            return (0, 0);
        }
        let cutoff = match hamming_cutoff {
            Some(c) => c,
            None => {
                let ber = bit_error_rate.unwrap_or(0.25);
                self.segment_hashes[0].bit_count() as f64 * ber
            }
        };

        let mut total_matches = 0usize;
        let mut total_distance = 0usize;

        for sh in &self.segment_hashes {
            let lowest = other
                .segment_hashes
                .iter()
                .filter_map(|oh| sh.subtract(oh).ok())
                .min()
                .unwrap_or(usize::MAX);
            if (lowest as f64) <= cutoff {
                total_matches += 1;
                total_distance += lowest;
            }
        }
        (total_matches, total_distance)
    }

    /// True if at least `region_cutoff` segments match within the hamming cutoff.
    pub fn matches(
        &self,
        other: &Self,
        region_cutoff: usize,
        hamming_cutoff: Option<f64>,
        bit_error_rate: Option<f64>,
    ) -> bool {
        let (m, _) = self.hash_diff(other, hamming_cutoff, bit_error_rate);
        m >= region_cutoff
    }

    /// Returns the `ImageMultiHash` in `others` that minimizes `self.subtract(other)`.
    pub fn best_match<'a>(&self, others: &'a [Self]) -> Option<&'a Self> {
        others.iter().min_by(|a, b| {
            self.subtract(a)
                .partial_cmp(&self.subtract(b))
                .unwrap_or(std::cmp::Ordering::Equal)
        })
    }

    /// Comma-joined hex of each segment hash (matches Python `str(multihash)`).
    pub fn to_hex(&self) -> String {
        self.segment_hashes
            .iter()
            .map(|h| h.to_hex())
            .collect::<Vec<_>>()
            .join(",")
    }
}

impl fmt::Display for ImageMultiHash {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(&self.to_hex())
    }
}

/// Parse a comma-separated multi-hash hex string into an `ImageMultiHash`.
///
/// Matches Python `imagehash.hex_to_multihash`.
pub fn hex_to_multihash(s: &str) -> Result<ImageMultiHash, ImageHashError> {
    let hashes: Result<Vec<Hash>, ImageHashError> =
        s.split(',').map(|part| hex_to_hash(part)).collect();
    Ok(ImageMultiHash { segment_hashes: hashes? })
}
