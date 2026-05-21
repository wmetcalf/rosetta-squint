use crate::error::{DecodeError, DecodeErrorKind};
use crate::limits::check_dimensions;
use crate::types::{Channels, DecodedImage, Format};
use image::ImageReader;
use std::io::Cursor;

pub(crate) fn decode_tiff(bytes: &[u8]) -> Result<DecodedImage, DecodeError> {
    let reader = ImageReader::with_format(Cursor::new(bytes), image::ImageFormat::Tiff);
    let img = match reader.decode() {
        Ok(i) => i,
        Err(e) => {
            return Err(DecodeError::new(
                DecodeErrorKind::CorruptInput,
                Some(Format::Tiff),
                format!("image::decode failed: {}", e),
            ))
        }
    };

    // For v1 we always output RGB (3 channels) — grayscale TIFFs expand to RGB.
    let (width, height) = (img.width() as usize, img.height() as usize);
    check_dimensions(width, height, Format::Tiff)?;
    let data: Vec<u8> = img.to_rgb8().into_raw();

    Ok(DecodedImage {
        width,
        height,
        data,
        channels: Channels::Rgb,
        format: Format::Tiff,
    })
}
