use crate::error::{DecodeError, DecodeErrorKind};
use crate::types::{Channels, DecodedImage, Format};

use image::ColorType;
use std::io::Cursor;

pub(crate) fn decode_png(bytes: &[u8]) -> Result<DecodedImage, DecodeError> {
    // Peek at the magic bytes: if they don't match PNG, return UnsupportedFormat.
    // (detect_format already filters this before we're called, but guard defensively.)
    if bytes.len() < 8
        || bytes[0] != 0x89
        || bytes[1] != 0x50
        || bytes[2] != 0x4E
        || bytes[3] != 0x47
        || bytes[4] != 0x0D
        || bytes[5] != 0x0A
        || bytes[6] != 0x1A
        || bytes[7] != 0x0A
    {
        return Err(DecodeError::new(
            DecodeErrorKind::UnsupportedFormat,
            None,
            "not a PNG file",
        ));
    }

    let reader = image::ImageReader::with_format(
        Cursor::new(bytes),
        image::ImageFormat::Png,
    );

    let img = match reader.decode() {
        Ok(i) => i,
        Err(e) => {
            return Err(DecodeError::new(
                DecodeErrorKind::CorruptInput,
                Some(Format::Png),
                format!("image::decode failed: {}", e),
            ))
        }
    };

    let width = img.width() as usize;
    let height = img.height() as usize;
    let color = img.color();

    // Determine output channel count based on source color type.
    // PIL rules (matched by Java and Go ports):
    //   - Grayscale without alpha → RGB (replicate gray)
    //   - Grayscale with alpha (LA) → RGBA
    //   - RGB → RGB
    //   - RGBA → RGBA
    //   - Paletted without tRNS → RGB
    //   - Paletted with tRNS → RGBA
    // The image crate expands paletted images during decode; the resulting
    // ColorType reflects the expansion. We use has_alpha to decide.
    let has_alpha = matches!(
        color,
        ColorType::La8 | ColorType::La16 | ColorType::Rgba8 | ColorType::Rgba16
    );

    let channels = if has_alpha {
        Channels::Rgba
    } else {
        Channels::Rgb
    };

    // Special case: 16-bit single-channel grayscale (L16).
    // PIL opens these as mode 'I' (32-bit int) and clips to [0,255] on convert('RGB').
    // The image crate's to_rgb8() uses >> 8, which gives wrong results.
    // We must replicate: gray8 = min(raw_u16, 255), then expand to R=G=B.
    if matches!(color, ColorType::L16) {
        let gray16 = img.into_luma16();
        let raw = gray16.as_raw(); // &[u16] (native endian after decode)
        let pixel_count = width * height;
        let mut data = Vec::with_capacity(pixel_count * 3);
        for &v in raw.iter() {
            let clipped = v.min(255) as u8;
            data.push(clipped); // R
            data.push(clipped); // G
            data.push(clipped); // B
        }
        return Ok(DecodedImage {
            width,
            height,
            data,
            channels: Channels::Rgb,
            format: Format::Png,
        });
    }

    // Special case: 16-bit grayscale+alpha (La16).
    // PIL uses >> 8 for both gray and alpha channels.
    // image crate's to_rgba8() should handle this correctly (>> 8), but let's
    // be explicit via luma_alpha16 to guarantee byte-exactness.
    if matches!(color, ColorType::La16) {
        let la16 = img.into_luma_alpha16();
        let raw = la16.as_raw(); // [gray, alpha, gray, alpha, ...]
        let pixel_count = width * height;
        let mut data = Vec::with_capacity(pixel_count * 4);
        for chunk in raw.chunks_exact(2) {
            let gray = (chunk[0] >> 8) as u8;
            let alpha = (chunk[1] >> 8) as u8;
            data.push(gray);  // R
            data.push(gray);  // G
            data.push(gray);  // B
            data.push(alpha); // A
        }
        return Ok(DecodedImage {
            width,
            height,
            data,
            channels: Channels::Rgba,
            format: Format::Png,
        });
    }

    // Special case: 16-bit RGB (Rgb16).
    // PIL uses >> 8. image crate's to_rgb8() uses >> 8 as well, but let's verify
    // by using the raw buffer directly to be safe.
    if matches!(color, ColorType::Rgb16) {
        let rgb16 = img.into_rgb16();
        let raw = rgb16.as_raw(); // [r, g, b, r, g, b, ...]
        let pixel_count = width * height;
        let mut data = Vec::with_capacity(pixel_count * 3);
        for &v in raw.iter() {
            data.push((v >> 8) as u8);
        }
        return Ok(DecodedImage {
            width,
            height,
            data,
            channels: Channels::Rgb,
            format: Format::Png,
        });
    }

    // Special case: 16-bit RGBA (Rgba16).
    // PIL uses >> 8 for all channels.
    if matches!(color, ColorType::Rgba16) {
        let rgba16 = img.into_rgba16();
        let raw = rgba16.as_raw(); // [r, g, b, a, ...]
        let pixel_count = width * height;
        let mut data = Vec::with_capacity(pixel_count * 4);
        for &v in raw.iter() {
            data.push((v >> 8) as u8);
        }
        return Ok(DecodedImage {
            width,
            height,
            data,
            channels: Channels::Rgba,
            format: Format::Png,
        });
    }

    // For all remaining 8-bit types (L8, Rgb8, Rgba8, and paletted which the
    // image crate expands to Rgb8 or Rgba8), use the standard conversions.
    // image crate's to_rgb8()/to_rgba8() match PIL for these types.
    let data: Vec<u8> = if has_alpha {
        img.to_rgba8().into_raw()
    } else {
        img.to_rgb8().into_raw()
    };

    Ok(DecodedImage {
        width,
        height,
        data,
        channels,
        format: Format::Png,
    })
}
