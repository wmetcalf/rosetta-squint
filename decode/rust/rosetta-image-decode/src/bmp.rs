use crate::error::{DecodeError, DecodeErrorKind};
use crate::limits::check_dimensions;
use crate::types::{Channels, DecodedImage, Format};

const BI_RGB: u32 = 0;
const BI_RLE8: u32 = 1;
const BI_RLE4: u32 = 2;
const BI_BITFIELDS: u32 = 3;
const BI_JPEG: u32 = 4;
const BI_PNG: u32 = 5;
const BI_ALPHABITFIELDS: u32 = 6;

struct BmpHeader {
    width: usize,
    height: usize,
    top_down: bool,
    bit_count: u32,
    compression: u32,
    clr_used: usize,
    red_mask: u32,
    green_mask: u32,
    blue_mask: u32,
    alpha_mask: u32,
    pixel_data_offset: usize,
    dib_header_size: usize,
}

pub(crate) fn decode_bmp(bytes: &[u8]) -> Result<DecodedImage, DecodeError> {
    let hdr = parse_bmp_header(bytes)?;
    check_dimensions(hdr.width, hdr.height, Format::Bmp)?;
    match (hdr.compression, hdr.bit_count) {
        (BI_RGB, 24) => decode_rgb24(bytes, &hdr),
        (BI_RGB, 32) => decode_rgb32(bytes, &hdr),
        (BI_RGB, 8) => decode_pal8(bytes, &hdr),
        (BI_RGB, 4) => decode_pal4(bytes, &hdr),
        (BI_RGB, 1) => decode_pal1(bytes, &hdr),
        (BI_RGB, 16) => Err(corrupt("BI_RGB 16-bit not supported")),
        (BI_RGB, bc) => Err(corrupt(format!("biBitCount {} for BI_RGB", bc))),
        (BI_BITFIELDS, 16) | (BI_ALPHABITFIELDS, 16) => decode_bitfields(bytes, &hdr, 16),
        (BI_BITFIELDS, 32) | (BI_ALPHABITFIELDS, 32) => decode_bitfields(bytes, &hdr, 32),
        (BI_BITFIELDS, bc) | (BI_ALPHABITFIELDS, bc) => {
            Err(corrupt(format!("BI_BITFIELDS with biBitCount {}", bc)))
        }
        (BI_RLE8, _) => decode_rle(bytes, &hdr, 8),
        (BI_RLE4, _) => decode_rle(bytes, &hdr, 4),
        _ => unreachable!(),
    }
}

fn read_u16_le(bytes: &[u8], i: usize) -> Result<u16, DecodeError> {
    bytes
        .get(i..i + 2)
        .map(|s| u16::from_le_bytes(s.try_into().unwrap()))
        .ok_or_else(|| truncated("unexpected end of data"))
}

fn read_u32_le(bytes: &[u8], i: usize) -> Result<u32, DecodeError> {
    bytes
        .get(i..i + 4)
        .map(|s| u32::from_le_bytes(s.try_into().unwrap()))
        .ok_or_else(|| truncated("unexpected end of data"))
}

/// Compute `offset + stride * height` with overflow checking.
///
/// On 64-bit targets, `usize` is wide enough that this can never overflow
/// for any input passing `check_dimensions` (which caps width × height).
/// On 32-bit targets, however, `stride * height` for a 2 GiB image plus the
/// pixel-data offset could wrap back to a small value and pass the
/// downstream `bytes.len() < needed` truncation check, leading to an OOB
/// slice index. `checked_mul` + `checked_add` make this defense portable.
fn checked_needed(offset: usize, stride: usize, height: usize) -> Result<usize, DecodeError> {
    stride
        .checked_mul(height)
        .and_then(|n| n.checked_add(offset))
        .ok_or_else(|| truncated("pixel data size overflow"))
}

fn parse_bmp_header(bytes: &[u8]) -> Result<BmpHeader, DecodeError> {
    if bytes.len() < 14 {
        return Err(truncated("file header truncated"));
    }
    if bytes[0] != 0x42 || bytes[1] != 0x4D {
        return Err(corrupt("Not a BMP file (no 'BM' signature)"));
    }

    let bf_off_bits = read_u32_le(bytes, 10)? as usize;

    if bytes.len() < 18 {
        return Err(truncated("DIB header size not readable"));
    }
    let bi_size = read_u32_le(bytes, 14)? as usize;

    if bi_size == 12 {
        return Err(unsupported_feature("OS/2 BMP header (size 12)"));
    }
    if bi_size != 40 && bi_size != 52 && bi_size != 56 && bi_size != 108 && bi_size != 124 {
        return Err(corrupt(format!("DIB header size {} not supported", bi_size)));
    }
    if bytes.len() < 14 + bi_size {
        return Err(truncated("DIB header truncated"));
    }

    // Read as i32 to handle signed width/height
    let bi_width_raw = read_u32_le(bytes, 18)? as i32;
    let bi_height_raw = read_u32_le(bytes, 22)? as i32;
    let bi_planes = read_u16_le(bytes, 26)?;
    let bi_bit_count = read_u16_le(bytes, 28)? as u32;
    let bi_compression = read_u32_le(bytes, 30)?;
    let bi_clr_used = read_u32_le(bytes, 46)? as usize;

    if bi_width_raw <= 0 {
        return Err(corrupt("biWidth must be positive"));
    }
    if bi_height_raw == 0 {
        return Err(corrupt("biHeight must be non-zero"));
    }
    if bi_planes != 1 {
        return Err(corrupt("biPlanes must be 1"));
    }
    if bi_bit_count != 1
        && bi_bit_count != 4
        && bi_bit_count != 8
        && bi_bit_count != 16
        && bi_bit_count != 24
        && bi_bit_count != 32
    {
        return Err(corrupt(format!("biBitCount {} not supported", bi_bit_count)));
    }
    if bi_compression > 6 {
        return Err(corrupt(format!(
            "biCompression {} not supported",
            bi_compression
        )));
    }
    if bi_compression == BI_JPEG {
        return Err(unsupported_feature("embedded JPEG"));
    }
    if bi_compression == BI_PNG {
        return Err(unsupported_feature("embedded PNG"));
    }

    // Masks if applicable
    let has_masks = bi_compression == BI_BITFIELDS
        || bi_compression == BI_ALPHABITFIELDS
        || bi_size >= 52;
    let mut red_mask: u32 = 0;
    let mut green_mask: u32 = 0;
    let mut blue_mask: u32 = 0;
    let mut alpha_mask: u32 = 0;

    if has_masks {
        if bytes.len() < 14 + 40 + 12 {
            return Err(truncated("BI_BITFIELDS masks truncated"));
        }
        red_mask = read_u32_le(bytes, 54)?;
        green_mask = read_u32_le(bytes, 58)?;
        blue_mask = read_u32_le(bytes, 62)?;
        if bi_compression == BI_ALPHABITFIELDS || bi_size >= 56 {
            if bytes.len() < 14 + 40 + 16 {
                return Err(truncated("alpha mask truncated"));
            }
            alpha_mask = read_u32_le(bytes, 66)?;
        }
        if bi_compression == BI_BITFIELDS {
            if red_mask == 0 || green_mask == 0 || blue_mask == 0 {
                return Err(corrupt("BI_BITFIELDS mask is zero"));
            }
        }
    }

    let top_down = bi_height_raw < 0;
    let abs_height = bi_height_raw.unsigned_abs() as usize;

    Ok(BmpHeader {
        width: bi_width_raw as usize,
        height: abs_height,
        top_down,
        bit_count: bi_bit_count,
        compression: bi_compression,
        clr_used: bi_clr_used,
        red_mask,
        green_mask,
        blue_mask,
        alpha_mask,
        pixel_data_offset: bf_off_bits,
        dib_header_size: bi_size,
    })
}

fn decode_rgb24(bytes: &[u8], hdr: &BmpHeader) -> Result<DecodedImage, DecodeError> {
    let stride = ((hdr.width * 3 + 3) / 4) * 4;
    let needed = checked_needed(hdr.pixel_data_offset, stride, hdr.height)?;
    if bytes.len() < needed {
        return Err(truncated("pixel data truncated (24-bit RGB)"));
    }
    let mut pixels = vec![0u8; hdr.width * hdr.height * 3];
    for src_row in 0..hdr.height {
        let dst_row = if hdr.top_down {
            src_row
        } else {
            hdr.height - 1 - src_row
        };
        for x in 0..hdr.width {
            let src_idx = hdr.pixel_data_offset + src_row * stride + x * 3;
            let dst_idx = (dst_row * hdr.width + x) * 3;
            pixels[dst_idx] = bytes[src_idx + 2]; // R (from BGR+2)
            pixels[dst_idx + 1] = bytes[src_idx + 1]; // G (unchanged)
            pixels[dst_idx + 2] = bytes[src_idx]; // B (from BGR+0)
        }
    }
    Ok(DecodedImage {
        width: hdr.width,
        height: hdr.height,
        data: pixels,
        channels: Channels::Rgb,
        format: Format::Bmp,
    })
}

fn decode_rgb32(bytes: &[u8], hdr: &BmpHeader) -> Result<DecodedImage, DecodeError> {
    let stride = hdr.width * 4;
    let needed = checked_needed(hdr.pixel_data_offset, stride, hdr.height)?;
    if bytes.len() < needed {
        return Err(truncated("pixel data truncated (32-bit RGB)"));
    }
    // Always output RGB to match Pillow 11 behavior (Pillow discards alpha for BI_RGB 32-bit).
    let mut pixels = vec![0u8; hdr.width * hdr.height * 3];
    for src_row in 0..hdr.height {
        let dst_row = if hdr.top_down {
            src_row
        } else {
            hdr.height - 1 - src_row
        };
        for x in 0..hdr.width {
            let src_idx = hdr.pixel_data_offset + src_row * stride + x * 4;
            let dst_idx = (dst_row * hdr.width + x) * 3;
            pixels[dst_idx] = bytes[src_idx + 2]; // R (from BGRA+2)
            pixels[dst_idx + 1] = bytes[src_idx + 1]; // G (unchanged)
            pixels[dst_idx + 2] = bytes[src_idx]; // B (from BGRA+0)
            // alpha byte at src_idx+3 discarded
        }
    }
    Ok(DecodedImage {
        width: hdr.width,
        height: hdr.height,
        data: pixels,
        channels: Channels::Rgb,
        format: Format::Bmp,
    })
}

/// Reads the color table for paletted images; returns Vec<[u8; 3]> (R, G, B).
fn read_color_table(
    bytes: &[u8],
    hdr: &BmpHeader,
    entry_count: usize,
) -> Result<Vec<[u8; 3]>, DecodeError> {
    let color_table_offset = 14 + hdr.dib_header_size;
    let color_table_end = entry_count
        .checked_mul(4)
        .and_then(|n| n.checked_add(color_table_offset))
        .ok_or_else(|| truncated("color table size overflow"))?;
    if bytes.len() < color_table_end {
        return Err(truncated("color table truncated"));
    }
    let mut palette = Vec::with_capacity(entry_count);
    for i in 0..entry_count {
        let off = color_table_offset + i * 4;
        palette.push([
            bytes[off + 2], // R
            bytes[off + 1], // G
            bytes[off],     // B
        ]);
    }
    Ok(palette)
}

/// Clamp `biClrUsed` to the bit-depth maximum (PIL's behavior). A hostile BMP
/// could declare `biClrUsed = 0x40000000` and otherwise drive multi-GB palette
/// allocations. Match the Java/Go/JS/Swift clamp added in D-M1 for parity.
///
/// On 64-bit the `checked_mul`/`checked_add` color-table-size arithmetic
/// already catches the OOM case, but the clamp is more efficient (clamps the
/// allocation to ≤ 1 KiB) and removes the inconsistency between ports.
fn clamp_entry_count(clr_used: usize, bit_depth_max: usize) -> usize {
    if clr_used == 0 { bit_depth_max } else { clr_used.min(bit_depth_max) }
}

fn decode_pal8(bytes: &[u8], hdr: &BmpHeader) -> Result<DecodedImage, DecodeError> {
    let color_table_offset = 14 + hdr.dib_header_size;
    let entry_count = clamp_entry_count(hdr.clr_used, 256);
    let color_table_end = entry_count
        .checked_mul(4)
        .and_then(|n| n.checked_add(color_table_offset))
        .ok_or_else(|| truncated("color table size overflow"))?;
    if bytes.len() < color_table_end {
        return Err(truncated("color table truncated (8-bit paletted)"));
    }
    let mut palette = Vec::with_capacity(entry_count);
    for i in 0..entry_count {
        let off = color_table_offset + i * 4;
        palette.push([
            bytes[off + 2], // R
            bytes[off + 1], // G
            bytes[off],     // B
        ]);
    }
    let stride = ((hdr.width + 3) / 4) * 4;
    let needed = checked_needed(hdr.pixel_data_offset, stride, hdr.height)?;
    if bytes.len() < needed {
        return Err(truncated("pixel data truncated (8-bit paletted)"));
    }
    let mut pixels = vec![0u8; hdr.width * hdr.height * 3];
    for src_row in 0..hdr.height {
        let dst_row = if hdr.top_down {
            src_row
        } else {
            hdr.height - 1 - src_row
        };
        for x in 0..hdr.width {
            let src_idx = hdr.pixel_data_offset + src_row * stride + x;
            let mut pal_idx = bytes[src_idx] as usize;
            if pal_idx >= entry_count {
                pal_idx = entry_count - 1;
            }
            let dst_idx = (dst_row * hdr.width + x) * 3;
            pixels[dst_idx] = palette[pal_idx][0];
            pixels[dst_idx + 1] = palette[pal_idx][1];
            pixels[dst_idx + 2] = palette[pal_idx][2];
        }
    }
    Ok(DecodedImage {
        width: hdr.width,
        height: hdr.height,
        data: pixels,
        channels: Channels::Rgb,
        format: Format::Bmp,
    })
}

fn decode_pal4(bytes: &[u8], hdr: &BmpHeader) -> Result<DecodedImage, DecodeError> {
    let entry_count = clamp_entry_count(hdr.clr_used, 16);
    let palette = read_color_table(bytes, hdr, entry_count)?;
    // Row stride: ceil(width*4 / 32) * 4 bytes = ((width * 4 + 31) / 32) * 4
    let stride = ((hdr.width * 4 + 31) / 32) * 4;
    let needed = checked_needed(hdr.pixel_data_offset, stride, hdr.height)?;
    if bytes.len() < needed {
        return Err(truncated("pixel data truncated (4-bit paletted)"));
    }
    let mut pixels = vec![0u8; hdr.width * hdr.height * 3];
    for src_row in 0..hdr.height {
        let dst_row = if hdr.top_down {
            src_row
        } else {
            hdr.height - 1 - src_row
        };
        for x in 0..hdr.width {
            let byte_off = hdr.pixel_data_offset + src_row * stride + (x / 2);
            let b = bytes[byte_off];
            let mut idx = if x % 2 == 0 {
                ((b >> 4) & 0xF) as usize
            } else {
                (b & 0xF) as usize
            };
            if idx >= entry_count {
                idx = entry_count - 1;
            }
            let dst_idx = (dst_row * hdr.width + x) * 3;
            pixels[dst_idx] = palette[idx][0];
            pixels[dst_idx + 1] = palette[idx][1];
            pixels[dst_idx + 2] = palette[idx][2];
        }
    }
    Ok(DecodedImage {
        width: hdr.width,
        height: hdr.height,
        data: pixels,
        channels: Channels::Rgb,
        format: Format::Bmp,
    })
}

fn decode_pal1(bytes: &[u8], hdr: &BmpHeader) -> Result<DecodedImage, DecodeError> {
    let entry_count = clamp_entry_count(hdr.clr_used, 2);
    let palette = read_color_table(bytes, hdr, entry_count)?;
    // Row stride: ceil(width / 32) * 4 bytes = ((width + 31) / 32) * 4
    let stride = ((hdr.width + 31) / 32) * 4;
    let needed = checked_needed(hdr.pixel_data_offset, stride, hdr.height)?;
    if bytes.len() < needed {
        return Err(truncated("pixel data truncated (1-bit paletted)"));
    }
    let mut pixels = vec![0u8; hdr.width * hdr.height * 3];
    for src_row in 0..hdr.height {
        let dst_row = if hdr.top_down {
            src_row
        } else {
            hdr.height - 1 - src_row
        };
        for x in 0..hdr.width {
            let byte_off = hdr.pixel_data_offset + src_row * stride + (x / 8);
            let b = bytes[byte_off];
            // MSB first: bit 7 is pixel 0
            let mut idx = ((b >> (7 - (x % 8))) & 1) as usize;
            if idx >= entry_count {
                idx = entry_count - 1;
            }
            let dst_idx = (dst_row * hdr.width + x) * 3;
            pixels[dst_idx] = palette[idx][0];
            pixels[dst_idx + 1] = palette[idx][1];
            pixels[dst_idx + 2] = palette[idx][2];
        }
    }
    Ok(DecodedImage {
        width: hdr.width,
        height: hdr.height,
        data: pixels,
        channels: Channels::Rgb,
        format: Format::Bmp,
    })
}

fn decode_bitfields(
    bytes: &[u8],
    hdr: &BmpHeader,
    bits_per_pixel: u32,
) -> Result<DecodedImage, DecodeError> {
    let has_alpha = hdr.alpha_mask != 0;
    let ch = if has_alpha { Channels::Rgba } else { Channels::Rgb };
    let pixel_bytes = ch.bytes_per_pixel();

    // Pre-compute shifts and ranges for each channel
    let red_shift = hdr.red_mask.trailing_zeros() as usize;
    let green_shift = hdr.green_mask.trailing_zeros() as usize;
    let blue_shift = hdr.blue_mask.trailing_zeros() as usize;
    let red_range = (hdr.red_mask >> red_shift) as u64;
    let green_range = (hdr.green_mask >> green_shift) as u64;
    let blue_range = (hdr.blue_mask >> blue_shift) as u64;
    let alpha_shift = if has_alpha {
        hdr.alpha_mask.trailing_zeros() as usize
    } else {
        0
    };
    let alpha_range = if has_alpha {
        (hdr.alpha_mask >> alpha_shift) as u64
    } else {
        1
    };

    let stride = if bits_per_pixel == 16 {
        ((hdr.width * 2 + 3) / 4) * 4
    } else {
        hdr.width * 4
    };
    let needed = checked_needed(hdr.pixel_data_offset, stride, hdr.height)?;
    if bytes.len() < needed {
        return Err(truncated(format!(
            "pixel data truncated (BI_BITFIELDS {}-bit)",
            bits_per_pixel
        )));
    }

    let mut pixels = vec![0u8; hdr.width * hdr.height * pixel_bytes];
    for src_row in 0..hdr.height {
        let dst_row = if hdr.top_down {
            src_row
        } else {
            hdr.height - 1 - src_row
        };
        for x in 0..hdr.width {
            let src_idx = hdr.pixel_data_offset + src_row * stride;
            let pixel: u64 = if bits_per_pixel == 16 {
                let off = src_idx + x * 2;
                u16::from_le_bytes(bytes[off..off + 2].try_into().unwrap()) as u64
            } else {
                let off = src_idx + x * 4;
                u32::from_le_bytes(bytes[off..off + 4].try_into().unwrap()) as u64
            };

            let masked_r = (pixel & (hdr.red_mask as u64)) >> red_shift;
            let masked_g = (pixel & (hdr.green_mask as u64)) >> green_shift;
            let masked_b = (pixel & (hdr.blue_mask as u64)) >> blue_shift;
            let r = (masked_r * 255 / red_range) as u8;
            let g = (masked_g * 255 / green_range) as u8;
            let b = (masked_b * 255 / blue_range) as u8;
            let dst_idx = (dst_row * hdr.width + x) * pixel_bytes;
            pixels[dst_idx] = r;
            pixels[dst_idx + 1] = g;
            pixels[dst_idx + 2] = b;
            if has_alpha {
                let masked_a = (pixel & (hdr.alpha_mask as u64)) >> alpha_shift;
                pixels[dst_idx + 3] = (masked_a * 255 / alpha_range) as u8;
            }
        }
    }
    Ok(DecodedImage {
        width: hdr.width,
        height: hdr.height,
        data: pixels,
        channels: ch,
        format: Format::Bmp,
    })
}

fn decode_rle(bytes: &[u8], hdr: &BmpHeader, bits_per_pixel: u32) -> Result<DecodedImage, DecodeError> {
    let entry_count = if hdr.clr_used > 0 {
        hdr.clr_used
    } else if bits_per_pixel == 8 {
        256
    } else {
        16
    };
    let palette = read_color_table(bytes, hdr, entry_count)?;

    let xsize = hdr.width;
    let ysize = hdr.height;

    // Replicate Pillow's BmpRleDecoder exactly.
    // Pillow accumulates pixel indices into a flat buffer in file-scanline order,
    // then calls set_as_raw with direction=-1 (bottom-up) or +1 (top-down).
    let total = xsize * ysize;
    let mut data_buf: Vec<usize> = Vec::with_capacity(total);
    let mut x: usize = 0;
    let mut pos = hdr.pixel_data_offset;
    let end = bytes.len();

    'outer: while data_buf.len() < total {
        if pos + 1 >= end {
            break;
        }
        let num_pixels = bytes[pos] as usize;
        pos += 1;
        let data_byte = bytes[pos] as usize;
        pos += 1;

        if num_pixels != 0 {
            // Encoded mode: clip at end of row (Pillow behavior)
            let clipped = if x + num_pixels > xsize {
                if xsize > x { xsize - x } else { 0 }
            } else {
                num_pixels
            };
            if bits_per_pixel == 8 {
                for _ in 0..clipped {
                    data_buf.push(data_byte);
                }
            } else {
                // RLE4: alternating high/low nibble
                for i in 0..clipped {
                    if i % 2 == 0 {
                        data_buf.push((data_byte >> 4) & 0xF);
                    } else {
                        data_buf.push(data_byte & 0xF);
                    }
                }
            }
            x += clipped;
        } else {
            match data_byte {
                0 => {
                    // EOL: pad with zeros to next row boundary (Pillow behavior)
                    while data_buf.len() % xsize != 0 {
                        data_buf.push(0);
                    }
                    x = 0;
                }
                1 => {
                    // End of bitmap
                    break 'outer;
                }
                2 => {
                    // Delta: Pillow reads 4 bytes (first 2 discarded, second 2 are dx/dy).
                    // This is a Pillow bug we must replicate.
                    if pos + 3 >= end {
                        break;
                    }
                    pos += 2; // skip first 2 bytes (discarded in Pillow)
                    let right = bytes[pos] as usize;
                    pos += 1;
                    let up = bytes[pos] as usize;
                    pos += 1;
                    let zeros = right + up * xsize;
                    for _ in 0..zeros {
                        data_buf.push(0);
                    }
                    x = data_buf.len() % xsize;
                }
                num_abs => {
                    // Absolute mode: num_abs >= 3 pixels follow
                    let byte_count = if bits_per_pixel == 8 {
                        num_abs
                    } else {
                        // RLE4: Pillow uses floor division (byte[0] // 2), NOT ceil
                        num_abs / 2
                    };
                    if pos + byte_count > end {
                        break;
                    }
                    if bits_per_pixel == 8 {
                        for i in 0..byte_count {
                            data_buf.push(bytes[pos + i] as usize);
                        }
                    } else {
                        // RLE4: emit both nibbles of each byte read
                        for i in 0..byte_count {
                            let bv = bytes[pos + i] as usize;
                            data_buf.push((bv >> 4) & 0xF);
                            data_buf.push(bv & 0xF);
                        }
                    }
                    x += num_abs;
                    pos += byte_count;
                    // Word-align: check if (pos - hdr.pixel_data_offset) % 2 != 0
                    if (pos - hdr.pixel_data_offset) % 2 != 0 {
                        pos += 1; // skip padding byte
                    }
                }
            }
        }
    }

    // Detect RLE overrun: if loop exited before buffer is full, the stream is corrupt.
    if data_buf.len() < total {
        return Err(corrupt(format!(
            "RLE stream ended with {} pixels, expected {}",
            data_buf.len(),
            total
        )));
    }

    // Build output pixels.
    // Pillow's set_as_raw with direction=-1 reverses rows:
    // image row i = buffer row (ysize - 1 - i) for bottom-up.
    // For top-down (direction=+1), image row i = buffer row i.
    let mut pixels = vec![0u8; xsize * ysize * 3];
    for buf_row in 0..ysize {
        let img_row = if hdr.top_down {
            buf_row
        } else {
            ysize - 1 - buf_row
        };
        for col in 0..xsize {
            let mut pal_idx = data_buf[buf_row * xsize + col];
            if pal_idx >= entry_count {
                pal_idx = entry_count - 1;
            }
            let rgb = &palette[pal_idx];
            let dst_idx = (img_row * xsize + col) * 3;
            pixels[dst_idx] = rgb[0]; // R
            pixels[dst_idx + 1] = rgb[1]; // G
            pixels[dst_idx + 2] = rgb[2]; // B
        }
    }

    Ok(DecodedImage {
        width: hdr.width,
        height: hdr.height,
        data: pixels,
        channels: Channels::Rgb,
        format: Format::Bmp,
    })
}

fn corrupt(detail: impl Into<String>) -> DecodeError {
    DecodeError::new(DecodeErrorKind::CorruptInput, Some(Format::Bmp), detail)
}

fn truncated(detail: impl Into<String>) -> DecodeError {
    DecodeError::new(DecodeErrorKind::Truncated, Some(Format::Bmp), detail)
}

fn unsupported_feature(detail: impl Into<String>) -> DecodeError {
    DecodeError::new(
        DecodeErrorKind::UnsupportedFeature,
        Some(Format::Bmp),
        detail,
    )
}
