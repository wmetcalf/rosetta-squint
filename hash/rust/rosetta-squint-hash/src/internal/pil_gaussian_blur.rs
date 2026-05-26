//! Pillow-compatible GaussianBlur(radius=2) on 2-D grayscale uint8 images.
//!
//! Pillow implements gaussian blur as 3 passes of a fractional box blur
//! (ImagingGaussianBlur in BoxBlur.c), where the box radius is computed via:
//!
//!   sigma2 = radius^2 / passes   (passes = 3)
//!   L = sqrt(12 * sigma2 + 1)
//!   l = floor((L - 1) / 2)
//!   a = (2l+1)(l(l+1) - 3*sigma2) / (6*(sigma2 - (l+1)^2))
//!   float_radius = l + a
//!
//! Each pass is ImagingBoxBlurInterp: a box blur with fractional-pixel edges,
//! implemented with integer fixed-point arithmetic (24-bit shift):
//!   radius = floor(float_radius)
//!   ww = (1<<24) / (float_radius * 2 + 1)    (integer division of float)
//!   fw = ((1<<24) - (radius * 2 + 1) * ww) / 2
//!   output[x] = ((acc * ww + (left_edge + right_edge) * fw) + (1<<23)) >> 24
//!
//! Boundary condition: edge replication (clamp).
//! Passes: 3 horizontal then 3 vertical (via transpose trick).

/// Compute the box blur float-radius used by Pillow for gaussian approximation.
fn gaussian_box_radius(radius: f64, passes: f64) -> f64 {
    let sigma2 = radius * radius / passes;
    let l_raw = (12.0f64 * sigma2 + 1.0).sqrt();
    let l = ((l_raw - 1.0) / 2.0).floor();
    // Fractional part (from Gwosdek 2011 formula)
    let a_num = (2.0 * l + 1.0) * (l * (l + 1.0) - 3.0 * sigma2);
    let a_den = 6.0 * (sigma2 - (l + 1.0) * (l + 1.0));
    let a = a_num / a_den;
    l + a
}

/// One horizontal pass of the fixed-point fractional box blur (8-bit mode).
/// Matches Pillow's ImagingLineBoxBlur8 exactly.
fn line_box_blur8(line_in: &[u8], radius: usize, edge_a: usize, edge_b: usize, ww: u32, fw: u32) -> Vec<u8> {
    let lastx = line_in.len() - 1;
    let n = lastx + 1;
    let mut line_out = vec![0u8; n];

    // Initialize accumulator for virtual position x = -1.
    // acc = lineIn[0] * (radius + 1) + sum(lineIn[0..edgeA-1]) + lineIn[lastx] * max(0, radius - edgeA + 1)
    let mut acc: u32 = line_in[0] as u32 * (radius as u32 + 1);
    for x in 0..(edge_a.saturating_sub(1)) {
        acc += line_in[x] as u32;
    }
    // radius - edgeA + 1 can be 0 for large images
    if radius + 1 > edge_a {
        acc += line_in[lastx] as u32 * (radius - edge_a + 1) as u32;
    }

    if edge_a <= edge_b {
        // Left edge region: x in [0, edgeA)
        for x in 0..edge_a {
            acc = acc + line_in[x + radius] as u32 - line_in[0] as u32;
            let bulk = acc as u64 * ww as u64
                + (line_in[0] as u64 + line_in[x + radius + 1] as u64) * fw as u64;
            line_out[x] = ((bulk + (1 << 23)) >> 24) as u8;
        }
        // Middle region: x in [edgeA, edgeB)
        for x in edge_a..edge_b {
            acc = acc + line_in[x + radius] as u32 - line_in[x - radius - 1] as u32;
            let bulk = acc as u64 * ww as u64
                + (line_in[x - radius - 1] as u64 + line_in[x + radius + 1] as u64) * fw as u64;
            line_out[x] = ((bulk + (1 << 23)) >> 24) as u8;
        }
        // Right edge region: x in [edgeB, n)
        for x in edge_b..n {
            acc = acc + line_in[lastx] as u32 - line_in[x - radius - 1] as u32;
            let bulk = acc as u64 * ww as u64
                + (line_in[x - radius - 1] as u64 + line_in[lastx] as u64) * fw as u64;
            line_out[x] = ((bulk + (1 << 23)) >> 24) as u8;
        }
    } else {
        // Small image (edgeB < edgeA)
        for x in 0..edge_b {
            acc = acc + line_in[x + radius] as u32 - line_in[0] as u32;
            let bulk = acc as u64 * ww as u64
                + (line_in[0] as u64 + line_in[x + radius + 1] as u64) * fw as u64;
            line_out[x] = ((bulk + (1 << 23)) >> 24) as u8;
        }
        for x in edge_b..edge_a {
            acc = acc + line_in[lastx] as u32 - line_in[0] as u32;
            let bulk = acc as u64 * ww as u64
                + (line_in[0] as u64 + line_in[lastx] as u64) * fw as u64;
            line_out[x] = ((bulk + (1 << 23)) >> 24) as u8;
        }
        for x in edge_a..n {
            acc = acc + line_in[lastx] as u32 - line_in[x - radius - 1] as u32;
            let bulk = acc as u64 * ww as u64
                + (line_in[x - radius - 1] as u64 + line_in[lastx] as u64) * fw as u64;
            line_out[x] = ((bulk + (1 << 23)) >> 24) as u8;
        }
    }

    line_out
}

/// Apply one full horizontal pass over a 2-D image.
fn horizontal_box_blur(image: &[Vec<u8>], float_r: f64) -> Vec<Vec<u8>> {
    if image.is_empty() {
        return vec![];
    }
    let m = image[0].len();
    let radius = float_r as usize; // floor
    // Fixed-point weights
    let ww = ((1u32 << 24) as f64 / (float_r * 2.0 + 1.0)) as u32;
    let fw = ((1u32 << 24) - (radius * 2 + 1) as u32 * ww) / 2;
    let edge_a = (radius + 1).min(m);
    let edge_b = if m > radius + 1 { m - radius - 1 } else { 0 };

    image.iter().map(|row| line_box_blur8(row, radius, edge_a, edge_b, ww, fw)).collect()
}

/// Apply PIL GaussianBlur(radius=blur_radius) to a 2-D grayscale image.
///
/// Input:  `&[Vec<u8>]` — rows of uint8 pixels.
/// Output: `Vec<Vec<u8>>` — same shape, after 3 horizontal + 3 vertical passes.
pub fn apply(image: &[Vec<u8>], blur_radius: f64) -> Vec<Vec<u8>> {
    if image.is_empty() || image[0].is_empty() {
        return image.to_vec();
    }
    let float_r = gaussian_box_radius(blur_radius, 3.0);

    // 3 horizontal passes
    let mut result = image.to_vec();
    for _ in 0..3 {
        result = horizontal_box_blur(&result, float_r);
    }

    // 3 vertical passes via transpose trick
    let h = result.len();
    let w = result[0].len();
    // Transpose: shape (h, w) -> (w, h)
    let mut transposed: Vec<Vec<u8>> = (0..w).map(|x| (0..h).map(|y| result[y][x]).collect()).collect();
    for _ in 0..3 {
        transposed = horizontal_box_blur(&transposed, float_r);
    }
    // Transpose back: shape (w, h) -> (h, w)
    (0..h).map(|y| (0..w).map(|x| transposed[x][y]).collect()).collect()
}
