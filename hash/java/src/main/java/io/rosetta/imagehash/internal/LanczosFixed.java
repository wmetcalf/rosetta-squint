package io.rosetta.imagehash.internal;

/**
 * Pillow-compatible Lanczos3 resize on uint8 grayscale (int[][] in [0,255]).
 *
 * Implements the algorithm from libImaging/Resample.c precisely:
 *   - center_of_pixel = (idx + 0.5) * scale
 *   - filter_scale = max(1.0, scale)
 *   - support = 3.0 * filter_scale
 *   - kernel = sinc(x) * sinc(x/3) for |x| < 3
 *   - xmin = (int)(center - support + 0.5), clamped to [0, srcSize)
 *   - xmax = (int)(center + support + 0.5), clamped to (xmin, srcSize] (EXCLUSIVE upper bound)
 *   - weights normalized per output pixel
 *   - PRECISION_BITS = 32 - 8 - 2 = 22 for fixed-point coefficients
 *   - acc accumulated in int64
 */
public final class LanczosFixed {
    private static final int PRECISION_BITS = 32 - 8 - 2; // = 22
    private static final double SUPPORT = 3.0;

    public static int[][] resize(int[][] src, int dstW, int dstH) {
        int srcH = src.length;
        int srcW = src[0].length;

        // Horizontal pass
        Coeffs cH = precomputeCoeffs(srcW, dstW);
        int[][] mid = new int[srcH][dstW];
        for (int y = 0; y < srcH; y++) {
            for (int xd = 0; xd < dstW; xd++) {
                long acc = 0;
                int off = cH.offsets[xd];
                int len = cH.lengths[xd];
                int[] w = cH.weights[xd];
                int[] row = src[y];
                for (int i = 0; i < len; i++) {
                    acc += (long) w[i] * row[off + i];
                }
                mid[y][xd] = clip8(acc);
            }
        }

        // Vertical pass
        Coeffs cV = precomputeCoeffs(srcH, dstH);
        int[][] out = new int[dstH][dstW];
        for (int yd = 0; yd < dstH; yd++) {
            int off = cV.offsets[yd];
            int len = cV.lengths[yd];
            int[] w = cV.weights[yd];
            for (int x = 0; x < dstW; x++) {
                long acc = 0;
                for (int i = 0; i < len; i++) {
                    acc += (long) w[i] * mid[off + i][x];
                }
                out[yd][x] = clip8(acc);
            }
        }
        return out;
    }

    private static int clip8(long acc) {
        long rounded = (acc + (1L << (PRECISION_BITS - 1))) >> PRECISION_BITS;
        if (rounded < 0) return 0;
        if (rounded > 255) return 255;
        return (int) rounded;
    }

    /** Holds per-output-pixel offsets, lengths, and weight arrays. */
    private static final class Coeffs {
        final int[] offsets;
        final int[] lengths;
        final int[][] weights; // weights[dstIdx] = int[length] of quantized coefficients
        Coeffs(int[] offsets, int[] lengths, int[][] weights) {
            this.offsets = offsets;
            this.lengths = lengths;
            this.weights = weights;
        }
    }

    private static Coeffs precomputeCoeffs(int srcSize, int dstSize) {
        double scale = (double) srcSize / dstSize;
        double filterScale = Math.max(1.0, scale);
        double support = SUPPORT * filterScale;

        int[] offsets = new int[dstSize];
        int[] lengths = new int[dstSize];
        int[][] weights = new int[dstSize][];

        for (int xd = 0; xd < dstSize; xd++) {
            double center = (xd + 0.5) * scale;
            int xmin = (int) (center - support + 0.5);
            if (xmin < 0) xmin = 0;
            int xmax = (int) (center + support + 0.5);
            if (xmax > srcSize) xmax = srcSize;
            int len = xmax - xmin;
            if (len < 0) len = 0;

            double[] tmp = new double[len];
            double wsum = 0.0;
            for (int i = 0; i < len; i++) {
                double dx = (xmin + i + 0.5 - center) / filterScale;
                double w = lanczosKernel(dx);
                tmp[i] = w;
                wsum += w;
            }
            if (wsum != 0.0) {
                for (int i = 0; i < len; i++) tmp[i] /= wsum;
            }

            int[] quantized = new int[len];
            for (int i = 0; i < len; i++) {
                quantized[i] = (int) Math.round(tmp[i] * (1L << PRECISION_BITS));
            }
            offsets[xd] = xmin;
            lengths[xd] = len;
            weights[xd] = quantized;
        }
        return new Coeffs(offsets, lengths, weights);
    }

    private static double lanczosKernel(double x) {
        if (x == 0.0) return 1.0;
        double ax = Math.abs(x);
        if (ax >= SUPPORT) return 0.0;
        double px = Math.PI * x;
        return (Math.sin(px) / px) * (Math.sin(px / SUPPORT) / (px / SUPPORT));
    }

    private LanczosFixed() {}
}
