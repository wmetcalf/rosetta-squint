package io.rosetta.imagehash.internal;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

/**
 * Normalize a BufferedImage of any type to row-major int[H][W] where each cell
 * is packed 0x00RRGGBB. Non-TYPE_INT_RGB inputs are first drawn onto a new
 * TYPE_INT_RGB BufferedImage (background pre-initialized to opaque black),
 * matching PIL Image.convert('RGB') composite-on-black behavior.
 */
public final class BufferedImageRgb {
    public static int[][] toIntArray(BufferedImage src) {
        BufferedImage rgb = (src.getType() == BufferedImage.TYPE_INT_RGB)
                ? src
                : convertToIntRgb(src);
        int w = rgb.getWidth();
        int h = rgb.getHeight();
        int[][] out = new int[h][w];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                out[y][x] = rgb.getRGB(x, y) & 0xFFFFFF;
            }
        }
        return out;
    }

    private static BufferedImage convertToIntRgb(BufferedImage src) {
        BufferedImage dst = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = dst.createGraphics();
        try {
            g.drawImage(src, 0, 0, null);
        } finally {
            g.dispose();
        }
        return dst;
    }

    private BufferedImageRgb() {}
}
