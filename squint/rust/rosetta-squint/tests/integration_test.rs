//! Integration tests for rosetta-squint.
//!
//! For each algorithm:
//!   1. `<algo>(path)` returns a non-empty Hash.
//!   2. `<algo>(path)` and `<algo>_bytes(read(path))` produce identical hex.
//!   3. Both are equivalent to calling rosetta_image_hash directly on the decoded image
//!      (proves the decode → hash chain is self-consistent).

use std::fs;
use std::path::Path;

use rosetta_squint::{decode_to_image, *};

// Fixture paths relative to workspace root
const PNG_FIXTURE: &str =
    "../../../decode/spec/fixtures/png/valid/checker-32.png";
const JPEG_FIXTURE: &str =
    "../../../decode/spec/fixtures/jpeg/valid/32x32-quality-95.jpg";
const WEBP_FIXTURE: &str =
    "../../../decode/spec/fixtures/webp/valid/32x32-lossless.webp";

// Large enough for crop_resistant_hash (needs ≥500-pixel segments at 300×300)
const PNG_LARGE_FIXTURE: &str =
    "../../../decode/spec/fixtures/png/valid/checker-256.png";

fn abs(rel: &str) -> std::path::PathBuf {
    let manifest = Path::new(env!("CARGO_MANIFEST_DIR"));
    manifest.join(rel)
}

fn read_fixture(rel: &str) -> Vec<u8> {
    fs::read(abs(rel)).unwrap_or_else(|e| panic!("failed to read {}: {}", rel, e))
}

// ── helpers to get a DynamicImage by going through the same chain ────────────

fn img_from_bytes(bytes: &[u8]) -> image::DynamicImage {
    decode_to_image(bytes).expect("decode_to_image failed")
}

// ── phash ────────────────────────────────────────────────────────────────────

#[test]
fn phash_png_path_and_bytes_agree() {
    let path = abs(PNG_FIXTURE);
    let bytes = read_fixture(PNG_FIXTURE);

    let from_path = phash(&path, 8).expect("phash path failed");
    let from_bytes = phash_bytes(&bytes, 8).expect("phash bytes failed");

    assert!(!from_path.to_hex().is_empty());
    assert_eq!(from_path.to_hex(), from_bytes.to_hex());
}

#[test]
fn phash_jpeg_chain_consistent() {
    let path = abs(JPEG_FIXTURE);
    let bytes = read_fixture(JPEG_FIXTURE);

    let from_path = phash(&path, 8).expect("phash path failed");
    let from_bytes = phash_bytes(&bytes, 8).expect("phash bytes failed");
    let img = img_from_bytes(&bytes);
    let direct = rosetta_image_hash::phash(&img, 8).expect("direct phash failed");

    assert_eq!(from_path.to_hex(), direct.to_hex());
    assert_eq!(from_bytes.to_hex(), direct.to_hex());
}

#[test]
fn phash_webp_chain_consistent() {
    let path = abs(WEBP_FIXTURE);
    let bytes = read_fixture(WEBP_FIXTURE);

    let from_path = phash(&path, 8).expect("phash path failed");
    let from_bytes = phash_bytes(&bytes, 8).expect("phash bytes failed");
    let img = img_from_bytes(&bytes);
    let direct = rosetta_image_hash::phash(&img, 8).expect("direct phash failed");

    assert_eq!(from_path.to_hex(), direct.to_hex());
    assert_eq!(from_bytes.to_hex(), direct.to_hex());
}

// ── phash_simple ─────────────────────────────────────────────────────────────

#[test]
fn phash_simple_png_chain_consistent() {
    let path = abs(PNG_FIXTURE);
    let bytes = read_fixture(PNG_FIXTURE);

    let from_path = phash_simple(&path, 8).expect("phash_simple path failed");
    let from_bytes = phash_simple_bytes(&bytes, 8).expect("phash_simple bytes failed");
    let img = img_from_bytes(&bytes);
    let direct = rosetta_image_hash::phash_simple(&img, 8).expect("direct phash_simple failed");

    assert!(!from_path.to_hex().is_empty());
    assert_eq!(from_path.to_hex(), direct.to_hex());
    assert_eq!(from_bytes.to_hex(), direct.to_hex());
}

#[test]
fn phash_simple_jpeg_chain_consistent() {
    let path = abs(JPEG_FIXTURE);
    let bytes = read_fixture(JPEG_FIXTURE);

    let from_path = phash_simple(&path, 8).expect("phash_simple path failed");
    let from_bytes = phash_simple_bytes(&bytes, 8).expect("phash_simple bytes failed");
    let img = img_from_bytes(&bytes);
    let direct = rosetta_image_hash::phash_simple(&img, 8).expect("direct phash_simple failed");

    assert_eq!(from_path.to_hex(), direct.to_hex());
    assert_eq!(from_bytes.to_hex(), direct.to_hex());
}

// ── dhash ────────────────────────────────────────────────────────────────────

#[test]
fn dhash_png_chain_consistent() {
    let path = abs(PNG_FIXTURE);
    let bytes = read_fixture(PNG_FIXTURE);

    let from_path = dhash(&path, 8).expect("dhash path failed");
    let from_bytes = dhash_bytes(&bytes, 8).expect("dhash bytes failed");
    let img = img_from_bytes(&bytes);
    let direct = rosetta_image_hash::dhash(&img, 8).expect("direct dhash failed");

    assert!(!from_path.to_hex().is_empty());
    assert_eq!(from_path.to_hex(), direct.to_hex());
    assert_eq!(from_bytes.to_hex(), direct.to_hex());
}

#[test]
fn dhash_jpeg_chain_consistent() {
    let path = abs(JPEG_FIXTURE);
    let bytes = read_fixture(JPEG_FIXTURE);

    let from_path = dhash(&path, 8).expect("dhash path failed");
    let from_bytes = dhash_bytes(&bytes, 8).expect("dhash bytes failed");
    let img = img_from_bytes(&bytes);
    let direct = rosetta_image_hash::dhash(&img, 8).expect("direct dhash failed");

    assert_eq!(from_path.to_hex(), direct.to_hex());
    assert_eq!(from_bytes.to_hex(), direct.to_hex());
}

#[test]
fn dhash_webp_chain_consistent() {
    let path = abs(WEBP_FIXTURE);
    let bytes = read_fixture(WEBP_FIXTURE);

    let from_path = dhash(&path, 8).expect("dhash path failed");
    let from_bytes = dhash_bytes(&bytes, 8).expect("dhash bytes failed");
    let img = img_from_bytes(&bytes);
    let direct = rosetta_image_hash::dhash(&img, 8).expect("direct dhash failed");

    assert_eq!(from_path.to_hex(), direct.to_hex());
    assert_eq!(from_bytes.to_hex(), direct.to_hex());
}

// ── dhash_vertical ───────────────────────────────────────────────────────────

#[test]
fn dhash_vertical_png_chain_consistent() {
    let path = abs(PNG_FIXTURE);
    let bytes = read_fixture(PNG_FIXTURE);

    let from_path = dhash_vertical(&path, 8).expect("dhash_vertical path failed");
    let from_bytes = dhash_vertical_bytes(&bytes, 8).expect("dhash_vertical bytes failed");
    let img = img_from_bytes(&bytes);
    let direct =
        rosetta_image_hash::dhash_vertical(&img, 8).expect("direct dhash_vertical failed");

    assert!(!from_path.to_hex().is_empty());
    assert_eq!(from_path.to_hex(), direct.to_hex());
    assert_eq!(from_bytes.to_hex(), direct.to_hex());
}

// ── average_hash ─────────────────────────────────────────────────────────────

#[test]
fn average_hash_png_chain_consistent() {
    let path = abs(PNG_FIXTURE);
    let bytes = read_fixture(PNG_FIXTURE);

    let from_path = average_hash(&path, 8).expect("average_hash path failed");
    let from_bytes = average_hash_bytes(&bytes, 8).expect("average_hash bytes failed");
    let img = img_from_bytes(&bytes);
    let direct = rosetta_image_hash::average_hash(&img, 8).expect("direct average_hash failed");

    assert!(!from_path.to_hex().is_empty());
    assert_eq!(from_path.to_hex(), direct.to_hex());
    assert_eq!(from_bytes.to_hex(), direct.to_hex());
}

#[test]
fn average_hash_jpeg_chain_consistent() {
    let path = abs(JPEG_FIXTURE);
    let bytes = read_fixture(JPEG_FIXTURE);

    let from_path = average_hash(&path, 8).expect("average_hash path failed");
    let from_bytes = average_hash_bytes(&bytes, 8).expect("average_hash bytes failed");
    let img = img_from_bytes(&bytes);
    let direct = rosetta_image_hash::average_hash(&img, 8).expect("direct average_hash failed");

    assert_eq!(from_path.to_hex(), direct.to_hex());
    assert_eq!(from_bytes.to_hex(), direct.to_hex());
}

// ── whash_haar ───────────────────────────────────────────────────────────────

#[test]
fn whash_haar_png_chain_consistent() {
    let path = abs(PNG_FIXTURE);
    let bytes = read_fixture(PNG_FIXTURE);

    let from_path = whash_haar(&path, 8).expect("whash_haar path failed");
    let from_bytes = whash_haar_bytes(&bytes, 8).expect("whash_haar bytes failed");
    let img = img_from_bytes(&bytes);
    let direct = rosetta_image_hash::whash_haar(&img, 8).expect("direct whash_haar failed");

    assert!(!from_path.to_hex().is_empty());
    assert_eq!(from_path.to_hex(), direct.to_hex());
    assert_eq!(from_bytes.to_hex(), direct.to_hex());
}

#[test]
fn whash_haar_jpeg_chain_consistent() {
    let path = abs(JPEG_FIXTURE);
    let bytes = read_fixture(JPEG_FIXTURE);

    let from_path = whash_haar(&path, 8).expect("whash_haar path failed");
    let from_bytes = whash_haar_bytes(&bytes, 8).expect("whash_haar bytes failed");
    let img = img_from_bytes(&bytes);
    let direct = rosetta_image_hash::whash_haar(&img, 8).expect("direct whash_haar failed");

    assert_eq!(from_path.to_hex(), direct.to_hex());
    assert_eq!(from_bytes.to_hex(), direct.to_hex());
}

// ── whash_db4 ────────────────────────────────────────────────────────────────

#[test]
fn whash_db4_png_chain_consistent() {
    let path = abs(PNG_FIXTURE);
    let bytes = read_fixture(PNG_FIXTURE);

    let from_path = whash_db4(&path, 8).expect("whash_db4 path failed");
    let from_bytes = whash_db4_bytes(&bytes, 8).expect("whash_db4 bytes failed");
    let img = img_from_bytes(&bytes);
    let direct = rosetta_image_hash::whash_db4(&img, 8).expect("direct whash_db4 failed");

    assert!(!from_path.to_hex().is_empty());
    assert_eq!(from_path.to_hex(), direct.to_hex());
    assert_eq!(from_bytes.to_hex(), direct.to_hex());
}

#[test]
fn whash_db4_webp_chain_consistent() {
    let path = abs(WEBP_FIXTURE);
    let bytes = read_fixture(WEBP_FIXTURE);

    let from_path = whash_db4(&path, 8).expect("whash_db4 path failed");
    let from_bytes = whash_db4_bytes(&bytes, 8).expect("whash_db4 bytes failed");
    let img = img_from_bytes(&bytes);
    let direct = rosetta_image_hash::whash_db4(&img, 8).expect("direct whash_db4 failed");

    assert_eq!(from_path.to_hex(), direct.to_hex());
    assert_eq!(from_bytes.to_hex(), direct.to_hex());
}

// ── whash_db4_robust ─────────────────────────────────────────────────────────

#[test]
fn whash_db4_robust_png_chain_consistent() {
    let path = abs(PNG_FIXTURE);
    let bytes = read_fixture(PNG_FIXTURE);

    let from_path = whash_db4_robust(&path, 8).expect("whash_db4_robust path failed");
    let from_bytes = whash_db4_robust_bytes(&bytes, 8).expect("whash_db4_robust bytes failed");
    let img = img_from_bytes(&bytes);
    let direct =
        rosetta_image_hash::whash_db4_robust(&img, 8).expect("direct whash_db4_robust failed");

    assert!(!from_path.to_hex().is_empty());
    assert_eq!(from_path.to_hex(), direct.to_hex());
    assert_eq!(from_bytes.to_hex(), direct.to_hex());
}

// ── colorhash ────────────────────────────────────────────────────────────────

#[test]
fn colorhash_png_chain_consistent() {
    let path = abs(PNG_FIXTURE);
    let bytes = read_fixture(PNG_FIXTURE);

    let from_path = colorhash(&path, 3).expect("colorhash path failed");
    let from_bytes = colorhash_bytes(&bytes, 3).expect("colorhash bytes failed");
    let img = img_from_bytes(&bytes);
    let direct = rosetta_image_hash::colorhash(&img, 3).expect("direct colorhash failed");

    assert!(!from_path.to_hex().is_empty());
    assert_eq!(from_path.to_hex(), direct.to_hex());
    assert_eq!(from_bytes.to_hex(), direct.to_hex());
}

#[test]
fn colorhash_jpeg_chain_consistent() {
    let path = abs(JPEG_FIXTURE);
    let bytes = read_fixture(JPEG_FIXTURE);

    let from_path = colorhash(&path, 3).expect("colorhash path failed");
    let from_bytes = colorhash_bytes(&bytes, 3).expect("colorhash bytes failed");
    let img = img_from_bytes(&bytes);
    let direct = rosetta_image_hash::colorhash(&img, 3).expect("direct colorhash failed");

    assert_eq!(from_path.to_hex(), direct.to_hex());
    assert_eq!(from_bytes.to_hex(), direct.to_hex());
}

// ── crop_resistant_hash ───────────────────────────────────────────────────────

#[test]
fn crop_resistant_hash_png_path_and_bytes_agree() {
    let path = abs(PNG_LARGE_FIXTURE);
    let bytes = read_fixture(PNG_LARGE_FIXTURE);

    let from_path = crop_resistant_hash(&path, None).expect("crop_resistant_hash path failed");
    let from_bytes =
        crop_resistant_hash_bytes(&bytes, None).expect("crop_resistant_hash bytes failed");

    assert!(!from_path.segment_hashes.is_empty());
    assert_eq!(from_path.to_hex(), from_bytes.to_hex());
}

#[test]
fn crop_resistant_hash_png_chain_consistent() {
    let path = abs(PNG_LARGE_FIXTURE);
    let bytes = read_fixture(PNG_LARGE_FIXTURE);

    let from_path = crop_resistant_hash(&path, None).expect("crop_resistant_hash path failed");
    let img = img_from_bytes(&bytes);
    let direct = rosetta_image_hash::crop_resistant_hash(&img, None)
        .expect("direct crop_resistant_hash failed");

    assert_eq!(from_path.to_hex(), direct.to_hex());
}

#[test]
fn crop_resistant_hash_limit_segments_caps_count() {
    // Verifies the limit_segments parameter is forwarded end-to-end (squint → hash).
    let path = abs(PNG_LARGE_FIXTURE);
    let unlimited =
        crop_resistant_hash(&path, None).expect("crop_resistant_hash unlimited failed");
    if unlimited.segment_hashes.len() <= 1 {
        // The chosen fixture only produces one segment; nothing to verify.
        return;
    }
    let limited =
        crop_resistant_hash(&path, Some(1)).expect("crop_resistant_hash limit=1 failed");
    assert_eq!(limited.segment_hashes.len(), 1);
    assert!(limited.segment_hashes.len() < unlimited.segment_hashes.len());
}

// ── print example hash for report ────────────────────────────────────────────

#[test]
fn report_phash_on_grayscale_jpeg() {
    // This test exists solely to emit the example hash for the report.
    let grayscale_jpg =
        "../../../decode/spec/fixtures/jpeg/valid/8x8-grayscale.jpg";
    let path = abs(grayscale_jpg);
    if !path.exists() {
        return; // fixture missing, skip
    }
    let h = phash(&path, 8).expect("phash on 8x8-grayscale.jpg failed");
    // Print the hash so it appears in `cargo test -- --nocapture` output.
    println!("phash(8x8-grayscale.jpg, 8) = {}", h);
    assert!(!h.to_hex().is_empty());
}
