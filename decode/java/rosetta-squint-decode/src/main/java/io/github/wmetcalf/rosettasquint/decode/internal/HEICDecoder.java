package io.github.wmetcalf.rosettasquint.decode.internal;

import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import io.github.wmetcalf.rosettasquint.decode.Channels;
import io.github.wmetcalf.rosettasquint.decode.DecodeException;
import io.github.wmetcalf.rosettasquint.decode.DecodedImage;
import io.github.wmetcalf.rosettasquint.decode.Format;
import io.github.wmetcalf.rosettasquint.decode.internal.libheif.HeifError;
import io.github.wmetcalf.rosettasquint.decode.internal.libheif.LibHeif;

/**
 * Decodes HEIC images via a JNA wrapper around system libheif.
 *
 * Resource management: all libheif handles are freed in finally-blocks so
 * that a decoding failure never leaks native memory.
 */
public final class HEICDecoder {

    private HEICDecoder() {}

    public static DecodedImage decode(byte[] bytes) throws DecodeException {
        LibHeif lib = LibHeif.INSTANCE;

        Pointer ctx = lib.heif_context_alloc();
        if (ctx == null) {
            throw new DecodeException(DecodeException.Kind.CORRUPT_INPUT, Format.HEIC,
                    "heif_context_alloc returned null");
        }

        try {
            // Use the copying variant so libheif owns the buffer independently of the
            // Java GC.  The no-copy version would require keeping the byte[] alive for
            // the entire lifetime of the context, which is hard to guarantee under GC.
            HeifError.ByValue err = lib.heif_context_read_from_memory(
                    ctx, bytes, bytes.length, null);
            if (!err.isOk()) {
                throw heifDecodeException(err);
            }

            // Obtain primary image handle.
            PointerByReference handleRef = new PointerByReference();
            err = lib.heif_context_get_primary_image_handle(ctx, handleRef);
            if (!err.isOk()) {
                throw heifDecodeException(err);
            }
            Pointer handle = handleRef.getValue();

            try {
                int width  = lib.heif_image_handle_get_width(handle);
                int height = lib.heif_image_handle_get_height(handle);
                boolean hasAlpha = lib.heif_image_handle_has_alpha_channel(handle) != 0;

                Limits.checkDimensions(width, height, Format.HEIC);

                int chroma = hasAlpha
                        ? LibHeif.heif_chroma_interleaved_RGBA
                        : LibHeif.heif_chroma_interleaved_RGB;
                Channels channels = hasAlpha ? Channels.RGBA : Channels.RGB;

                // Decode to interleaved RGB/RGBA.
                PointerByReference imgRef = new PointerByReference();
                err = lib.heif_decode_image(
                        handle, imgRef,
                        LibHeif.heif_colorspace_RGB,
                        chroma,
                        null);
                if (!err.isOk()) {
                    throw heifDecodeException(err);
                }
                Pointer img = imgRef.getValue();

                try {
                    IntByReference strideRef = new IntByReference();
                    Pointer plane = lib.heif_image_get_plane_readonly(
                            img, LibHeif.heif_channel_interleaved, strideRef);
                    if (plane == null) {
                        throw new DecodeException(DecodeException.Kind.CORRUPT_INPUT, Format.HEIC,
                                "heif_image_get_plane_readonly returned null");
                    }

                    int stride = strideRef.getValue();
                    int bpp = channels.bytesPerPixel();
                    long outSize = Math.multiplyExact(Math.multiplyExact((long) width, (long) height), (long) bpp);
                    if (outSize > Integer.MAX_VALUE) {
                        throw new DecodeException(DecodeException.Kind.IMAGE_TOO_LARGE, Format.HEIC,
                            "pixel buffer size " + outSize + " exceeds Java int max");
                    }
                    byte[] out = new byte[(int) outSize];

                    // Copy pixel rows, stripping any padding the library may add.
                    int rowBytes = width * bpp;
                    for (int y = 0; y < height; y++) {
                        byte[] row = plane.getByteArray((long) y * stride, rowBytes);
                        System.arraycopy(row, 0, out, y * rowBytes, rowBytes);
                    }

                    return new DecodedImage(width, height, out, channels, Format.HEIC);

                } finally {
                    lib.heif_image_release(img);
                }

            } finally {
                lib.heif_image_handle_release(handle);
            }

        } finally {
            lib.heif_context_free(ctx);
        }
    }

    /**
     * Converts a non-OK heif_error into a DecodeException.
     * Error code 3 = heif_error_Unsupported_filetype, but that case is handled
     * by Decoder.java's magic detection before we are called, so here we treat
     * all libheif errors as CORRUPT_INPUT.
     */
    private static DecodeException heifDecodeException(HeifError.ByValue err) {
        String msg = err.messageString();
        return new DecodeException(DecodeException.Kind.CORRUPT_INPUT, Format.HEIC, msg);
    }
}
