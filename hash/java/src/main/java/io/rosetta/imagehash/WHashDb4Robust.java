package io.rosetta.imagehash;

import io.rosetta.imagehash.internal.BufferedImageRgb;
import io.rosetta.imagehash.internal.Db4Dwt;
import io.rosetta.imagehash.internal.HaarDwt;
import io.rosetta.imagehash.internal.LanczosFixed;
import io.rosetta.imagehash.internal.PilGrayscale;

import java.awt.image.BufferedImage;
import java.util.Arrays;

/**
 * Cross-port-stable variant of WHashDb4. Identical pipeline up to the LL
 * band, then snaps coefficients with |c| < WHASH_DB4_ROBUST_EPS to exactly
 * zero before median + threshold. Real-world photos produce the same hash
 * as WHashDb4; pathological symmetric inputs (checker patterns, line art)
 * produce a deterministic hash across all ports. NOT byte-exact-compatible
 * with Python imagehash on pathological inputs.
 *
 * <p>See spec/SPEC.md §whash_db4_robust for the authoritative description.
 */
public final class WHashDb4Robust {
    /** ε threshold for snap-to-zero. See spec/SPEC.md §whash_db4_robust. */
    public static final double WHASH_DB4_ROBUST_EPS = 1e-12;

    private WHashDb4Robust() {}

    public static ImageHash compute(BufferedImage img) {
        return compute(img, 8);
    }

    public static ImageHash compute(BufferedImage img, int hashSize) {
        if (hashSize < 2)
            throw new IllegalArgumentException("hashSize must be >= 2");
        if (!isPowerOfTwo(hashSize))
            throw new IllegalArgumentException("hashSize must be a power of 2 for whash");

        int[][] rgb = BufferedImageRgb.toIntArray(img);
        int h = rgb.length;
        int w = rgb[0].length;

        int minSide = Math.min(w, h);
        int imageNaturalScale = 1 << (int) Math.floor(Math.log(minSide) / Math.log(2));
        int imageScale = Math.max(imageNaturalScale, hashSize);

        int llMaxLevel = (int) (Math.log(imageScale) / Math.log(2));
        int level = (int) (Math.log(hashSize) / Math.log(2));
        if (level > llMaxLevel)
            throw new IllegalArgumentException(
                    "hashSize too large for image (level=" + level + " > ll_max_level=" + llMaxLevel + ")");
        int dwtLevel = llMaxLevel - level;

        // Grayscale
        int[][] gray = new int[h][w];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int p = rgb[y][x];
                gray[y][x] = PilGrayscale.toGray((p >> 16) & 0xFF, (p >> 8) & 0xFF, p & 0xFF);
            }
        }

        // Lanczos resize to (imageScale, imageScale)
        int[][] resized = LanczosFixed.resize(gray, imageScale, imageScale);

        // Normalize to [0, 1]
        double[][] pixels = new double[imageScale][imageScale];
        for (int y = 0; y < imageScale; y++)
            for (int x = 0; x < imageScale; x++)
                pixels[y][x] = resized[y][x] / 255.0;

        // Step 1: Full HAAR decomposition, zero LL, reconstruct with HAAR
        // (Python: pywt.wavedec2(pixels, 'haar', level=ll_max_level))
        HaarDwt.WavedecResult fullHaarDec = HaarDwt.wavedec2(pixels, llMaxLevel);
        for (int y = 0; y < fullHaarDec.cA.length; y++)
            for (int x = 0; x < fullHaarDec.cA[y].length; x++)
                fullHaarDec.cA[y][x] = 0.0;
        double[][] modified = HaarDwt.waverec2(fullHaarDec);

        // Step 2: Second decomposition with DB4
        // (Python: pywt.wavedec2(modified, 'db4', level=dwt_level))
        Db4Dwt.WavedecResult db4Dec = Db4Dwt.wavedec2(modified, dwtLevel);
        double[][] ll = db4Dec.cA;

        // Snap near-zero coefficients to exactly zero (robust step)
        int llH = ll.length, llW = ll[0].length;
        for (int y = 0; y < llH; y++)
            for (int x = 0; x < llW; x++)
                if (Math.abs(ll[y][x]) < WHASH_DB4_ROBUST_EPS)
                    ll[y][x] = 0.0;

        // Median threshold
        int n = llH * llW;
        double[] flat = new double[n];
        int k = 0;
        for (int y = 0; y < llH; y++)
            for (int x = 0; x < llW; x++)
                flat[k++] = ll[y][x];
        double[] sorted = flat.clone();
        Arrays.sort(sorted);
        double median = (n % 2 == 1) ? sorted[n / 2] : (sorted[n / 2 - 1] + sorted[n / 2]) / 2.0;

        boolean[][] bits = new boolean[llH][llW];
        for (int y = 0; y < llH; y++)
            for (int x = 0; x < llW; x++)
                bits[y][x] = ll[y][x] > median;

        return new ImageHash(bits);
    }

    private static boolean isPowerOfTwo(int n) {
        return n > 0 && (n & (n - 1)) == 0;
    }
}
