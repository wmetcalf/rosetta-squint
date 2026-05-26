package io.github.wmetcalf.rosettasquint.decode.internal.libwebp;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;

/**
 * JNA interface to libwebp.so (system libwebp).
 *
 * Only the minimal subset required for decoding WebP images is mapped.
 * All functions use the *Into variants that write into caller-supplied buffers,
 * so no {@code WebPFree} is needed.
 */
public interface LibWebP extends Library {

    LibWebP INSTANCE = Native.load("webp", LibWebP.class);

    /**
     * WEBP_DECODER_ABI_VERSION from {@code <webp/decode.h>}: 0x0209.
     * Passed as the {@code version} argument to {@code WebPGetFeaturesInternal}.
     */
    int WEBP_DECODER_ABI_VERSION = 0x0209;

    // --- feature detection ---

    /**
     * Internal entry point for {@code WebPGetFeatures} (the public macro expands to this).
     * Fills {@code features} with dimension and alpha/animation information.
     *
     * @return 0 (VP8_STATUS_OK) on success, non-zero VP8StatusCode on failure.
     */
    int WebPGetFeaturesInternal(
            byte[] data,
            long data_size,
            WebPBitstreamFeatures features,
            int version);

    // --- decoding into caller-supplied buffers ---

    /**
     * Decodes WebP data into a caller-supplied RGB buffer.
     *
     * @param output_stride bytes per row (typically {@code width * 3}).
     * @return {@code output_buffer} on success, {@code null} on failure.
     */
    Pointer WebPDecodeRGBInto(
            byte[] data,
            long data_size,
            byte[] output_buffer,
            long output_buffer_size,
            int output_stride);

    /**
     * Decodes WebP data into a caller-supplied RGBA buffer.
     *
     * @param output_stride bytes per row (typically {@code width * 4}).
     * @return {@code output_buffer} on success, {@code null} on failure.
     */
    Pointer WebPDecodeRGBAInto(
            byte[] data,
            long data_size,
            byte[] output_buffer,
            long output_buffer_size,
            int output_stride);
}
