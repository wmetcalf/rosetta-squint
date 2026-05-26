use crate::error::{DecodeError, DecodeErrorKind};
use crate::types::Format;

/// Maximum number of pixels (width * height) accepted by any decoder.
/// Images whose declared dimensions exceed this limit are rejected with
/// `DecodeErrorKind::ImageTooLarge` before any size-proportional allocation.
/// 256 MiB / 1 byte-per-channel = 268_435_456 pixels.
pub const MAX_PIXELS: usize = 256 * 1024 * 1024; // 268_435_456

/// Check that `width * height` does not exceed `MAX_PIXELS`.
///
/// Uses `checked_mul` so that on 32-bit targets the multiplication cannot
/// silently overflow; on 64-bit targets the product always fits in `usize` but
/// we still want the cap check.
pub fn check_dimensions(width: usize, height: usize, format: Format) -> Result<(), DecodeError> {
    let pixels = width.checked_mul(height).ok_or_else(|| {
        DecodeError::new(
            DecodeErrorKind::ImageTooLarge,
            Some(format),
            format!("width*height overflow ({}x{})", width, height),
        )
    })?;
    if pixels > MAX_PIXELS {
        return Err(DecodeError::new(
            DecodeErrorKind::ImageTooLarge,
            Some(format),
            format!(
                "declared dimensions {}x{} = {} pixels exceeds MAX_PIXELS = {}",
                width, height, pixels, MAX_PIXELS
            ),
        ));
    }
    Ok(())
}
