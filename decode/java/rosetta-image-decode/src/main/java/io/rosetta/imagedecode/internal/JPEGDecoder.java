package io.rosetta.imagedecode.internal;

import io.rosetta.imagedecode.Channels;
import io.rosetta.imagedecode.DecodeException;
import io.rosetta.imagedecode.DecodedImage;
import io.rosetta.imagedecode.Format;

import java.io.IOException;

import org.libjpegturbo.turbojpeg.TJ;
import org.libjpegturbo.turbojpeg.TJDecompressor;
import org.libjpegturbo.turbojpeg.TJException;

public final class JPEGDecoder {
    private JPEGDecoder() {}

    public static DecodedImage decode(byte[] bytes) throws DecodeException {
        TJDecompressor decomp = null;
        try {
            decomp = new TJDecompressor(bytes);
            int width = decomp.getWidth();
            int height = decomp.getHeight();
            int colorspace = decomp.getColorspace();

            if (colorspace == TJ.CS_CMYK || colorspace == TJ.CS_YCCK) {
                throw new DecodeException(DecodeException.Kind.UNSUPPORTED_FEATURE, Format.JPEG, "CMYK color space");
            }

            Limits.checkDimensions(width, height, Format.JPEG);

            // Decompress to RGB. pitch=0 means auto (width * bytesPerPixel).
            // FLAG_ACCURATEDCT = JDCT_ISLOW, matching PIL's default slow-but-accurate IDCT.
            byte[] rgb = decomp.decompress(width, 0, height, TJ.PF_RGB, TJ.FLAG_ACCURATEDCT);
            return new DecodedImage(width, height, rgb, Channels.RGB, Format.JPEG);
        } catch (DecodeException e) {
            throw e;
        } catch (TJException e) {
            // TJException wraps libjpeg-turbo errors (truncated/corrupt JPEG)
            throw new DecodeException(DecodeException.Kind.CORRUPT_INPUT, Format.JPEG, "TJ: " + e.getMessage());
        } catch (IOException e) {
            // Stream / I/O failure surfacing through TurboJPEG; treat as corrupt input.
            throw new DecodeException(DecodeException.Kind.CORRUPT_INPUT, Format.JPEG, "IO: " + e.getMessage());
        } catch (IllegalStateException e) {
            // TurboJPEG's TJDecompressor.getWidth/getHeight/getColorspace throw
            // IllegalStateException("No JPEG image is associated with this instance")
            // on inputs that pass the magic-byte detect (FF D8) but fail libjpeg-turbo's
            // internal header parse — e.g. a truncated SOI like "FF D8 0A". Without this
            // catch the exception escapes the decoder layer, violating the
            // "any input → DecodedImage or DecodeException" contract. Found via Jazzer.
            throw new DecodeException(DecodeException.Kind.CORRUPT_INPUT, Format.JPEG,
                "TJ header parse failed: " + e.getMessage());
        } finally {
            // Note: other programmer-error throwables (NPE, generic IAE, etc.) are
            // intentionally not caught here — they would mask real bugs by mapping
            // to corruptInput. IllegalStateException is the documented TurboJPEG
            // signal for "no valid JPEG header" and is mapped above.
            if (decomp != null) {
                try { decomp.close(); } catch (Exception ignored) {}
            }
        }
    }
}
