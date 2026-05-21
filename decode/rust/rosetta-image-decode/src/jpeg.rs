//! JPEG decoder via mozjpeg-sys (libjpeg-turbo decode path).
//!
//! Configures dct_method=JDCT_ISLOW and out_color_space=JCS_RGB to match PIL's default.
//! Always outputs RGB (3 channels). CMYK/YCCK input → UnsupportedFeature.
//!
//! Error handling: installs a custom error_exit callback that panics; the outer
//! decode_jpeg wraps everything in catch_unwind so libjpeg fatal errors become
//! CorruptInput rather than process abort.

use crate::error::{DecodeError, DecodeErrorKind};
use crate::types::{Channels, DecodedImage, Format};

use mozjpeg_sys::*;
use std::mem;
use std::os::raw::c_int;
use std::panic;

/// Custom error_exit installed on the jpeg_error_mgr.
/// Called by libjpeg on fatal errors instead of exit().
/// Since the ABI is "C-unwind", a Rust panic propagates back through libjpeg.
unsafe extern "C-unwind" fn jpeg_error_exit_panic(cinfo: &mut jpeg_common_struct) {
    // Format the error message into a short string before panicking
    let err = &mut *cinfo.err;
    // msg_code 0 means "no message" — just panic with a generic message
    panic!("libjpeg fatal error (msg_code={})", err.msg_code);
}

pub(crate) fn decode_jpeg(bytes: &[u8]) -> Result<DecodedImage, DecodeError> {
    // Wrap everything in catch_unwind so that jpeg_error_exit_panic above
    // converts libjpeg fatal errors into a Rust Result rather than aborting.
    let result = panic::catch_unwind(|| decode_jpeg_inner(bytes));
    match result {
        Ok(inner) => inner,
        Err(_panic_payload) => Err(DecodeError::new(
            DecodeErrorKind::CorruptInput,
            Some(Format::Jpeg),
            "libjpeg fatal error",
        )),
    }
}

fn decode_jpeg_inner(bytes: &[u8]) -> Result<DecodedImage, DecodeError> {
    unsafe {
        let mut cinfo: jpeg_decompress_struct = mem::zeroed();
        let mut err: jpeg_error_mgr = mem::zeroed();
        cinfo.common.err = jpeg_std_error(&mut err);

        // Replace the default error_exit (which calls exit()) with our panic-based one
        (*cinfo.common.err).error_exit = Some(jpeg_error_exit_panic);

        jpeg_create_decompress(&mut cinfo);
        jpeg_mem_src(&mut cinfo, bytes.as_ptr(), bytes.len() as _);

        // JPEG_HEADER_OK == 1 (from jpeglib.h; not exported as a Rust constant)
        let header_ret = jpeg_read_header(&mut cinfo, true as boolean);
        if header_ret != 1 as c_int {
            jpeg_destroy_decompress(&mut cinfo);
            return Err(DecodeError::new(
                DecodeErrorKind::CorruptInput,
                Some(Format::Jpeg),
                format!("jpeg_read_header returned {}", header_ret),
            ));
        }

        // Reject CMYK / YCCK before allocating output buffer
        let cs = cinfo.jpeg_color_space;
        if cs == J_COLOR_SPACE::JCS_CMYK || cs == J_COLOR_SPACE::JCS_YCCK {
            jpeg_destroy_decompress(&mut cinfo);
            return Err(DecodeError::new(
                DecodeErrorKind::UnsupportedFeature,
                Some(Format::Jpeg),
                "CMYK color space",
            ));
        }

        cinfo.out_color_space = J_COLOR_SPACE::JCS_RGB;
        cinfo.dct_method = J_DCT_METHOD::JDCT_ISLOW;
        // Fancy upsampling is the default; explicitly set for clarity
        cinfo.do_fancy_upsampling = true as boolean;

        jpeg_start_decompress(&mut cinfo);

        let width = cinfo.output_width as usize;
        let height = cinfo.output_height as usize;
        let components = cinfo.output_components as usize;

        if components != 3 {
            jpeg_destroy_decompress(&mut cinfo);
            return Err(DecodeError::new(
                DecodeErrorKind::CorruptInput,
                Some(Format::Jpeg),
                format!("unexpected output_components: {}", components),
            ));
        }

        let row_stride = width * components;
        let mut data = vec![0u8; height * row_stride];

        while cinfo.output_scanline < cinfo.output_height {
            let offset = (cinfo.output_scanline as usize) * row_stride;
            let row_ptr = data[offset..].as_mut_ptr();
            let mut row_ptrs: [*mut u8; 1] = [row_ptr];
            jpeg_read_scanlines(&mut cinfo, row_ptrs.as_mut_ptr(), 1);
        }

        jpeg_finish_decompress(&mut cinfo);
        jpeg_destroy_decompress(&mut cinfo);

        Ok(DecodedImage {
            width,
            height,
            data,
            channels: Channels::Rgb,
            format: Format::Jpeg,
        })
    }
}
