package io.rosetta.imagedecode.internal;

import io.rosetta.imagedecode.Channels;
import io.rosetta.imagedecode.DecodeException;
import io.rosetta.imagedecode.DecodedImage;
import io.rosetta.imagedecode.Format;

import javax.imageio.ImageIO;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.io.ByteArrayInputStream;
import java.io.IOException;

public final class TIFFDecoder {
    private TIFFDecoder() {}

    /**
     * Sniff TIFF ImageWidth (tag 0x0100) and ImageLength (tag 0x0101) from
     * the first IFD without invoking the full decoder. Returns null if the
     * header is malformed (let the main decoder produce the canonical
     * error). Spec §3.1 requires this pre-check before
     * {@link ImageIO#read} allocates any pixel buffer.
     *
     * <p>TIFF layout: byte order (2) + magic (2, = 42) + IFD offset (4).
     * The IFD at that offset is entry count (2) + N × 12-byte entries +
     * next-IFD offset (4). Each entry is tag(2) + type(2) + count(4) +
     * value(4). For LONG/SHORT typed scalars with count=1 the value field
     * holds the dimension directly.
     */
    static int[] sniffDimensions(byte[] bytes) {
        if (bytes == null || bytes.length < 8) return null;
        final boolean little;
        if (bytes[0] == 0x49 && bytes[1] == 0x49) {
            little = true;
        } else if (bytes[0] == 0x4D && bytes[1] == 0x4D) {
            little = false;
        } else {
            return null;
        }
        int magic = readU16(bytes, 2, little);
        if (magic != 42) return null;
        long ifdOff = readU32(bytes, 4, little);
        if (ifdOff < 8 || ifdOff + 2 > bytes.length) return null;
        int n = readU16(bytes, (int) ifdOff, little);
        if (n <= 0) return null;
        // Defensive cap: real TIFFs rarely have thousands of IFD entries.
        int capped = Math.min(n, 4096);
        int width = -1;
        int height = -1;
        for (int i = 0; i < capped; i++) {
            int entryOff = (int) ifdOff + 2 + i * 12;
            if (entryOff + 12 > bytes.length) return null;
            int tag = readU16(bytes, entryOff, little);
            int type = readU16(bytes, entryOff + 2, little);
            long count = readU32(bytes, entryOff + 4, little);
            if (count != 1) continue;
            // Only SHORT (3) or LONG (4) with count=1 inline value.
            if (type != 3 && type != 4) continue;
            long value;
            if (type == 3) {
                value = readU16(bytes, entryOff + 8, little);
            } else {
                value = readU32(bytes, entryOff + 8, little);
            }
            if (value < 0 || value > Integer.MAX_VALUE) return null;
            if (tag == 0x0100) width = (int) value;
            else if (tag == 0x0101) height = (int) value;
            if (width >= 0 && height >= 0) break;
        }
        if (width <= 0 || height <= 0) return null;
        return new int[]{width, height};
    }

    private static int readU16(byte[] b, int off, boolean little) {
        if (off + 2 > b.length) return -1;
        if (little) {
            return (b[off] & 0xFF) | ((b[off + 1] & 0xFF) << 8);
        }
        return ((b[off] & 0xFF) << 8) | (b[off + 1] & 0xFF);
    }

    private static long readU32(byte[] b, int off, boolean little) {
        if (off + 4 > b.length) return -1;
        if (little) {
            return (long) (b[off] & 0xFF)
                | ((long) (b[off + 1] & 0xFF) << 8)
                | ((long) (b[off + 2] & 0xFF) << 16)
                | ((long) (b[off + 3] & 0xFF) << 24);
        }
        return ((long) (b[off] & 0xFF) << 24)
            | ((long) (b[off + 1] & 0xFF) << 16)
            | ((long) (b[off + 2] & 0xFF) << 8)
            | (long) (b[off + 3] & 0xFF);
    }

    public static DecodedImage decode(byte[] bytes) throws DecodeException {
        // Sniff dimensions from the first IFD BEFORE invoking ImageIO, so
        // the MAX_PIXELS guard fires before the underlying decoder
        // allocates the raster. Spec §3.1 requires this ordering.
        int[] dims = sniffDimensions(bytes);
        if (dims != null) {
            Limits.checkDimensions(dims[0], dims[1], Format.TIFF);
        }

        BufferedImage img;
        try {
            img = ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (IOException e) {
            throw new DecodeException(DecodeException.Kind.CORRUPT_INPUT, Format.TIFF, "ImageIO.read failed: " + e.getMessage());
        }
        if (img == null) {
            throw new DecodeException(DecodeException.Kind.CORRUPT_INPUT, Format.TIFF, "ImageIO could not decode TIFF");
        }

        int width = img.getWidth();
        int height = img.getHeight();

        Limits.checkDimensions(width, height, Format.TIFF);

        boolean isGray = img.getColorModel().getColorSpace().getType() == ColorSpace.TYPE_GRAY;

        // For v1: always RGB output (grayscale expands).
        // Grayscale images: use Raster.getPixels() to bypass sRGB gamma that getRGB() applies.
        // RGB images: getRGB() is correct.
        long outSize = Math.multiplyExact(Math.multiplyExact((long) width, (long) height), 3L);
        if (outSize > Integer.MAX_VALUE) {
            throw new DecodeException(DecodeException.Kind.IMAGE_TOO_LARGE, Format.TIFF,
                "pixel buffer size " + outSize + " exceeds Java int max");
        }
        byte[] out = new byte[(int) outSize];
        int outIdx = 0;

        if (isGray) {
            Raster raster = img.getRaster();
            int numBands = raster.getNumBands();
            int[] samples = new int[width * numBands];
            for (int y = 0; y < height; y++) {
                raster.getPixels(0, y, width, 1, samples);
                for (int x = 0; x < width; x++) {
                    byte gByte = (byte) (samples[x * numBands] & 0xFF);
                    out[outIdx++] = gByte; // R
                    out[outIdx++] = gByte; // G
                    out[outIdx++] = gByte; // B
                }
            }
        } else {
            int[] argb = new int[width];
            for (int y = 0; y < height; y++) {
                img.getRGB(0, y, width, 1, argb, 0, width);
                for (int x = 0; x < width; x++) {
                    int pixel = argb[x];
                    out[outIdx++] = (byte) ((pixel >> 16) & 0xFF);
                    out[outIdx++] = (byte) ((pixel >> 8) & 0xFF);
                    out[outIdx++] = (byte) (pixel & 0xFF);
                }
            }
        }

        return new DecodedImage(width, height, out, Channels.RGB, Format.TIFF);
    }
}
