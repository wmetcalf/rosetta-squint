package io.github.wmetcalf.rosettasquint.decode.internal.libheif;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;

/**
 * JNA interface to libheif.so.1 (libheif 1.17.x).
 *
 * Only the minimal subset required for decoding HEIC images is mapped.
 * All enum values are passed/received as plain ints.
 */
public interface LibHeif extends Library {

    LibHeif INSTANCE = Native.load("heif", LibHeif.class);

    // heif_colorspace
    int heif_colorspace_RGB = 1;

    // heif_chroma
    int heif_chroma_interleaved_RGB  = 10;
    int heif_chroma_interleaved_RGBA = 11;

    // heif_channel
    int heif_channel_interleaved = 10;

    // --- context lifecycle ---

    Pointer heif_context_alloc();

    void heif_context_free(Pointer ctx);

    /** Copies the memory before parsing; safe to call even if the Java byte[] is GC'd. */
    HeifError.ByValue heif_context_read_from_memory(
            Pointer ctx,
            byte[] mem,
            long size,
            Pointer options);   // heif_reading_options* — pass null for defaults

    /** No-copy variant — caller must keep mem alive for the context's lifetime. */
    HeifError.ByValue heif_context_read_from_memory_without_copy(
            Pointer ctx,
            byte[] mem,
            long size,
            Pointer options);   // heif_reading_options* — pass null for defaults

    // --- image handle ---

    HeifError.ByValue heif_context_get_primary_image_handle(
            Pointer ctx,
            PointerByReference out_handle);

    void heif_image_handle_release(Pointer handle);

    int heif_image_handle_get_width(Pointer handle);

    int heif_image_handle_get_height(Pointer handle);

    /** Returns non-zero if the image has an alpha channel. */
    int heif_image_handle_has_alpha_channel(Pointer handle);

    // --- decoding ---

    HeifError.ByValue heif_decode_image(
            Pointer handle,
            PointerByReference out_img,
            int colorspace,   // heif_colorspace
            int chroma,       // heif_chroma
            Pointer options); // heif_decoding_options* — pass null for defaults

    void heif_image_release(Pointer img);

    /**
     * Returns a pointer to the pixel data for a plane and sets *out_stride.
     * Returns NULL for a non-existing channel.
     */
    Pointer heif_image_get_plane_readonly(
            Pointer img,
            int channel,          // heif_channel
            IntByReference out_stride);
}
