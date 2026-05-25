//! Pre-decode dimension sniffers for formats whose native library refuses
//! dimensions that exceed its own internal limits before our `MAX_PIXELS`
//! check has a chance to run.
//!
//! Concretely:
//! - libwebp's `WebPGetFeatures` returns `VP8_STATUS_BITSTREAM_ERROR` for
//!   VP8X-declared canvas dimensions that fail libwebp's internal validation.
//! - libheif returns dimensions from the underlying HEVC bitstream rather
//!   than the container's ispe box, so even a patched ispe is ignored.
//!
//! By peeking at the file's declared dimensions ourselves before invoking
//! the native library, we ensure that "header says it's too large" produces
//! a clean `imageTooLarge` error instead of `corruptInput`. Spec §3.1
//! requires this ordering.

/// Sniff WebP canvas dimensions from the VP8X chunk header.
///
/// VP8X canvas dims live as 24-bit ``width - 1`` and ``height - 1``
/// little-endian fields at offsets 24 and 27 from the start of the file.
/// Returns ``None`` for non-VP8X WebPs (VP8/VP8L); those rarely exceed
/// libwebp's 14-bit per-side limit so the existing post-`WebPGetFeatures`
/// check covers them.
pub(crate) fn sniff_webp_dimensions(bytes: &[u8]) -> Option<(usize, usize)> {
    if bytes.len() < 30 { return None; }
    if &bytes[..4] != b"RIFF" || &bytes[8..12] != b"WEBP" {
        return None;
    }
    if &bytes[12..16] != b"VP8X" {
        return None;
    }
    let w_minus1 = bytes[24] as u32
        | ((bytes[25] as u32) << 8)
        | ((bytes[26] as u32) << 16);
    let h_minus1 = bytes[27] as u32
        | ((bytes[28] as u32) << 8)
        | ((bytes[29] as u32) << 16);
    Some(((w_minus1 + 1) as usize, (h_minus1 + 1) as usize))
}

/// Sniff HEIC primary-image dimensions from the first ``ispe`` (Image Spatial
/// Extents) box.
///
/// HEIF/HEIC is an ISO Base Media File Format container; ``ispe`` payload is
/// fixed: 4 bytes version+flags, then 4 bytes width and 4 bytes height as
/// big-endian u32. The box lives inside ``meta`` → ``iprp`` → ``ipco`` →
/// ``ispe``. We scan the byte stream for the literal "ispe" fourcc and read
/// the 8 bytes of dimensions that follow the version word — same approach
/// as the JS dimension sniffer. Capped at 1 MiB of prefix to keep the scan
/// bounded for adversarial inputs.
pub(crate) fn sniff_heic_dimensions(bytes: &[u8]) -> Option<(usize, usize)> {
    if bytes.len() < 30 { return None; }
    let scan_limit = bytes.len().saturating_sub(16).min(1024 * 1024);
    for i in 0..scan_limit {
        if &bytes[i..i + 4] == b"ispe" {
            let w_off = i + 4 + 4; // skip "ispe" type + version+flags
            if w_off + 8 > bytes.len() { return None; }
            let width = u32::from_be_bytes([
                bytes[w_off], bytes[w_off + 1], bytes[w_off + 2], bytes[w_off + 3],
            ]);
            let height = u32::from_be_bytes([
                bytes[w_off + 4], bytes[w_off + 5], bytes[w_off + 6], bytes[w_off + 7],
            ]);
            if width == 0 || height == 0 { return None; }
            return Some((width as usize, height as usize));
        }
    }
    None
}
