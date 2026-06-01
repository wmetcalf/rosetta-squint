package io.github.wmetcalf.rosettasquint.decode.internal;

import io.github.wmetcalf.rosettasquint.decode.Channels;
import io.github.wmetcalf.rosettasquint.decode.DecodeException;
import io.github.wmetcalf.rosettasquint.decode.DecodedImage;
import io.github.wmetcalf.rosettasquint.decode.Format;

/**
 * Decodes HEIC via the shared libheif+libde265 WASM module (decode/wasm/), run
 * in Chicory — no JNA, no system libheif. The same WASM artifact is shared by
 * every port so HEIC output is byte-identical across languages. See
 * decode/wasm/ and decode/spec/HEIC_REPRODUCIBILITY.md.
 */
public final class HEICDecoder {

    private HEICDecoder() {}

    public static DecodedImage decode(byte[] bytes) throws DecodeException {
        try {
            HeicWasm.Result res = HeicWasm.decode(bytes, Limits.MAX_PIXELS);
            Channels channels = res.channels == 4 ? Channels.RGBA : Channels.RGB;
            return new DecodedImage(res.width, res.height, res.data, channels, Format.HEIC);
        } catch (HeicWasm.TooLargeException e) {
            // Produce the canonical imageTooLarge DecodeException (always throws).
            Limits.checkDimensions(e.width, e.height, Format.HEIC);
            throw new DecodeException(DecodeException.Kind.IMAGE_TOO_LARGE, Format.HEIC,
                    "HEIC too large: " + e.width + "x" + e.height);
        } catch (HeicWasm.CorruptException e) {
            throw new DecodeException(DecodeException.Kind.CORRUPT_INPUT, Format.HEIC, e.getMessage());
        }
    }
}
