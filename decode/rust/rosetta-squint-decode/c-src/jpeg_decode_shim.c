/*
 * jpeg_decode_shim.c — libjpeg-turbo decode wrapper using the canonical
 * setjmp/longjmp error-recovery pattern.
 *
 * Why: Rust's panic-based error_exit (panic! + catch_unwind) doesn't survive
 * panic=abort builds (which cargo-fuzz uses) — libFuzzer's deadly-signal
 * handler intercepts SIGABRT before catch_unwind can fire. The fix is to
 * stay entirely in C-land for error recovery: error_exit calls longjmp,
 * Rust never sees a Rust panic from this code path.
 *
 * Surface (called from src/jpeg.rs via FFI):
 *
 *   int rid_decode_jpeg(
 *       const unsigned char *bytes, size_t len,
 *       size_t max_pixels,
 *       unsigned int *out_width, unsigned int *out_height,
 *       unsigned char **out_buf
 *   );
 *
 * Return codes:
 *      0  success - *out_buf is a malloc'd width x height x 3 RGB buffer
 *                   (free via rid_free_buf); *out_width and *out_height populated
 *     -1  CMYK / YCCK color space (UnsupportedFeature)
 *     -2  image_width*image_height > max_pixels (ImageTooLarge)
 *     -3  unexpected output_components != 3
 *     -4  out of memory
 *     -5  jpeg_read_header returned non-OK
 *   1000+ libjpeg fatal error; (rc - 1000) is the libjpeg msg_code
 *         (CorruptInput / Truncated)
 *
 * On any non-zero return, *out_buf is NULL.
 */

#include <stdint.h>
#include <stdio.h>      /* jpeglib.h references FILE; must come before jpeglib.h */
#include <stdlib.h>
#include <string.h>
#include <setjmp.h>
#include <jpeglib.h>

#define RID_OK 0
#define RID_ERR_CMYK -1
#define RID_ERR_TOO_LARGE -2
#define RID_ERR_UNEXPECTED_COMPONENTS -3
#define RID_ERR_ALLOC -4
#define RID_ERR_BAD_HEADER -5
#define RID_ERR_LIBJPEG_BASE 1000

struct rid_error_mgr {
    struct jpeg_error_mgr pub_mgr;
    jmp_buf setjmp_buf;
    int err_code;
};

/*
 * Custom error_exit installed on jpeg_error_mgr. Called by libjpeg on
 * fatal errors (truncated input, invalid markers, etc.). Stashes the
 * msg_code into the error mgr and longjmps back to the setjmp point
 * in rid_decode_jpeg.
 */
static void rid_error_exit(j_common_ptr cinfo) {
    struct rid_error_mgr *err = (struct rid_error_mgr *) cinfo->err;
    err->err_code = err->pub_mgr.msg_code;
    longjmp(err->setjmp_buf, 1);
}

/*
 * Suppress libjpeg's verbose log output ("Premature end of JPEG file" etc.)
 * by installing a no-op output_message handler. We surface error info via
 * the return code instead.
 */
static void rid_output_message(j_common_ptr cinfo) {
    (void) cinfo;
}

int rid_decode_jpeg(
    const unsigned char *bytes, size_t len,
    size_t max_pixels,
    unsigned int *out_width, unsigned int *out_height,
    unsigned char **out_buf
) {
    struct jpeg_decompress_struct cinfo;
    struct rid_error_mgr jerr;
    unsigned char *buf = NULL;

    *out_buf = NULL;
    *out_width = 0;
    *out_height = 0;

    cinfo.err = jpeg_std_error(&jerr.pub_mgr);
    jerr.pub_mgr.error_exit = rid_error_exit;
    jerr.pub_mgr.output_message = rid_output_message;
    jerr.err_code = 0;

    if (setjmp(jerr.setjmp_buf)) {
        /* longjmp came back here from rid_error_exit. The decompress struct
         * may be in any state — destroy is safe even on a half-initialized
         * struct (jpeg_destroy_decompress is documented to be idempotent). */
        jpeg_destroy_decompress(&cinfo);
        if (buf) free(buf);
        return RID_ERR_LIBJPEG_BASE + jerr.err_code;
    }

    jpeg_create_decompress(&cinfo);
    jpeg_mem_src(&cinfo, bytes, len);

    if (jpeg_read_header(&cinfo, TRUE) != JPEG_HEADER_OK) {
        jpeg_destroy_decompress(&cinfo);
        return RID_ERR_BAD_HEADER;
    }

    /* Reject CMYK/YCCK before allocating any output. The original
     * panic-based code did the same; we just return an error code now. */
    if (cinfo.jpeg_color_space == JCS_CMYK ||
        cinfo.jpeg_color_space == JCS_YCCK) {
        jpeg_destroy_decompress(&cinfo);
        return RID_ERR_CMYK;
    }

    /* Enforce MAX_PIXELS before any size-proportional allocation.
     * image_width / image_height are populated by jpeg_read_header. */
    size_t pixels = (size_t)cinfo.image_width * (size_t)cinfo.image_height;
    if (pixels > max_pixels) {
        jpeg_destroy_decompress(&cinfo);
        return RID_ERR_TOO_LARGE;
    }

    cinfo.out_color_space = JCS_RGB;
    cinfo.dct_method = JDCT_ISLOW;
    cinfo.do_fancy_upsampling = TRUE;

    jpeg_start_decompress(&cinfo);

    if (cinfo.output_components != 3) {
        jpeg_destroy_decompress(&cinfo);
        return RID_ERR_UNEXPECTED_COMPONENTS;
    }

    size_t row_stride = (size_t)cinfo.output_width * 3;
    size_t total_size = row_stride * (size_t)cinfo.output_height;
    buf = (unsigned char *)malloc(total_size);
    if (!buf) {
        jpeg_destroy_decompress(&cinfo);
        return RID_ERR_ALLOC;
    }

    while (cinfo.output_scanline < cinfo.output_height) {
        unsigned char *row = buf + (size_t)cinfo.output_scanline * row_stride;
        unsigned char *row_ptrs[1] = { row };
        jpeg_read_scanlines(&cinfo, row_ptrs, 1);
    }

    jpeg_finish_decompress(&cinfo);
    *out_width = cinfo.output_width;
    *out_height = cinfo.output_height;
    *out_buf = buf;
    jpeg_destroy_decompress(&cinfo);
    return RID_OK;
}

void rid_free_buf(unsigned char *buf) {
    if (buf) free(buf);
}
