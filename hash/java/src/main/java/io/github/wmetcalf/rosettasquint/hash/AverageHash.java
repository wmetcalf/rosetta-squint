package io.github.wmetcalf.rosettasquint.hash;

import io.github.wmetcalf.rosettasquint.hash.internal.BufferedImageRgb;
import io.github.wmetcalf.rosettasquint.hash.internal.LanczosFixed;
import io.github.wmetcalf.rosettasquint.hash.internal.PilGrayscale;

import java.awt.image.BufferedImage;

/**
 * average_hash: convert to grayscale, Lanczos resize to (N, N), threshold against mean.
 */
public final class AverageHash {
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

        int[][] resized = LanczosFixed.resize(gray, hashSize, hashSize);

        long sum = 0;
        for (int y = 0; y < hashSize; y++)
            for (int x = 0; x < hashSize; x++)
                sum += resized[y][x];
        double avg = (double) sum / (hashSize * hashSize);

        boolean[][] bits = new boolean[hashSize][hashSize];
        for (int y = 0; y < hashSize; y++)
            for (int x = 0; x < hashSize; x++)
                bits[y][x] = resized[y][x] > avg;

        return new ImageHash(bits);
    }

    private AverageHash() {}
}
