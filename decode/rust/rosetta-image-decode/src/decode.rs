use crate::bmp::decode_bmp;
use crate::gif::decode_gif;
use crate::png::decode_png;
use crate::error::{DecodeError, DecodeErrorKind};
use crate::types::{DecodedImage, Format};
use crate::webp::decode_webp;

/// Decode auto-detects the format from magic bytes and decodes.
pub fn decode(bytes: &[u8]) -> Result<DecodedImage, DecodeError> {
    let fmt = detect_format(bytes).ok_or_else(|| {
        DecodeError::new(DecodeErrorKind::UnsupportedFormat, None, "")
    })?;
    match fmt {
        Format::Bmp => decode_bmp(bytes),
        Format::Gif => decode_gif(bytes),
        Format::Jpeg => crate::jpeg::decode_jpeg(bytes),
        Format::Png => decode_png(bytes),
        Format::Webp => decode_webp(bytes),
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
    // GIF87a or GIF89a
    if bytes.len() >= 6
        && bytes[0] == b'G'
        && bytes[1] == b'I'
        && bytes[2] == b'F'
        && bytes[3] == b'8'
        && (bytes[4] == b'7' || bytes[4] == b'9')
        && bytes[5] == b'a'
    {
        return Some(Format::Gif);
    }
    // JPEG: starts with FF D8 (SOI marker); third byte FF is typical but not required for detection
    if bytes.len() >= 2
        && bytes[0] == 0xFF
        && bytes[1] == 0xD8
    {
        return Some(Format::Jpeg);
    }
    // WebP: RIFF????WEBP (bytes 0-3 = "RIFF", bytes 8-11 = "WEBP")
    if bytes.len() >= 12
        && bytes[0..4] == [b'R', b'I', b'F', b'F']
        && bytes[8..12] == [b'W', b'E', b'B', b'P']
    {
        return Some(Format::Webp);
    }
    None
}

/// Returns the list of formats this port can decode.
pub fn supported_formats() -> Vec<Format> {
    vec![Format::Bmp, Format::Png, Format::Gif, Format::Jpeg, Format::Webp]
}
