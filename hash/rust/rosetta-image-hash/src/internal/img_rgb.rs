//! Normalize any `image::DynamicImage` to a canonical Vec<Vec<[u8; 3]>>
//! row-major RGB buffer. Non-opaque sources are composited on opaque black.

use image::{DynamicImage, GenericImageView};

pub fn to_rgb(img: &DynamicImage) -> Vec<Vec<[u8; 3]>> {
    let (w, h) = img.dimensions();
    let w = w as usize;
    let h = h as usize;

    // For ImageRgba8 specifically, manually composite on black to ensure
    // PIL-compatible semantics. For other types, to_rgb8() handles the
    // conversion (including indexed palette expansion).
    let rgb8 = match img {
        DynamicImage::ImageRgba8(rgba) => {
            // Composite RGBA on opaque black via straight-alpha-against-black formula.
            // For PIL parity: out = src.rgb * (alpha/255). When alpha=0, out=(0,0,0).
            let mut buf = image::ImageBuffer::<image::Rgb<u8>, Vec<u8>>::new(w as u32, h as u32);
            for (x, y, p) in rgba.enumerate_pixels() {
                let a = u32::from(p.0[3]);
                let r = (u32::from(p.0[0]) * a / 255) as u8;
                let g = (u32::from(p.0[1]) * a / 255) as u8;
                let b = (u32::from(p.0[2]) * a / 255) as u8;
                buf.put_pixel(x, y, image::Rgb([r, g, b]));
            }
            buf
        }
        _ => img.to_rgb8(),
    };

    let mut out: Vec<Vec<[u8; 3]>> = Vec::with_capacity(h);
    for y in 0..h {
        let mut row: Vec<[u8; 3]> = Vec::with_capacity(w);
        for x in 0..w {
            let p = rgb8.get_pixel(x as u32, y as u32);
            row.push([p.0[0], p.0[1], p.0[2]]);
        }
        out.push(row);
    }
    out
}

#[cfg(test)]
mod tests {
    use super::*;
    use image::{ImageBuffer, Rgb, Rgba};

    #[test]
    fn rgb8_passthrough() {
        let mut img = ImageBuffer::new(2, 1);
        img.put_pixel(0, 0, Rgb([255, 0, 0]));
        img.put_pixel(1, 0, Rgb([0, 255, 0]));
        let dyn_img = DynamicImage::ImageRgb8(img);
        let rgb = to_rgb(&dyn_img);
        assert_eq!(rgb.len(), 1);
        assert_eq!(rgb[0].len(), 2);
        assert_eq!(rgb[0][0], [255, 0, 0]);
        assert_eq!(rgb[0][1], [0, 255, 0]);
    }

    #[test]
    fn rgba_transparent_composites_on_black() {
        let mut img = ImageBuffer::new(1, 1);
        img.put_pixel(0, 0, Rgba([255, 255, 255, 0]));
        let dyn_img = DynamicImage::ImageRgba8(img);
        let rgb = to_rgb(&dyn_img);
        assert_eq!(rgb[0][0], [0, 0, 0]);
    }

    #[test]
    fn rgba_opaque_passes_through() {
        let mut img = ImageBuffer::new(1, 1);
        img.put_pixel(0, 0, Rgba([100, 150, 200, 255]));
        let dyn_img = DynamicImage::ImageRgba8(img);
        let rgb = to_rgb(&dyn_img);
        assert_eq!(rgb[0][0], [100, 150, 200]);
    }

    #[test]
    fn shape_matches_image_size() {
        let img = ImageBuffer::new(5, 3);
        let dyn_img = DynamicImage::ImageRgb8(img);
        let rgb = to_rgb(&dyn_img);
        assert_eq!(rgb.len(), 3);
        assert_eq!(rgb[0].len(), 5);
    }
}
