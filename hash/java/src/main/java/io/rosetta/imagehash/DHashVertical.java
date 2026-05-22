package io.rosetta.imagehash;

import io.rosetta.imagehash.internal.BufferedImageRgb;
import io.rosetta.imagehash.internal.LanczosFixed;
import io.rosetta.imagehash.internal.PilGrayscale;

import java.awt.image.BufferedImage;

/**
 * dhash_vertical: pre-3.0 back-compat variant of dhash that compares vertically
 * adjacent pixels instead of horizontally adjacent.
 *
 * Resize to (width=N, height=N+1); bit[y][x] = pixel[y+1][x] > pixel[y][x].
 */
public final class DHashVertical {
    public static ImageHash compute(BufferedImage img, int hashSize) {
        if (hashSize < 2)
            throw new IllegalArgumentException("hashSize must be >= 2");

        int[][] rgb = BufferedImageRgb.toIntArray(img);
        int h = rgb.length;
        int w = rgb[0].length;

        int[][] gray = new int[h][w];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int p = rgb[y][x];
                int r = (p >> 16) & 0xFF;
                int g = (p >> 8) & 0xFF;
                int b = p & 0xFF;
                gray[y][x] = PilGrayscale.toGray(r, g, b);
            }
        }

        // Resize to width=N, height=N+1 — note Lanczos signature is (dstW, dstH)
        int[][] resized = LanczosFixed.resize(gray, hashSize, hashSize + 1);

        // bit[y][x] = pixel[y+1][x] > pixel[y][x]
        boolean[][] bits = new boolean[hashSize][hashSize];
        for (int y = 0; y < hashSize; y++)
            for (int x = 0; x < hashSize; x++)
                bits[y][x] = resized[y + 1][x] > resized[y][x];

        return new ImageHash(bits);
    }

    private DHashVertical() {}
}
