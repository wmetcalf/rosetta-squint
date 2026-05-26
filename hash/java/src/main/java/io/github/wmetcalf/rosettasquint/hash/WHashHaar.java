package io.github.wmetcalf.rosettasquint.hash;

import io.github.wmetcalf.rosettasquint.hash.internal.BufferedImageRgb;
import io.github.wmetcalf.rosettasquint.hash.internal.HaarDwt;
import io.github.wmetcalf.rosettasquint.hash.internal.LanczosFixed;
import io.github.wmetcalf.rosettasquint.hash.internal.PilGrayscale;

import java.awt.image.BufferedImage;
import java.util.Arrays;

/**
 * whash with mode='haar', remove_max_haar_ll=true, image_scale=None.
 * Mirrors Python imagehash.whash exactly.
 */
public final class WHashHaar {
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
            throw new IllegalArgumentException("hashSize too large for image (level=" + level + " > ll_max_level=" + llMaxLevel + ")");
        int dwtLevel = llMaxLevel - level;

        int[][] gray = new int[h][w];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int p = rgb[y][x];
                gray[y][x] = PilGrayscale.toGray((p >> 16) & 0xFF, (p >> 8) & 0xFF, p & 0xFF);
            }
        }
        int[][] resized = LanczosFixed.resize(gray, imageScale, imageScale);

        double[][] pixels = new double[imageScale][imageScale];
        for (int y = 0; y < imageScale; y++)
            for (int x = 0; x < imageScale; x++)
                pixels[y][x] = resized[y][x] / 255.0;

        // remove_max_haar_ll: full decomp, zero LL, reconstruct
        HaarDwt.WavedecResult fullDec = HaarDwt.wavedec2(pixels, llMaxLevel);
        for (int y = 0; y < fullDec.cA.length; y++)
            for (int x = 0; x < fullDec.cA[y].length; x++)
                fullDec.cA[y][x] = 0.0;
        double[][] modified = HaarDwt.waverec2(fullDec);

        // Decompose modified to dwt_level
        HaarDwt.WavedecResult dec = HaarDwt.wavedec2(modified, dwtLevel);
        double[][] ll = dec.cA;

        int n = ll.length * ll[0].length;
        double[] flat = new double[n];
        int k = 0;
        for (int y = 0; y < ll.length; y++)
            for (int x = 0; x < ll[0].length; x++)
                flat[k++] = ll[y][x];
        double[] sorted = flat.clone();
        Arrays.sort(sorted);
        double median = (n % 2 == 1) ? sorted[n / 2] : (sorted[n / 2 - 1] + sorted[n / 2]) / 2.0;

        boolean[][] bits = new boolean[ll.length][ll[0].length];
        for (int y = 0; y < ll.length; y++)
            for (int x = 0; x < ll[0].length; x++)
                bits[y][x] = ll[y][x] > median;

        return new ImageHash(bits);
    }

    private static boolean isPowerOfTwo(int n) {
        return n > 0 && (n & (n - 1)) == 0;
    }

    private WHashHaar() {}
}
