//! JPEG decoder via the in-tree C shim (`c-src/jpeg_decode_shim.c`),
//! which uses libjpeg-turbo with the canonical `setjmp`/`longjmp`
//! error-recovery pattern.
//!
//! ## Why a C shim and not direct FFI?
//!
//! libjpeg's error model: install an `error_exit` callback; on fatal errors
//! libjpeg invokes it; the callback is expected to call `longjmp` back to
//! a `setjmp` point in the caller, which then returns an error.
//!
//! `longjmp` skips Rust destructors — using it through Rust frames is UB.
//! Earlier versions of this file used a `panic!()` + `catch_unwind`
//! workaround. That works under `cargo test` (panic=unwind) but **breaks
//! under `panic=abort` builds** like cargo-fuzz, where panics SIGABRT and
//! libFuzzer's signal handler intercepts before `catch_unwind` fires.
//!
//! The fix: keep `setjmp`/`longjmp` entirely in C. The shim does the full
//! decode pipeline in one C call; Rust just gets a return code + buffer.
//! Real bug found by fuzzing: `[0xFF, 0xD8]` (bare SOI marker) used to
//! crash the process under panic=abort; now returns `CorruptInput` properly
//! in any build profile.
//!
//! Configures `out_color_space = JCS_RGB`, `dct_method = JDCT_ISLOW` (matches
//! PIL's default; ensures cross-architecture pixel parity). CMYK/YCCK input
//! → `UnsupportedFeature`.

use crate::error::{DecodeError, DecodeErrorKind};
use crate::limits::MAX_PIXELS;
use crate::types::{Channels, DecodedImage, Format};

use std::os::raw::{c_int, c_uchar};

// Force the linker to pull in mozjpeg-sys's bundled libjpeg-turbo even though
// this module no longer references any of its symbols directly — our C shim
// in c-src/jpeg_decode_shim.c calls jpeg_create_decompress / jpeg_read_header
// / etc., and without a Rust-side reference, the linker drops mozjpeg-sys's
// static library and the shim's libjpeg calls go unresolved.
#[allow(unused_imports)]
use mozjpeg_sys::jpeg_std_error as _force_libjpeg_link;

// Return codes from the C shim. Must stay in sync with c-src/jpeg_decode_shim.c.
const RID_OK: c_int = 0;
const RID_ERR_CMYK: c_int = -1;
const RID_ERR_TOO_LARGE: c_int = -2;
const RID_ERR_UNEXPECTED_COMPONENTS: c_int = -3;
const RID_ERR_ALLOC: c_int = -4;
const RID_ERR_BAD_HEADER: c_int = -5;
const RID_ERR_LIBJPEG_BASE: c_int = 1000;

extern "C" {
    fn rid_decode_jpeg(
        bytes: *const c_uchar,
        len: usize,
        max_pixels: usize,
        out_width: *mut u32,
        out_height: *mut u32,
        out_buf: *mut *mut c_uchar,
    ) -> c_int;

    fn rid_free_buf(buf: *mut c_uchar);
}

pub(crate) fn decode_jpeg(bytes: &[u8]) -> Result<DecodedImage, DecodeError> {
    if bytes.is_empty() {
        return Err(DecodeError::new(
            DecodeErrorKind::CorruptInput,
            Some(Format::Jpeg),
            "empty input",
        ));
    }

    let mut width: u32 = 0;
    let mut height: u32 = 0;
    let mut buf: *mut c_uchar = std::ptr::null_mut();

    // SAFETY: rid_decode_jpeg never reads past bytes.as_ptr() + bytes.len()
    // (libjpeg's mem source is bounded by the len argument), and it writes
    // to width/height/buf only. On success, *buf is a malloc'd buffer we own
    // and free via rid_free_buf. On failure, *buf is NULL and we don't deref.
    let rc = unsafe {
        rid_decode_jpeg(
            bytes.as_ptr(),
            bytes.len(),
            MAX_PIXELS,
            &mut width,
            &mut height,
            &mut buf,
        )
    };

    if rc != RID_OK {
        debug_assert!(buf.is_null());
        return Err(map_shim_error(rc));
    }

    let width = width as usize;
    let height = height as usize;
    let size = width * height * 3;

    let mut data = vec![0u8; size];
    unsafe {
        std::ptr::copy_nonoverlapping(buf, data.as_mut_ptr(), size);
        rid_free_buf(buf);
    }

    Ok(DecodedImage {
        width,
        height,
        data,
        channels: Channels::Rgb,
        format: Format::Jpeg,
    })
}

fn map_shim_error(rc: c_int) -> DecodeError {
    match rc {
        RID_ERR_CMYK => DecodeError::new(
            DecodeErrorKind::UnsupportedFeature,
            Some(Format::Jpeg),
            "CMYK color space",
        ),
        RID_ERR_TOO_LARGE => DecodeError::new(
            DecodeErrorKind::ImageTooLarge,
            Some(Format::Jpeg),
            format!("declared dimensions exceed MAX_PIXELS = {}", MAX_PIXELS),
        ),
        RID_ERR_UNEXPECTED_COMPONENTS => DecodeError::new(
            DecodeErrorKind::CorruptInput,
            Some(Format::Jpeg),
            "unexpected output_components (libjpeg returned non-RGB)",
        ),
        RID_ERR_ALLOC => DecodeError::new(
            DecodeErrorKind::CorruptInput,
            Some(Format::Jpeg),
            "out of memory allocating output buffer",
        ),
        RID_ERR_BAD_HEADER => DecodeError::new(
            DecodeErrorKind::CorruptInput,
            Some(Format::Jpeg),
            "jpeg_read_header returned non-OK (corrupt header)",
        ),
        x if x >= RID_ERR_LIBJPEG_BASE => DecodeError::new(
            DecodeErrorKind::CorruptInput,
            Some(Format::Jpeg),
            format!("libjpeg fatal error msg_code={}", x - RID_ERR_LIBJPEG_BASE),
        ),
        _ => DecodeError::new(
            DecodeErrorKind::CorruptInput,
            Some(Format::Jpeg),
            format!("unknown jpeg shim error code: {}", rc),
        ),
    }
}
