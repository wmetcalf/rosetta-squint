package io.rosetta.imagehash;

import io.rosetta.imagehash.internal.BufferedImageRgb;
import io.rosetta.imagehash.internal.Db4Dwt;
import io.rosetta.imagehash.internal.HaarDwt;
import io.rosetta.imagehash.internal.LanczosFixed;
import io.rosetta.imagehash.internal.PilGrayscale;

import java.awt.image.BufferedImage;
import java.util.Arrays;

/**
 * whash with mode='db4' (Daubechies-4 wavelet), mirroring Python imagehash exactly.
 *
 * <p>Despite the name, the implementation uses <b>Haar</b> for the
 * {@code remove_max_haar_ll} step (full decomp → zero LL → reconstruct) and
 * <b>Daubechies-4</b> only for the second {@code wavedec2} call that produces the
 * final LL band. This matches the Python imagehash source verbatim:
 * <pre>
 *   coeffs = pywt.wavedec2(pixels, 'haar', level=ll_max_level)
 *   coeffs[0] *= 0
 *   pixels = pywt.waverec2(coeffs, 'haar')
 *   coeffs = pywt.wavedec2(pixels, mode='db4', level=dwt_level)
 *   dwt_low = coeffs[0]
 * </pre>
 *
 * <p>The LL band after the second decomposition is NOT necessarily hashSize×hashSize;
 * for db4 with filter length 8, each level expands the output by ~3 samples per
 * dimension relative to half the input. The resulting bit array shape is whatever
 * the wavelet decomposition produces — the golden hex length reflects this.
 */
public final class WHashDb4 {
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

        // Median threshold with snap-to-threshold tie-break.
        int llH = ll.length, llW = ll[0].length;
        int n = llH * llW;
        double[] flat = new double[n];
        int k = 0;
        for (int y = 0; y < llH; y++)
            for (int x = 0; x < llW; x++)
                flat[k++] = ll[y][x];
        double[] sorted = flat.clone();
        Arrays.sort(sorted);
        double median = (n % 2 == 1) ? sorted[n / 2] : (sorted[n / 2 - 1] + sorted[n / 2]) / 2.0;

        // Snap-to-threshold tie-break: deterministic bit 0 on ties.
        // See spec/SPEC.md §"Threshold tie-break".
        double threshold = median + PHash.SNAP_EPS;
        boolean[][] bits = new boolean[llH][llW];
        for (int y = 0; y < llH; y++)
            for (int x = 0; x < llW; x++)
                bits[y][x] = ll[y][x] > threshold;

        return new ImageHash(bits);
    }

    private static boolean isPowerOfTwo(int n) {
        return n > 0 && (n & (n - 1)) == 0;
    }

    private WHashDb4() {}
}
