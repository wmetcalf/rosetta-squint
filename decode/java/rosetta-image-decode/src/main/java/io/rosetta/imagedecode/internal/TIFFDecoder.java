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

    public static DecodedImage decode(byte[] bytes) throws DecodeException {
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
