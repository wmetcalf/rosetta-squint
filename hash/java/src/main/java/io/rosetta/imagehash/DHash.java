package io.rosetta.imagehash;

import io.rosetta.imagehash.internal.BufferedImageRgb;
import io.rosetta.imagehash.internal.LanczosFixed;
import io.rosetta.imagehash.internal.PilGrayscale;

import java.awt.image.BufferedImage;

/**
 * dhash: convert to grayscale, Lanczos resize to (width=N+1, height=N),
 * compare each pixel to the pixel to its right (strict >).
 */
public final class DHash {
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

        // Resize to width=N+1, height=N — note Lanczos signature is (dstW, dstH)
        int[][] resized = LanczosFixed.resize(gray, hashSize + 1, hashSize);

        // bit[y][x] = pixel[y][x+1] > pixel[y][x]
        boolean[][] bits = new boolean[hashSize][hashSize];
        for (int y = 0; y < hashSize; y++)
            for (int x = 0; x < hashSize; x++)
                bits[y][x] = resized[y][x + 1] > resized[y][x];

        return new ImageHash(bits);
    }

    private DHash() {}
}
