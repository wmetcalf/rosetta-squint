use crate::bmp::decode_bmp;
use crate::png::decode_png;
use crate::error::{DecodeError, DecodeErrorKind};
use crate::types::{DecodedImage, Format};

/// Decode auto-detects the format from magic bytes and decodes.
pub fn decode(bytes: &[u8]) -> Result<DecodedImage, DecodeError> {
    let fmt = detect_format(bytes).ok_or_else(|| {
        DecodeError::new(DecodeErrorKind::UnsupportedFormat, None, "")
    })?;
    match fmt {
        Format::Bmp => decode_bmp(bytes),
        Format::Png => decode_png(bytes),
        other => Err(DecodeError::new(DecodeErrorKind::UnsupportedFormat, Some(other), "")),
    }
}

/// Returns the Format if the magic bytes match, else None.
pub fn detect_format(bytes: &[u8]) -> Option<Format> {
    if bytes.len() < 2 {
        return None;
    }
    if bytes[0] == 0x42 && bytes[1] == 0x4D {
        return Some(Format::Bmp);
    }
    if bytes.len() >= 8
        && bytes[0] == 0x89
        && bytes[1] == 0x50
        && bytes[2] == 0x4E
        && bytes[3] == 0x47
        && bytes[4] == 0x0D
        && bytes[5] == 0x0A
        && bytes[6] == 0x1A
        && bytes[7] == 0x0A
    {
        return Some(Format::Png);
    }
    None
}

/// Returns the list of formats this port can decode.
pub fn supported_formats() -> Vec<Format> {
    vec![Format::Bmp, Format::Png]
}
