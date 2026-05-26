//! Pillow-compatible MedianFilter(size=3) on 2-D grayscale uint8 images.
//!
//! For each pixel (y, x), gather the 9 values from the 3×3 window
//! (y-1..=y+1, x-1..=x+1), clamping indices to [0, H-1] × [0, W-1]
//! (edge replication). Sort the 9 values and return index 4 (the median).

/// Apply PIL MedianFilter(size=3) to a 2-D grayscale image.
///
/// Input:  `&[Vec<u8>]` — rows of uint8 pixels, shape (H, W).
/// Output: `Vec<Vec<u8>>` — same shape.
pub fn apply(image: &[Vec<u8>]) -> Vec<Vec<u8>> {
    let h = image.len();
    if h == 0 {
        return vec![];
    }
    let w = image[0].len();
    if w == 0 {
        return vec![vec![]; h];
    }

    let mut out = vec![vec![0u8; w]; h];
    for y in 0..h {
        for x in 0..w {
            let mut window = [0u8; 9];
            let mut k = 0;
            for dy in -1i32..=1 {
                let sy = (y as i32 + dy).clamp(0, h as i32 - 1) as usize;
                for dx in -1i32..=1 {
                    let sx = (x as i32 + dx).clamp(0, w as i32 - 1) as usize;
                    window[k] = image[sy][sx];
                    k += 1;
                }
            }
            window.sort_unstable();
            out[y][x] = window[4];
        }
    }
    out
}
