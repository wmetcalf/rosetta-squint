use crate::error::{DecodeError, DecodeErrorKind};
use crate::types::{DecodedImage, Format};

pub(crate) fn decode_bmp(_bytes: &[u8]) -> Result<DecodedImage, DecodeError> {
    Err(DecodeError::new(
        DecodeErrorKind::UnsupportedFeature,
        Some(Format::Bmp),
        "BMP decoder not yet implemented",
    ))
}
