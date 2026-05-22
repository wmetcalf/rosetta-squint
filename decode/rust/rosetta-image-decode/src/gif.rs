use crate::error::{DecodeError, DecodeErrorKind};
use crate::limits::check_dimensions;
use crate::types::{Channels, DecodedImage, Format};

use image::ImageReader;
use std::io::Cursor;

/// Returns true if the GIF's first frame has a Graphic Control Extension
/// with the transparency flag set — matching PIL's img.info['transparency'] logic.
fn gif_has_transparency(bytes: &[u8]) -> bool {
    if bytes.len() < 13 {
        return false;
    }
    // Skip 6-byte header + 7-byte logical screen descriptor
    let packed = bytes[10];
    let gct_flag = (packed >> 7) & 1;
    let gct_size = packed & 7;

    let mut i: usize = 13;
    // Skip global color table
    if gct_flag != 0 {
        let n_colors = 1usize << (gct_size + 1);
        i += n_colors * 3;
    }

    // Walk blocks until we hit the first image descriptor
    while i < bytes.len() {
        let block_type = bytes[i];
        match block_type {
            0x3B => return false, // GIF trailer
            0x21 => {
                // Extension block
                if i + 1 >= bytes.len() {
                    return false;
                }
                let ext_label = bytes[i + 1];
                if ext_label == 0xF9 {
                    // Graphic Control Extension
                    // Format: 0x21 0xF9 <block-size=4> <flags> <delay-lo> <delay-hi> <transparent-idx> 0x00
                    if i + 3 < bytes.len() {
                        let flags = bytes[i + 3];
                        if flags & 1 != 0 {
                            return true;
                        }
                    }
                    // This GCE belongs to the first image — if it has no transparency, stop.
                    // Skip extension sub-blocks
                    i += 2;
                    while i < bytes.len() {
                        let sub_len = bytes[i] as usize;
                        i += 1;
                        if sub_len == 0 {
                            break;
                        }
                        i += sub_len;
                    }
                    // After the GCE we expect the image descriptor (0x2C), don't keep scanning
                    return false;
                } else {
                    // Other extension — skip it
                    i += 2;
                    while i < bytes.len() {
                        let sub_len = bytes[i] as usize;
                        i += 1;
                        if sub_len == 0 {
                            break;
                        }
                        i += sub_len;
                    }
                }
            }
            0x2C => {
                // Image descriptor — first frame reached, no GCE transparency found
                return false;
            }
            _ => {
                // Unknown block, stop
                return false;
            }
        }
    }
    false
}

pub(crate) fn decode_gif(bytes: &[u8]) -> Result<DecodedImage, DecodeError> {
    let reader = ImageReader::with_format(Cursor::new(bytes), image::ImageFormat::Gif);
    let img = match reader.decode() {
        Ok(i) => i,
        Err(e) => {
            return Err(DecodeError::new(
                DecodeErrorKind::CorruptInput,
                Some(Format::Gif),
                format!("image::decode failed: {}", e),
            ))
        }
    };

    let (width, height) = (img.width() as usize, img.height() as usize);
    check_dimensions(width, height, Format::Gif)?;

    // The image crate always returns RGBA8 for GIF palette images.
    // We replicate PIL's behaviour: use RGBA only if the first frame has a
    // Graphic Control Extension with the transparency flag set.
    if gif_has_transparency(bytes) {
        let data: Vec<u8> = img.to_rgba8().into_raw();
        Ok(DecodedImage {
            width,
            height,
            data,
            channels: Channels::Rgba,
            format: Format::Gif,
        })
    } else {
        let data: Vec<u8> = img.to_rgb8().into_raw();
        Ok(DecodedImage {
            width,
            height,
            data,
            channels: Channels::Rgb,
            format: Format::Gif,
        })
    }
}
