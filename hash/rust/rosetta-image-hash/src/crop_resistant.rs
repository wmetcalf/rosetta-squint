//! crop_resistant_hash: segment-based hash resistant to image cropping.
//!
//! Implements the algorithm from "Efficient Cropping-Resistant Robust Image Hashing"
//! (DOI 10.1109/ARES.2014.85), matching Python `imagehash.crop_resistant_hash`.
//!
//! Pipeline:
//!   1. Keep original image for per-segment cropping.
//!   2. Convert to grayscale (PIL 'L' mode), Lanczos-resize to 300×300.
//!   3. Apply PIL GaussianBlur(radius=2).
//!   4. Apply PIL MedianFilter(size=3).
//!   5. Cast pixels to float32 array (300, 300).
//!   6. Find all segments via _find_all_segments(pixels, threshold=128, min_size=500).
//!   7. If no segments, synthesize a whole-image segment {(0,0), (299,299)}.
//!   8. For each segment: compute bounding box in segmentation coords,
//!      scale to original coords, crop original, dhash → Hash.
//!   9. Return ImageMultiHash.
//!
//! Parity hazard notes (matching spec §3.4):
//! - Bounding box float→int: PIL's Image.crop truncates toward zero (int() in Python).
//! - Scale: scale_w = orig_w / 300.0, scale_h = orig_h / 300.0 (float64).

use image::DynamicImage;

use crate::average::rgb_to_gray;
use crate::dhash::dhash;
use crate::internal::{find_segments, img_rgb, lanczos, pil_gaussian_blur, pil_median_filter};
use crate::multihash::ImageMultiHash;
use crate::ImageHashError;

/// Default segmentation image size (matches Python default).
const SEG_SIZE: usize = 300;
/// Default segment threshold (matches Python default).
const SEG_THRESHOLD: f32 = 128.0;
/// Default minimum segment size (matches Python default).
const SEG_MIN_SIZE: usize = 500;
/// Inner dhash size (fixed at 8 for v1).
const DHASH_SIZE: usize = 8;

/// Compute a crop-resistant hash of an image.
///
/// Uses default parameters matching Python imagehash.crop_resistant_hash:
///   - hash_func = dhash (hash_size=8)
///   - limit_segments = None
///   - segment_threshold = 128
///   - min_segment_size = 500
///   - segmentation_image_size = 300
pub fn crop_resistant_hash(img: &DynamicImage) -> Result<ImageMultiHash, ImageHashError> {
    let orig_w = img.width() as usize;
    let orig_h = img.height() as usize;

    // Step 2: Grayscale + Lanczos resize to 300×300 for segmentation
    let rgb = img_rgb::to_rgb(img);
    let gray = rgb_to_gray(&rgb);
    let resized = lanczos::resize(&gray, SEG_SIZE, SEG_SIZE);

    // Step 3: PIL GaussianBlur(radius=2)
    let blurred = pil_gaussian_blur::apply(&resized, 2.0);

    // Step 4: PIL MedianFilter(size=3)
    let filtered = pil_median_filter::apply(&blurred);

    // Step 5: Cast to float32
    let pixels: Vec<Vec<f32>> = filtered
        .iter()
        .map(|row| row.iter().map(|&v| v as f32).collect())
        .collect();

    // Step 6: Segment
    let mut segments = find_segments::find_all_segments(&pixels, SEG_THRESHOLD, SEG_MIN_SIZE);

    // Step 7: If no segments, synthesize whole-image segment
    if segments.is_empty() {
        segments.push(vec![(0, 0), (SEG_SIZE - 1, SEG_SIZE - 1)]);
    }

    // Step 8: Hash each segment
    let scale_w = orig_w as f64 / SEG_SIZE as f64;
    let scale_h = orig_h as f64 / SEG_SIZE as f64;

    let mut hashes = Vec::with_capacity(segments.len());
    for segment in &segments {
        // Bounding box in segmentation coords
        let min_y = segment.iter().map(|&(y, _)| y).min().unwrap();
        let max_y = segment.iter().map(|&(y, _)| y).max().unwrap();
        let min_x = segment.iter().map(|&(_, x)| x).min().unwrap();
        let max_x = segment.iter().map(|&(_, x)| x).max().unwrap();

        // Scale to original coords.
        // PIL._crop uses: x0, y0, x1, y1 = map(int, map(round, box))
        // i.e., round() then int() (not truncation toward zero).
        let crop_left = (min_x as f64 * scale_w).round() as i64 as u32;
        let crop_top = (min_y as f64 * scale_h).round() as i64 as u32;
        let crop_right = ((max_x + 1) as f64 * scale_w).round() as i64 as u32;
        let crop_bottom = ((max_y + 1) as f64 * scale_h).round() as i64 as u32;

        // Clamp crop to image bounds
        let crop_right = crop_right.min(orig_w as u32);
        let crop_bottom = crop_bottom.min(orig_h as u32);

        // Width / height of crop (must be > 0)
        let cw = crop_right.saturating_sub(crop_left);
        let ch = crop_bottom.saturating_sub(crop_top);
        if cw == 0 || ch == 0 {
            continue;
        }

        // Crop from original image (not the resized segmentation image)
        let cropped = img.crop_imm(crop_left, crop_top, cw, ch);

        // dhash with default hash_size=8
        let h = dhash(&cropped, DHASH_SIZE)?;
        hashes.push(h);
    }

    Ok(ImageMultiHash { segment_hashes: hashes })
}
