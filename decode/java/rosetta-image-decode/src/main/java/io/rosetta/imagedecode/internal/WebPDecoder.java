package io.rosetta.imagedecode.internal;

import com.sun.jna.Pointer;
import io.rosetta.imagedecode.Channels;
import io.rosetta.imagedecode.DecodeException;
import io.rosetta.imagedecode.DecodedImage;
import io.rosetta.imagedecode.Format;
import io.rosetta.imagedecode.internal.libwebp.LibWebP;
import io.rosetta.imagedecode.internal.libwebp.WebPBitstreamFeatures;

/**
 * Decodes WebP images via a JNA wrapper around system libwebp.
 *
 * Uses the {@code *Into} decode variants (caller-supplied output buffer) so
 * no {@code WebPFree} is required.
 */
public final class WebPDecoder {

    private WebPDecoder() {}

    public static DecodedImage decode(byte[] bytes) throws DecodeException {
        LibWebP lib = LibWebP.INSTANCE;

        // 1. Read dimensions and alpha flag from the bitstream header.
        WebPBitstreamFeatures features = new WebPBitstreamFeatures();
        int status = lib.WebPGetFeaturesInternal(
                bytes, bytes.length, features, LibWebP.WEBP_DECODER_ABI_VERSION);
        if (status != 0) {
            throw new DecodeException(DecodeException.Kind.CORRUPT_INPUT, Format.WEBP,
                    "WebPGetFeaturesInternal returned VP8StatusCode " + status);
        }

        int width  = features.width;
        int height = features.height;
        boolean hasAlpha = features.has_alpha != 0;

        // 2. Validate dimensions before allocating the output buffer.
        Limits.checkDimensions(width, height, Format.WEBP);

        Channels channels = hasAlpha ? Channels.RGBA : Channels.RGB;
        int bpp = channels.bytesPerPixel();
        int stride = width * bpp;

        long outSize = Math.multiplyExact(Math.multiplyExact((long) width, (long) height), (long) bpp);
        if (outSize > Integer.MAX_VALUE) {
            throw new DecodeException(DecodeException.Kind.IMAGE_TOO_LARGE, Format.WEBP,
                    "pixel buffer size " + outSize + " exceeds Java int max");
        }

        // 3. Allocate output buffer and decode directly into it.
        byte[] out = new byte[(int) outSize];

        Pointer result;
        if (hasAlpha) {
            result = lib.WebPDecodeRGBAInto(bytes, bytes.length, out, out.length, stride);
        } else {
            result = lib.WebPDecodeRGBInto(bytes, bytes.length, out, out.length, stride);
        }

        if (result == null) {
            throw new DecodeException(DecodeException.Kind.CORRUPT_INPUT, Format.WEBP,
                    "WebPDecode" + (hasAlpha ? "RGBA" : "RGB") + "Into returned null");
        }

        return new DecodedImage(width, height, out, channels, Format.WEBP);
    }
}
