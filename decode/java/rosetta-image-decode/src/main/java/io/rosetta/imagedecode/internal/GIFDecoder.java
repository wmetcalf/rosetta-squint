package io.rosetta.imagedecode.internal;

import io.rosetta.imagedecode.Channels;
import io.rosetta.imagedecode.DecodeException;
import io.rosetta.imagedecode.DecodedImage;
import io.rosetta.imagedecode.Format;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

public final class GIFDecoder {
    private GIFDecoder() {}

    public static DecodedImage decode(byte[] bytes) throws DecodeException {
        BufferedImage img;
        try {
            img = ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (IOException e) {
            throw new DecodeException(DecodeException.Kind.CORRUPT_INPUT, Format.GIF,
                "ImageIO.read failed: " + e.getMessage());
        }
        if (img == null) {
            throw new DecodeException(DecodeException.Kind.CORRUPT_INPUT, Format.GIF,
                "ImageIO could not decode GIF");
        }

        // GIFs are paletted. ImageIO returns BufferedImage.TYPE_BYTE_INDEXED with
        // an IndexColorModel. If the palette has a transparent index, the model
        // hasAlpha() == true and our output is RGBA.
        boolean hasAlpha = img.getColorModel().hasAlpha();
        Channels channels = hasAlpha ? Channels.RGBA : Channels.RGB;
        int width = img.getWidth();
        int height = img.getHeight();
        int channelCount = hasAlpha ? 4 : 3;

        Limits.checkDimensions(width, height, Format.GIF);

        // Draw into a standard int image using SRC composite so that transparent pixels
        // retain their original palette RGB values (matching PIL's behavior). Without SRC,
        // Graphics2D's default SRC_OVER compositing zeroes the RGB components of fully
        // transparent pixels.
        int targetType = hasAlpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        BufferedImage dst = new BufferedImage(width, height, targetType);
        Graphics2D g = dst.createGraphics();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC));
        g.drawImage(img, 0, 0, null);
        g.dispose();

        long outSize = Math.multiplyExact(Math.multiplyExact((long) width, (long) height), (long) channelCount);
        if (outSize > Integer.MAX_VALUE) {
            throw new DecodeException(DecodeException.Kind.IMAGE_TOO_LARGE, Format.GIF,
                "pixel buffer size " + outSize + " exceeds Java int max");
        }
        byte[] out = new byte[(int) outSize];
        int[] argb = new int[width];
        int outIdx = 0;
        for (int y = 0; y < height; y++) {
            dst.getRGB(0, y, width, 1, argb, 0, width);
            for (int x = 0; x < width; x++) {
                int pixel = argb[x];
                out[outIdx++] = (byte) ((pixel >> 16) & 0xFF);
                out[outIdx++] = (byte) ((pixel >> 8) & 0xFF);
                out[outIdx++] = (byte) (pixel & 0xFF);
                if (hasAlpha) {
                    out[outIdx++] = (byte) ((pixel >> 24) & 0xFF);
                }
            }
        }
        return new DecodedImage(width, height, out, channels, Format.GIF);
    }
}
