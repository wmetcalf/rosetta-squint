package io.github.wmetcalf.rosettasquint.hash;

import io.github.wmetcalf.rosettasquint.hash.internal.BufferedImageRgb;
import io.github.wmetcalf.rosettasquint.hash.internal.Dct;
import io.github.wmetcalf.rosettasquint.hash.internal.LanczosFixed;
import io.github.wmetcalf.rosettasquint.hash.internal.PilGrayscale;

import java.awt.image.BufferedImage;

/**
 * phash_simple: row-wise 1-D DCT (not 2-D) → take rows [0,N) cols [1,N+1) → mean threshold.
 *
 * <p>The Python imagehash source (v4.3.2) uses {@code scipy.fftpack.dct(pixels)} with no
 * axis argument, which applies 1-D DCT along the last axis (rows). It then takes
 * {@code dct[:hash_size, 1:hash_size+1]} — skipping the DC column (index 0) —
 * and thresholds against the mean (not the median used by phash).
 *
 * <p>This is distinct from {@code phash} which does a full 2-D DCT (column-then-row)
 * and takes {@code dct[0:N, 0:N]} (including the DC term at [0,0]).
 */
public final class PHashSimple {
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

        // Row-wise 1-D DCT (scipy.fftpack.dct(pixels) with default axis=-1)
        // Then take rows [0, hashSize), cols [1, hashSize+1)
        double sum = 0.0;
        double[][] block = new double[hashSize][hashSize];
        for (int y = 0; y < imgSize; y++) {
            // Only process rows needed for the output block
            if (y >= hashSize) break;
            double[] row = new double[imgSize];
            for (int x = 0; x < imgSize; x++) row[x] = resized[y][x];
            double[] dctRow = Dct.dct1d(row);
            // Take cols [1, hashSize+1)
            for (int x = 0; x < hashSize; x++) {
                block[y][x] = dctRow[x + 1];
                sum += dctRow[x + 1];
            }
        }
        double mean = sum / (hashSize * hashSize);

        // Snap-to-threshold tie-break: deterministic bit 0 on ties.
        // See spec/SPEC.md §"Threshold tie-break".
        double threshold = mean + PHash.SNAP_EPS;
        boolean[][] bits = new boolean[hashSize][hashSize];
        for (int y = 0; y < hashSize; y++)
            for (int x = 0; x < hashSize; x++)
                bits[y][x] = block[y][x] > threshold;

        return new ImageHash(bits);
    }

    private PHashSimple() {}
}
