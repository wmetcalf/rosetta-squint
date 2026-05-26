package io.github.wmetcalf.rosettasquint.decode.internal;

import io.github.wmetcalf.rosettasquint.decode.Channels;
import io.github.wmetcalf.rosettasquint.decode.DecodeException;
import io.github.wmetcalf.rosettasquint.decode.DecodedImage;
import io.github.wmetcalf.rosettasquint.decode.Format;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.awt.image.Raster;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;

/**
 * Decodes PNG images byte-exactly against PIL's reference output.
 *
 * <p>PIL conversion rules that require special handling in Java:
 *
 * <ol>
 *   <li><b>Grayscale (L, 8-bit)</b>: PIL copies raw gray byte → R=G=B. Java's
 *       {@code BufferedImage.getRGB()} applies sRGB gamma (TYPE_BYTE_GRAY is treated as
 *       linear light). Fix: use {@link Raster#getPixels} to read raw samples.
 *
 *   <li><b>Grayscale (I, 16-bit without alpha)</b>: PIL opens as mode 'I' (32-bit int) and
 *       clips to [0, 255] on {@code convert('RGB')} — so any 16-bit value ≥ 256 maps to 255.
 *       Fix: {@code min(sample, 255)}.
 *
 *   <li><b>Grayscale+alpha (LA, 8-bit)</b>: Same sRGB gamma issue as L. Fix: Raster access.
 *
 *   <li><b>16-bit color or color+alpha (RGB/RGBA, 16-bit)</b>: PIL uses {@code raw >> 8} (top
 *       byte) when loading. Java's {@code getRGB()} uses {@code raw * 255 / 65535} which rounds
 *       differently. Fix: Raster access + {@code >> 8}.
 *
 *   <li><b>16-bit gray+alpha (LA, 16-bit)</b>: PIL loads as RGBA with {@code >> 8}.
 *       Fix: Raster access + {@code >> 8}.
 *
 *   <li><b>Paletted (P, no transparency)</b>: PIL converts to RGB (3 channels).
 *       Java's IndexColorModel can produce ARGB. Fix: convert to TYPE_INT_RGB.
 *
 *   <li><b>Paletted (P, with tRNS transparency)</b>: PIL converts to RGBA with original
 *       palette RGB values preserved even for alpha=0 pixels. Java's Graphics2D
 *       SRC_OVER compositing zeroes RGB for fully-transparent pixels. Fix: use
 *       {@link AlphaComposite#SRC} to preserve source pixel values.
 *
 *   <li><b>1-bit bilevel (mode '1')</b>: Java reads as TYPE_BYTE_BINARY (not gray).
 *       We convert to TYPE_INT_RGB; the white/black palette gives the right RGB values.
 * </ol>
 */
public final class PNGDecoder {
    private PNGDecoder() {}

    public static DecodedImage decode(byte[] bytes) throws DecodeException {
        // Sniff dimensions before allocating the full BufferedImage.
        try {
            Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("PNG");
            if (readers.hasNext()) {
                ImageReader reader = readers.next();
                try {
                    reader.setInput(new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes)));
                    int w = reader.getWidth(0);
                    int h = reader.getHeight(0);
                    Limits.checkDimensions(w, h, Format.PNG);
                } finally {
                    reader.dispose();
                }
            }
        } catch (DecodeException e) {
            throw e;
        } catch (IOException e) {
            // Header parse failed — a corrupt PNG, not a "we couldn't be bothered to
            // check". Propagate as corruptInput rather than silently falling through
            // to ImageIO.read() which might OOM on a malformed dim field.
            throw new DecodeException(DecodeException.Kind.CORRUPT_INPUT, Format.PNG,
                "PNG header dimension read failed: " + e.getMessage());
        }

        BufferedImage img;
        try {
            img = ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (IOException e) {
            throw new DecodeException(DecodeException.Kind.CORRUPT_INPUT, Format.PNG,
                "ImageIO.read failed: " + e.getMessage());
        }
        if (img == null) {
            throw new DecodeException(DecodeException.Kind.CORRUPT_INPUT, Format.PNG,
                "ImageIO could not decode PNG");
        }

        int width  = img.getWidth();
        int height = img.getHeight();
        int imgType = img.getType();

        // ── Indexed / paletted images ────────────────────────────────────────────────
        // TYPE_BYTE_INDEXED, TYPE_BYTE_BINARY (1-bit bilevel), or TYPE_CUSTOM with
        // IndexColorModel: the raster holds palette indices (numBands=1), not expanded
        // RGB values. We expand the palette ourselves.
        boolean isIndexed = imgType == BufferedImage.TYPE_BYTE_INDEXED
            || imgType == BufferedImage.TYPE_BYTE_BINARY
            || (imgType == BufferedImage.TYPE_CUSTOM
                && img.getColorModel() instanceof IndexColorModel);

        if (isIndexed) {
            // Determine whether the original palette has alpha (tRNS chunk).
            boolean srcHasAlpha = img.getColorModel().hasAlpha();
            return decodeIndexed(img, width, height, srcHasAlpha);
        }

        // ── Determine channel count from the source image ────────────────────────────
        boolean hasAlpha = img.getColorModel().hasAlpha();
        int colorSpaceType = img.getColorModel().getColorSpace().getType();
        boolean isGray  = (colorSpaceType == ColorSpace.TYPE_GRAY);
        int bitsPerSample = img.getColorModel().getComponentSize(0);
        boolean is16bit = (bitsPerSample == 16);

        // ── 8-bit non-gray: use getRGB() (matches PIL for these types) ───────────────
        if (!isGray && !is16bit) {
            return decodeViaGetRgb(img, width, height, hasAlpha);
        }

        // ── Grayscale or 16-bit: use Raster API to match PIL's raw-sample behavior ──
        Channels channels = hasAlpha ? Channels.RGBA : Channels.RGB;
        int channelCount  = hasAlpha ? 4 : 3;
        long outSize = Math.multiplyExact(Math.multiplyExact((long) width, (long) height), (long) channelCount);
        if (outSize > Integer.MAX_VALUE) {
            throw new DecodeException(DecodeException.Kind.IMAGE_TOO_LARGE, Format.PNG,
                "pixel buffer size " + outSize + " exceeds Java int max");
        }
        byte[] out = new byte[(int) outSize];
        Raster raster  = img.getRaster();
        int numBands   = raster.getNumBands();
        int[] samples  = new int[width * numBands];
        int outIdx = 0;

        for (int y = 0; y < height; y++) {
            raster.getPixels(0, y, width, 1, samples);

            for (int x = 0; x < width; x++) {
                int base = x * numBands;

                if (isGray) {
                    // PIL maps gray → R=G=B with no gamma correction.
                    int gray = samples[base];
                    if (is16bit && !hasAlpha) {
                        // Mode 'I': PIL keeps raw 16-bit int and clips to 255 on convert('RGB').
                        gray = Math.min(gray, 255);
                    } else if (is16bit) {
                        // Mode 'LA' (16-bit) → RGBA: PIL uses >>8 at load time.
                        gray = gray >> 8;
                    }
                    // 8-bit: use the raw sample value directly.
                    byte gByte = (byte) (gray & 0xFF);
                    out[outIdx++] = gByte;   // R
                    out[outIdx++] = gByte;   // G
                    out[outIdx++] = gByte;   // B
                    if (hasAlpha) {
                        // Alpha is in the last band.
                        int alpha = samples[base + numBands - 1];
                        if (is16bit) alpha = alpha >> 8;
                        out[outIdx++] = (byte) (alpha & 0xFF);
                    }
                } else {
                    // 16-bit RGB or RGBA: PIL uses >>8 when loading.
                    int r = samples[base]     >> 8;
                    int g = samples[base + 1] >> 8;
                    int b = samples[base + 2] >> 8;
                    out[outIdx++] = (byte) (r & 0xFF);
                    out[outIdx++] = (byte) (g & 0xFF);
                    out[outIdx++] = (byte) (b & 0xFF);
                    if (hasAlpha) {
                        int alpha = samples[base + 3] >> 8;
                        out[outIdx++] = (byte) (alpha & 0xFF);
                    }
                }
            }
        }

        return new DecodedImage(width, height, out, channels, Format.PNG);
    }

    /**
     * Expand palette-indexed images to RGB or RGBA.
     *
     * <p>Uses {@link AlphaComposite#SRC} when drawing so that transparent pixels retain
     * their original palette RGB values (matching PIL's behavior). Without SRC,
     * Graphics2D's default SRC_OVER compositing zeroes the RGB components of fully
     * transparent pixels.
     */
    private static DecodedImage decodeIndexed(BufferedImage src, int width, int height,
                                              boolean hasAlpha) throws DecodeException {
        Channels channels = hasAlpha ? Channels.RGBA : Channels.RGB;
        int channelCount  = hasAlpha ? 4 : 3;

        // Draw into a standard ARGB image; SRC composite preserves source pixel values.
        int targetType = hasAlpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        BufferedImage dst = new BufferedImage(width, height, targetType);
        Graphics2D g = dst.createGraphics();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC));
        g.drawImage(src, 0, 0, null);
        g.dispose();

        // Read back via getRGB().
        long outSize = Math.multiplyExact(Math.multiplyExact((long) width, (long) height), (long) channelCount);
        if (outSize > Integer.MAX_VALUE) {
            throw new DecodeException(DecodeException.Kind.IMAGE_TOO_LARGE, Format.PNG,
                "pixel buffer size " + outSize + " exceeds Java int max");
        }
        byte[] out = new byte[(int) outSize];
        int[] argb = new int[width];
        int outIdx = 0;
        for (int y = 0; y < height; y++) {
            dst.getRGB(0, y, width, 1, argb, 0, width);
            for (int x = 0; x < width; x++) {
                int pixel = argb[x];
                out[outIdx++] = (byte) ((pixel >> 16) & 0xFF);   // R
                out[outIdx++] = (byte) ((pixel >> 8) & 0xFF);    // G
                out[outIdx++] = (byte) (pixel & 0xFF);           // B
                if (hasAlpha) {
                    out[outIdx++] = (byte) ((pixel >> 24) & 0xFF);  // A
                }
            }
        }
        return new DecodedImage(width, height, out, channels, Format.PNG);
    }

    /** Decode 8-bit non-gray images using getRGB() (matches PIL for these types). */
    private static DecodedImage decodeViaGetRgb(BufferedImage img, int width, int height,
                                                boolean hasAlpha) throws DecodeException {
        Channels channels = hasAlpha ? Channels.RGBA : Channels.RGB;
        int channelCount  = hasAlpha ? 4 : 3;
        long outSize = Math.multiplyExact(Math.multiplyExact((long) width, (long) height), (long) channelCount);
        if (outSize > Integer.MAX_VALUE) {
            throw new DecodeException(DecodeException.Kind.IMAGE_TOO_LARGE, Format.PNG,
                "pixel buffer size " + outSize + " exceeds Java int max");
        }
        byte[] out = new byte[(int) outSize];
        int[] argb = new int[width];
        int outIdx = 0;
        for (int y = 0; y < height; y++) {
            img.getRGB(0, y, width, 1, argb, 0, width);
            for (int x = 0; x < width; x++) {
                int pixel = argb[x];
                out[outIdx++] = (byte) ((pixel >> 16) & 0xFF);   // R
                out[outIdx++] = (byte) ((pixel >> 8) & 0xFF);    // G
                out[outIdx++] = (byte) (pixel & 0xFF);           // B
                if (hasAlpha) {
                    out[outIdx++] = (byte) ((pixel >> 24) & 0xFF);  // A
                }
            }
        }
        return new DecodedImage(width, height, out, channels, Format.PNG);
    }
}
