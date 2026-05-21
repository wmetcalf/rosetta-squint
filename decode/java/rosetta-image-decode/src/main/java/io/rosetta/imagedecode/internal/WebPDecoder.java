package io.rosetta.imagedecode.internal;

import io.rosetta.imagedecode.Channels;
import io.rosetta.imagedecode.DecodeException;
import io.rosetta.imagedecode.DecodedImage;
import io.rosetta.imagedecode.Format;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

public final class WebPDecoder {
    private WebPDecoder() {}

    public static DecodedImage decode(byte[] bytes) throws DecodeException {
        BufferedImage img;
        try {
            img = ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (IOException e) {
            throw new DecodeException(DecodeException.Kind.CORRUPT_INPUT, Format.WEBP, "ImageIO.read failed: " + e.getMessage());
        }
        if (img == null) {
            throw new DecodeException(DecodeException.Kind.CORRUPT_INPUT, Format.WEBP, "ImageIO could not decode WebP");
        }

        boolean hasAlpha = detectWebpAlpha(bytes);
        Channels channels = hasAlpha ? Channels.RGBA : Channels.RGB;
        int width = img.getWidth();
        int height = img.getHeight();
        int channelCount = hasAlpha ? 4 : 3;

        Limits.checkDimensions(width, height, Format.WEBP);

        long outSize = Math.multiplyExact(Math.multiplyExact((long) width, (long) height), (long) channelCount);
        if (outSize > Integer.MAX_VALUE) {
            throw new DecodeException(DecodeException.Kind.IMAGE_TOO_LARGE, Format.WEBP,
                "pixel buffer size " + outSize + " exceeds Java int max");
        }
        byte[] out = new byte[(int) outSize];

        int[] argb = new int[width];
        int outIdx = 0;
        for (int y = 0; y < height; y++) {
            img.getRGB(0, y, width, 1, argb, 0, width);
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

        return new DecodedImage(width, height, out, channels, Format.WEBP);
    }

    /**
     * Inspect the WebP container to determine whether the image has an alpha channel.
     * This matches PIL's behavior more reliably than ColorModel.hasAlpha().
     *
     * WebP container layout (bytes 0-11):
     *   0-3:  "RIFF"
     *   4-7:  file size (little-endian uint32)
     *   8-11: "WEBP"
     *   12-15: chunk FourCC  ("VP8 ", "VP8L", or "VP8X")
     */
    private static boolean detectWebpAlpha(byte[] bytes) {
        if (bytes.length < 20) return false;
        // bytes[12..15] = chunk FourCC
        String chunkType = new String(bytes, 12, 4, java.nio.charset.StandardCharsets.US_ASCII);
        switch (chunkType) {
            case "VP8X":
                // Extended format: flags byte at offset 20, bit 4 (0x10) = has alpha
                if (bytes.length < 21) return false;
                return (bytes[20] & 0x10) != 0;
            case "VP8L":
                // Lossless: the transform presence bit in the bitstream header.
                // VP8L bitstream starts at byte 20 (after 4 bytes FourCC + 4 bytes chunk size + 1 byte signature 0x2F).
                // The signature byte is at offset 20; bits 0-13 of the next 4 bytes encode width-1,
                // bits 14-27 encode height-1, bit 28 = has alpha.
                if (bytes.length < 25) return false;
                // Byte 20 is VP8L signature (0x2F). Bitstream data starts at byte 21.
                // We need bits 28 of the 32-bit value at offset 21 (LE).
                int b21 = bytes[21] & 0xFF;
                int b22 = bytes[22] & 0xFF;
                int b23 = bytes[23] & 0xFF;
                int b24 = bytes[24] & 0xFF;
                int bits = b21 | (b22 << 8) | (b23 << 16) | (b24 << 24);
                // bit 28 in the 32-bit LE word starting at offset 21 = alpha-used flag
                return (bits & (1 << 28)) != 0;
            case "VP8 ":
                // Lossy, no alpha
                return false;
            default:
                return false;
        }
    }
}
