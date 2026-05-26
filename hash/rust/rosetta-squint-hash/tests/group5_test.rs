mod testkit;

use rosetta_squint_hash::{Hash, ImageHashError};

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

use image::{ImageBuffer, Rgb};

fn tiny_rgb_image() -> image::DynamicImage {
    image::DynamicImage::ImageRgb8(ImageBuffer::new(8, 8))
}

fn small_rgb_image() -> image::DynamicImage {
    let mut buf: ImageBuffer<Rgb<u8>, Vec<u8>> = ImageBuffer::new(32, 32);
    for y in 0..32 {
        for x in 0..32 {
            buf.put_pixel(x, y, Rgb([128, 64, 192]));
        }
    }
    image::DynamicImage::ImageRgb8(buf)
}

#[test]
fn average_hash_rejects_hash_size_below_two() {
    assert!(rosetta_squint_hash::average_hash(&tiny_rgb_image(), 1).is_err());
    assert!(rosetta_squint_hash::average_hash(&tiny_rgb_image(), 0).is_err());
}

#[test]
fn dhash_rejects_hash_size_below_two() {
    assert!(rosetta_squint_hash::dhash(&tiny_rgb_image(), 1).is_err());
}

#[test]
fn phash_rejects_hash_size_below_two() {
    assert!(rosetta_squint_hash::phash(&tiny_rgb_image(), 1).is_err());
}

#[test]
fn whash_rejects_hash_size_below_two() {
    assert!(rosetta_squint_hash::whash_haar(&small_rgb_image(), 1).is_err());
}

#[test]
fn whash_rejects_non_power_of_two() {
    assert!(matches!(
        rosetta_squint_hash::whash_haar(&small_rgb_image(), 3),
        Err(ImageHashError::NotPowerOfTwo(3))
    ));
    assert!(matches!(
        rosetta_squint_hash::whash_haar(&small_rgb_image(), 5),
        Err(ImageHashError::NotPowerOfTwo(5))
    ));
}

#[test]
fn colorhash_rejects_binbits_below_one() {
    assert!(rosetta_squint_hash::colorhash(&tiny_rgb_image(), 0).is_err());
}

#[test]
fn phash_simple_rejects_hash_size_below_two() {
    assert!(rosetta_squint_hash::phash_simple(&tiny_rgb_image(), 1).is_err());
    assert!(rosetta_squint_hash::phash_simple(&tiny_rgb_image(), 0).is_err());
}

#[test]
fn dhash_vertical_rejects_hash_size_below_two() {
    assert!(rosetta_squint_hash::dhash_vertical(&tiny_rgb_image(), 1).is_err());
    assert!(rosetta_squint_hash::dhash_vertical(&tiny_rgb_image(), 0).is_err());
}

#[test]
fn whash_db4_rejects_hash_size_below_two() {
    assert!(rosetta_squint_hash::whash_db4(&small_rgb_image(), 1).is_err());
}

#[test]
fn whash_db4_rejects_non_power_of_two() {
    assert!(matches!(
        rosetta_squint_hash::whash_db4(&small_rgb_image(), 3),
        Err(ImageHashError::NotPowerOfTwo(3))
    ));
}
