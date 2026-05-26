use crate::error::{DecodeError, DecodeErrorKind};
use crate::limits::check_dimensions;
use crate::types::{Channels, DecodedImage, Format};
use image::ImageReader;
use std::io::Cursor;

/// Sniff TIFF ImageWidth (tag 0x0100) and ImageLength (tag 0x0101) from the
/// first IFD without invoking the full decoder. Returns None if the header
/// is malformed (let the main decoder produce the canonical error).
///
/// TIFF layout: byte order (2) + magic (2, = 42) + IFD offset (4). The IFD
/// at that offset contains entry count (2) + N × 12-byte entries +
/// next-IFD offset (4). Each entry is tag(2) + type(2) + count(4) + value(4).
/// For LONG/SHORT typed scalars (count=1) the value field holds the raw
/// dimension; we only need to read those two tags.
fn sniff_tiff_dimensions(bytes: &[u8]) -> Option<(usize, usize)> {
    if bytes.len() < 8 { return None; }
    let little = match &bytes[..2] {
        b"II" => true,
        b"MM" => false,
        _ => return None,
    };
    let read_u16 = |off: usize| -> Option<u16> {
        let s = bytes.get(off..off + 2)?;
        let a = [s[0], s[1]];
        Some(if little { u16::from_le_bytes(a) } else { u16::from_be_bytes(a) })
    };
    let read_u32 = |off: usize| -> Option<u32> {
        let s = bytes.get(off..off + 4)?;
        let a = [s[0], s[1], s[2], s[3]];
        Some(if little { u32::from_le_bytes(a) } else { u32::from_be_bytes(a) })
    };
    if read_u16(2)? != 42 { return None; }
    let ifd_off = read_u32(4)? as usize;
    let n = read_u16(ifd_off)? as usize;
    let mut width: Option<usize> = None;
    let mut height: Option<usize> = None;
    for i in 0..n {
        let entry_off = ifd_off + 2 + i * 12;
        let tag = read_u16(entry_off)?;
        let ty = read_u16(entry_off + 2)?;
        let count = read_u32(entry_off + 4)?;
        if count != 1 { continue; }
        // type 3 = SHORT (u16), 4 = LONG (u32). Other types not used for dimensions.
        let val: usize = match ty {
            3 => read_u16(entry_off + 8)? as usize,
            4 => read_u32(entry_off + 8)? as usize,
            _ => continue,
        };
        match tag {
            0x0100 => width = Some(val),
            0x0101 => height = Some(val),
            _ => {}
        }
        if width.is_some() && height.is_some() { break; }
    }
    Some((width?, height?))
}

pub(crate) fn decode_tiff(bytes: &[u8]) -> Result<DecodedImage, DecodeError> {
    // Sniff dimensions from the first IFD BEFORE invoking the underlying
    // decoder, so the MAX_PIXELS guard fires before the tiff crate allocates
    // the raster. Spec §3.1 requires this ordering.
    if let Some((w, h)) = sniff_tiff_dimensions(bytes) {
        check_dimensions(w, h, Format::Tiff)?;
    }

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
