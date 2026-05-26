package io.github.wmetcalf.rosettasquint.hash.internal;

/**
 * Pillow-compatible GaussianBlur approximation via 3 separable box filters.
 *
 * Matches PIL.ImageFilter.GaussianBlur(radius=2) exactly:
 *   1. Compute float box radius using _gaussian_blur_radius(2.0, 3) from BoxBlur.c.
 *   2. Apply 3 horizontal passes (ImagingLineBoxBlur8) over rows.
 *   3. Transpose, apply 3 horizontal passes, transpose back (vertical passes).
 *   4. Values are integer-rounded inside each 1-D line via fixed-point (ww/fw).
 *
 * Reference: src/libImaging/BoxBlur.c in Pillow 10.4.0.
 */
public final class PilGaussianBlur {

    private static final int PASSES = 3;
    /** Default PIL GaussianBlur radius (sigma). */
    private static final double RADIUS = 2.0;

    /**
     * Apply PIL GaussianBlur(radius=2) to a uint8 grayscale image.
     *
     * @param src  row-major uint8 array [H][W], values 0-255
     * @return new [H][W] array with blur applied
     */
    public static int[][] apply(int[][] src) {
        int H = src.length;
        int W = src[0].length;

        double floatRadius = gaussianBlurRadius(RADIUS, PASSES);
        int radius = (int) floatRadius;  // truncate toward zero
        long ww = (long) ((1L << 24) / (floatRadius * 2.0 + 1.0));
        long fw = ((1L << 24) - (long) (radius * 2 + 1) * ww) / 2L;

        // --- 3 horizontal passes ---
        int[][] buf = copyOf(src, H, W);
        for (int pass = 0; pass < PASSES; pass++) {
            int[][] next = new int[H][W];
            int edgeA = Math.min(radius + 1, W);
            int edgeB = Math.max(W - radius - 1, 0);
            for (int y = 0; y < H; y++) {
                blurLine8(buf[y], next[y], W - 1, radius, edgeA, edgeB, ww, fw);
            }
            buf = next;
        }

        // --- Transpose ---
        int[][] t = transpose(buf, H, W);

        // --- 3 horizontal passes on transposed (= vertical) ---
        int edgeAv = Math.min(radius + 1, H);
        int edgeBv = Math.max(H - radius - 1, 0);
        for (int pass = 0; pass < PASSES; pass++) {
            int[][] next = new int[W][H];
            for (int y = 0; y < W; y++) {
                blurLine8(t[y], next[y], H - 1, radius, edgeAv, edgeBv, ww, fw);
            }
            t = next;
        }

        // --- Transpose back ---
        return transpose(t, W, H);
    }

    /**
     * From BoxBlur.c: _gaussian_blur_radius(radius, passes).
     * Computes the float box radius that approximates a Gaussian with std-dev = radius.
     */
    static double gaussianBlurRadius(double radius, int passes) {
        double sigma2 = radius * radius / passes;
        double L = Math.sqrt(12.0 * sigma2 + 1.0);
        double l = Math.floor((L - 1.0) / 2.0);
        double a = (2.0 * l + 1.0) * (l * (l + 1.0) - 3.0 * sigma2);
        a /= 6.0 * (sigma2 - (l + 1.0) * (l + 1.0));
        return l + a;
    }

    /**
     * ImagingLineBoxBlur8 — exact translation of the C function for 'L' (uint8) images.
     *
     * @param lineIn  source row (length >= lastx+1)
     * @param lineOut destination row
     * @param lastx   last valid index (= width - 1)
     * @param radius  integer part of box radius
     * @param edgeA   min(radius+1, width)
     * @param edgeB   max(width-radius-1, 0)
     * @param ww      fixed-point weight for interior pixels
     * @param fw      fixed-point weight for fractional far pixels
     */
    static void blurLine8(int[] lineIn, int[] lineOut,
                           int lastx, int radius, int edgeA, int edgeB,
                           long ww, long fw) {
        // Init acc for position -1 (one to the left of pixel 0)
        long acc = (long) lineIn[0] * (radius + 1);
        for (int x = 0; x < edgeA - 1; x++) {
            acc += lineIn[x];
        }
        acc += (long) lineIn[lastx] * (radius - edgeA + 1);

        if (edgeA <= edgeB) {
            for (int x = 0; x < edgeA; x++) {
                acc += lineIn[x + radius] - lineIn[0];
                long bulk = acc * ww + (long) (lineIn[0] + lineIn[x + radius + 1]) * fw;
                lineOut[x] = (int) ((bulk + (1L << 23)) >> 24);
            }
            for (int x = edgeA; x < edgeB; x++) {
                acc += lineIn[x + radius] - lineIn[x - radius - 1];
                long bulk = acc * ww + (long) (lineIn[x - radius - 1] + lineIn[x + radius + 1]) * fw;
                lineOut[x] = (int) ((bulk + (1L << 23)) >> 24);
            }
            for (int x = edgeB; x <= lastx; x++) {
                acc += lineIn[lastx] - lineIn[x - radius - 1];
                long bulk = acc * ww + (long) (lineIn[x - radius - 1] + lineIn[lastx]) * fw;
                lineOut[x] = (int) ((bulk + (1L << 23)) >> 24);
            }
        } else {
            for (int x = 0; x < edgeB; x++) {
                acc += lineIn[x + radius] - lineIn[0];
                long bulk = acc * ww + (long) (lineIn[0] + lineIn[x + radius + 1]) * fw;
                lineOut[x] = (int) ((bulk + (1L << 23)) >> 24);
            }
            for (int x = edgeB; x < edgeA; x++) {
                acc += lineIn[lastx] - lineIn[0];
                long bulk = acc * ww + (long) (lineIn[0] + lineIn[lastx]) * fw;
                lineOut[x] = (int) ((bulk + (1L << 23)) >> 24);
            }
            for (int x = edgeA; x <= lastx; x++) {
                acc += lineIn[lastx] - lineIn[x - radius - 1];
                long bulk = acc * ww + (long) (lineIn[x - radius - 1] + lineIn[lastx]) * fw;
                lineOut[x] = (int) ((bulk + (1L << 23)) >> 24);
            }
        }
    }

    private static int[][] copyOf(int[][] src, int H, int W) {
        int[][] out = new int[H][W];
        for (int y = 0; y < H; y++) {
            System.arraycopy(src[y], 0, out[y], 0, W);
        }
        return out;
    }

    private static int[][] transpose(int[][] src, int H, int W) {
        int[][] out = new int[W][H];
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                out[x][y] = src[y][x];
            }
        }
        return out;
    }

    private PilGaussianBlur() {}
}
