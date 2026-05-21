use crate::error::{DecodeError, DecodeErrorKind};
use crate::types::{Channels, DecodedImage, Format};
use libheif_rs::{ColorSpace, HeifContext, LibHeif, RgbChroma};

pub(crate) fn decode_heic(bytes: &[u8]) -> Result<DecodedImage, DecodeError> {
    let ctx = HeifContext::read_from_bytes(bytes).map_err(|e| {
        DecodeError::new(
            DecodeErrorKind::CorruptInput,
            Some(Format::Heic),
            format!("HeifContext::read failed: {}", e),
        )
    })?;

    let handle = ctx.primary_image_handle().map_err(|e| {
        DecodeError::new(
            DecodeErrorKind::CorruptInput,
            Some(Format::Heic),
            format!("primary_image_handle failed: {}", e),
        )
    })?;

    let has_alpha = handle.has_alpha_channel();
    let chroma = if has_alpha {
        RgbChroma::Rgba
    } else {
        RgbChroma::Rgb
    };

    // Use None for decoding options to match pillow-heif's behavior:
    // pillow-heif calls heif_decoding_options_alloc() and uses system defaults
    // (bilinear upsampling, average downsampling, only_use=false) without
    // explicitly overriding color conversion options.
    let lib_heif = LibHeif::new();
    let img = lib_heif
        .decode(&handle, ColorSpace::Rgb(chroma), None)
        .map_err(|e| {
            DecodeError::new(
                DecodeErrorKind::CorruptInput,
                Some(Format::Heic),
                format!("decode failed: {}", e),
            )
        })?;

    let planes = img.planes();
    let plane = planes.interleaved.ok_or_else(|| {
        DecodeError::new(
            DecodeErrorKind::CorruptInput,
            Some(Format::Heic),
            "no interleaved plane",
        )
    })?;

    let width = plane.width as usize;
    let height = plane.height as usize;
    let stride = plane.stride;
    let bpp = if has_alpha { 4 } else { 3 };

    // Copy row-by-row to strip stride padding
    let mut data = Vec::with_capacity(width * height * bpp);
    for y in 0..height {
        let row_start = y * stride;
        data.extend_from_slice(&plane.data[row_start..row_start + width * bpp]);
    }

    Ok(DecodedImage {
        width,
        height,
        data,
        channels: if has_alpha {
            Channels::Rgba
        } else {
            Channels::Rgb
        },
        format: Format::Heic,
    })
}
