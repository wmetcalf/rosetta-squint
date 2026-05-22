package io.rosetta.imagehash;

import io.rosetta.imagehash.internal.BufferedImageRgb;
import io.rosetta.imagehash.internal.Dct;
import io.rosetta.imagehash.internal.LanczosFixed;
import io.rosetta.imagehash.internal.PilGrayscale;

import java.awt.image.BufferedImage;
import java.util.Arrays;

/**
 * phash: grayscale -> Lanczos to (N*4, N*4) -> 2-D DCT-II -> top-left NxN block -> median threshold.
 */
public final class PHash {
    public static ImageHash compute(BufferedImage img, int hashSize) {
        return compute(img, hashSize, 4);
    }

    public static ImageHash compute(BufferedImage img, int hashSize, int highfreqFactor) {
        if (hashSize < 2)
            throw new IllegalArgumentException("hashSize must be >= 2");

        int imgSize = hashSize * highfreqFactor;

        int[][] rgb = BufferedImageRgb.toIntArray(img);
        int h = rgb.length;
        int w = rgb[0].length;
        int[][] gray = new int[h][w];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int p = rgb[y][x];
                gray[y][x] = PilGrayscale.toGray((p >> 16) & 0xFF, (p >> 8) & 0xFF, p & 0xFF);
            }
        }

        int[][] resized = LanczosFixed.resize(gray, imgSize, imgSize);
        double[][] doubles = new double[imgSize][imgSize];
        for (int y = 0; y < imgSize; y++)
            for (int x = 0; x < imgSize; x++)
                doubles[y][x] = resized[y][x];

        double[][] dct = Dct.dct2d(doubles);

        double[] block = new double[hashSize * hashSize];
        int k = 0;
        for (int y = 0; y < hashSize; y++)
            for (int x = 0; x < hashSize; x++)
                block[k++] = dct[y][x];

        double[] sorted = block.clone();
        Arrays.sort(sorted);
        int n = sorted.length;
        double median = (n % 2 == 1) ? sorted[n / 2] : (sorted[n / 2 - 1] + sorted[n / 2]) / 2.0;

        boolean[][] bits = new boolean[hashSize][hashSize];
        for (int y = 0; y < hashSize; y++)
            for (int x = 0; x < hashSize; x++)
                bits[y][x] = dct[y][x] > median;

        return new ImageHash(bits);
    }

    private PHash() {}
}
