mod testkit;

use rosetta_image_hash::{Hash, ImageHashError};

fn must_hash(bits: Vec<Vec<bool>>) -> Hash {
    Hash::new(bits).expect("Hash::new")
}

#[test]
fn hamming_distance_is_zero_for_equal_hashes() {
    let a = must_hash(vec![vec![true, false], vec![true, true]]);
    let b = must_hash(vec![vec![true, false], vec![true, true]]);
    assert_eq!(a.subtract(&b).unwrap(), 0);
}

#[test]
fn hamming_distance_counts_differing_bits() {
    let a = must_hash(vec![vec![true, false], vec![true, true]]);
    let b = must_hash(vec![vec![false, false], vec![true, false]]);
    assert_eq!(a.subtract(&b).unwrap(), 2);
}

#[test]
fn bit_count_is_height_times_width() {
    let h = must_hash(vec![vec![false; 8]; 8]);
    assert_eq!(h.bit_count(), 64);
}

#[test]
fn equals_is_value_based() {
    let a = must_hash(vec![vec![true, false]]);
    let b = must_hash(vec![vec![true, false]]);
    let c = must_hash(vec![vec![false, false]]);
    assert_eq!(a, b);
    assert_ne!(a, c);
}

#[test]
fn to_hex_produces_expected_format() {
    let bits: Vec<Vec<bool>> = (0..8).map(|_| vec![true; 8]).collect();
    let h = must_hash(bits);
    assert_eq!(h.to_hex(), "ffffffffffffffff");
}

#[test]
fn subtract_requires_matching_shape() {
    let a = must_hash(vec![vec![true, false]]);
    let b = must_hash(vec![vec![true, false], vec![true, false]]);
    match a.subtract(&b) {
        Err(ImageHashError::ShapeMismatch { .. }) => (),
        other => panic!("expected ShapeMismatch, got {other:?}"),
    }
}

#[test]
fn new_hash_rejects_empty() {
    assert!(Hash::new(vec![]).is_err());
    assert!(Hash::new(vec![vec![]]).is_err());
}

#[test]
fn new_hash_rejects_non_rectangular() {
    assert!(Hash::new(vec![vec![true, false], vec![true]]).is_err());
}
