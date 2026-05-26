//! Loads `spec/decoded/<name>.rgb.bin` into a `DynamicImage::ImageRgb8`.

use std::fs;

use image::{DynamicImage, ImageBuffer, Rgb};

use super::spec_path;

pub fn load_predecoded(name: &str) -> DynamicImage {
    let path = format!("{}/decoded/{}.rgb.bin", spec_path::SPEC_DIR, name);
    let data = fs::read(&path).unwrap_or_else(|e| panic!("read {path}: {e}"));
    assert!(data.len() >= 8, "decoded file {name} too short");
    let w = u32::from_le_bytes(data[0..4].try_into().unwrap());
    let h = u32::from_le_bytes(data[4..8].try_into().unwrap());
    let expected = 8 + (w as usize) * (h as usize) * 3;
    assert_eq!(
        data.len(),
        expected,
        "decoded {name} length mismatch: got {}, expected {expected}",
        data.len()
    );
    let buf: ImageBuffer<Rgb<u8>, Vec<u8>> =
        ImageBuffer::from_raw(w, h, data[8..].to_vec()).expect("ImageBuffer construction");
    DynamicImage::ImageRgb8(buf)
}
