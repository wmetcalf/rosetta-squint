//! WebP decoder via libwebp FFI (libwebp-sys2).
//!
//! Uses WebPGetFeatures to probe alpha/dimensions, then dispatches to
//! WebPDecodeRGBA (4ch) or WebPDecodeRGB (3ch) matching PIL behaviour.
//! Decoded memory is freed via WebPFree (requires the "0_5" feature).

use crate::error::{DecodeError, DecodeErrorKind};
use crate::limits::check_dimensions;
use crate::types::{Channels, DecodedImage, Format};

use libwebp_sys::{
    WebPBitstreamFeatures, WebPDecodeRGB, WebPDecodeRGBA, WebPFree, WebPGetFeatures,
    VP8_STATUS_OK,
};
use std::os::raw::c_int;

pub(crate) fn decode_webp(bytes: &[u8]) -> Result<DecodedImage, DecodeError> {
    unsafe {
        // --- probe bitstream features (width, height, has_alpha) ---
        let mut features: WebPBitstreamFeatures = std::mem::zeroed();
        let status = WebPGetFeatures(bytes.as_ptr(), bytes.len(), &mut features);
        if status != VP8_STATUS_OK {
            return Err(DecodeError::new(
                DecodeErrorKind::CorruptInput,
                Some(Format::Webp),
                format!("WebPGetFeatures status={}", status),
            ));
        }

        let w = features.width as usize;
        let h = features.height as usize;
        let has_alpha = features.has_alpha != 0;

        check_dimensions(w, h, Format::Webp)?;

        if has_alpha {
            let mut width: c_int = 0;
            let mut height: c_int = 0;
            let out_ptr = WebPDecodeRGBA(bytes.as_ptr(), bytes.len(), &mut width, &mut height);
            if out_ptr.is_null() {
                return Err(DecodeError::new(
                    DecodeErrorKind::CorruptInput,
                    Some(Format::Webp),
                    "WebPDecodeRGBA returned null",
                ));
            }
            let data = std::slice::from_raw_parts(out_ptr, w * h * 4).to_vec();
            WebPFree(out_ptr as *mut _);
            Ok(DecodedImage {
                width: w,
                height: h,
                data,
                channels: Channels::Rgba,
                format: Format::Webp,
            })
        } else {
            let mut width: c_int = 0;
            let mut height: c_int = 0;
            let out_ptr = WebPDecodeRGB(bytes.as_ptr(), bytes.len(), &mut width, &mut height);
            if out_ptr.is_null() {
                return Err(DecodeError::new(
                    DecodeErrorKind::CorruptInput,
                    Some(Format::Webp),
                    "WebPDecodeRGB returned null",
                ));
            }
            let data = std::slice::from_raw_parts(out_ptr, w * h * 3).to_vec();
            WebPFree(out_ptr as *mut _);
            Ok(DecodedImage {
                width: w,
                height: h,
                data,
                channels: Channels::Rgb,
                format: Format::Webp,
            })
        }
    }
}
