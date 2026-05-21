package io.rosetta.imagedecode.internal;

import io.rosetta.imagedecode.Channels;
import io.rosetta.imagedecode.DecodeException;
import io.rosetta.imagedecode.DecodedImage;
import io.rosetta.imagedecode.Format;

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
        } catch (Exception e) {
            throw new DecodeException(DecodeException.Kind.CORRUPT_INPUT, Format.JPEG, e.getClass().getSimpleName() + ": " + e.getMessage());
        } finally {
            if (decomp != null) {
                try { decomp.close(); } catch (Exception ignored) {}
            }
        }
    }
}
