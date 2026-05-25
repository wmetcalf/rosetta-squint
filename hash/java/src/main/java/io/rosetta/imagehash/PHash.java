package io.rosetta.imagehash;

import io.rosetta.imagehash.internal.BufferedImageRgb;
import io.rosetta.imagehash.internal.Dct;
import io.rosetta.imagehash.internal.LanczosFixed;
import io.rosetta.imagehash.internal.PilGrayscale;

import java.awt.image.BufferedImage;
import java.util.Arrays;

/**
 * phash: grayscale -> Lanczos to (N*4, N*4) -> 2-D DCT-II -> top-left NxN block
 * -> bit = (coefficient &gt; median + {@link #SNAP_EPS}).
 *
 * <p>The snap-to-threshold tie-break (&epsilon; = 1e-10) deterministically maps
 * coefficients within &epsilon; of the median to bit 0, eliminating cross-port
 * FP-noise divergence at large hash sizes. See spec/SPEC.md
 * &sect;"Threshold tie-break".
 */
public final class PHash {
    /**
     * &epsilon; threshold for the snap-to-threshold tie-break used by PHash,
     * PHashSimple, WHashDb4, and WHashDb4Robust. Coefficients within
     * SNAP_EPS of the threshold map deterministically to bit 0 across all
     * ports. Fixed across all 6 ports — do not vary per-call without a
     * coordinated spec change.
     */
    public static final double SNAP_EPS = 1e-10;

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

        // Snap-to-threshold tie-break: deterministic bit 0 on ties.
        double threshold = median + SNAP_EPS;
        boolean[][] bits = new boolean[hashSize][hashSize];
        for (int y = 0; y < hashSize; y++)
            for (int x = 0; x < hashSize; x++)
                bits[y][x] = dct[y][x] > threshold;

        return new ImageHash(bits);
    }

    private PHash() {}
}
