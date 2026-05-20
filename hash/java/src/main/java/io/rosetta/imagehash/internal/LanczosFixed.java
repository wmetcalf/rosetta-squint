package io.rosetta.imagehash.internal;

/**
 * Pillow-compatible Lanczos3 resize on uint8 grayscale (int[][] in [0,255]).
 *
 * Implements the algorithm from libImaging/Resample.c:
 *   - separable horizontal then vertical pass
 *   - center_of_pixel = (idx + 0.5) * scale
 *   - filter_scale = max(1.0, scale)  (kernel widens on downsample only)
 *   - support = 3.0 * filter_scale
 *   - kernel = sinc(x) * sinc(x/3) for |x| < 3
 *   - per-output-pixel weight normalization
 *   - 32-bit fixed-point integer coefficients with int64 accumulator
 *   - output clipped to [0, 255]
 *
 * Byte-exact match against PIL on the lanczos_cases reference vectors.
 */
public final class LanczosFixed {
    // PIL uses PRECISION_BITS = 32 - 8 - 2 = 22 in Resample.c. The "32" in the
    // spec narrative refers to the int32 storage type for weights; the shift is 22.
    // With weights normalized to ~1.0, 1.0 * 2^22 = 4194304 fits cleanly in int32,
    // while 1.0 * 2^32 would overflow int32 entirely.
    private static final int PRECISION_BITS = 32 - 8 - 2;
    private static final double SUPPORT = 3.0;

    /** Resize uint8 src[H][W] to int[dstH][dstW] of uint8 values via Lanczos3. */
    public static int[][] resize(int[][] src, int dstW, int dstH) {
        int srcH = src.length;
        int srcW = src[0].length;

        // Horizontal pass: src[srcH][srcW] -> mid[srcH][dstW]
        int[][] coeffsH = precomputeCoeffs(srcW, dstW);
        int[] offsetsH = computeOffsets(srcW, dstW);
        int kSizeH = coeffsH[0].length;
        int[][] mid = new int[srcH][dstW];
        for (int y = 0; y < srcH; y++) {
            for (int xd = 0; xd < dstW; xd++) {
                long acc = 0;
                int[] w = coeffsH[xd];
                int off = offsetsH[xd];
                int[] row = src[y];
                for (int i = 0; i < kSizeH; i++) {
                    int xs = off + i;
                    if (xs < 0 || xs >= srcW) continue;
                    acc += (long) w[i] * row[xs];
                }
                mid[y][xd] = clip8(acc);
            }
        }

        // Vertical pass: mid[srcH][dstW] -> out[dstH][dstW]
        int[][] coeffsV = precomputeCoeffs(srcH, dstH);
        int[] offsetsV = computeOffsets(srcH, dstH);
        int kSizeV = coeffsV[0].length;
        int[][] out = new int[dstH][dstW];
        for (int yd = 0; yd < dstH; yd++) {
            int[] w = coeffsV[yd];
            int off = offsetsV[yd];
            for (int x = 0; x < dstW; x++) {
                long acc = 0;
                for (int i = 0; i < kSizeV; i++) {
                    int ys = off + i;
                    if (ys < 0 || ys >= srcH) continue;
                    acc += (long) w[i] * mid[ys][x];
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

    private static int[][] precomputeCoeffs(int srcSize, int dstSize) {
        double scale = (double) srcSize / dstSize;
        double filterScale = Math.max(1.0, scale);
        double support = SUPPORT * filterScale;
        int kSize = (int) Math.ceil(support * 2) + 1;
        int[][] coeffs = new int[dstSize][kSize];
        for (int xd = 0; xd < dstSize; xd++) {
            double center = (xd + 0.5) * scale;
            int xmin = (int) Math.ceil(center - support);
            int xmax = (int) Math.floor(center + support);
            if (xmin < 0) xmin = 0;
            if (xmax > srcSize - 1) xmax = srcSize - 1;
            int n = xmax - xmin + 1;
            double[] tmp = new double[n];
            double wsum = 0.0;
            for (int i = 0; i < n; i++) {
                double dx = (xmin + i + 0.5 - center) / filterScale;
                double w = lanczosKernel(dx);
                tmp[i] = w;
                wsum += w;
            }
            if (wsum != 0.0) {
                for (int i = 0; i < n; i++) tmp[i] /= wsum;
            }
            for (int i = 0; i < n; i++) {
                coeffs[xd][i] = (int) Math.round(tmp[i] * (1L << PRECISION_BITS));
            }
        }
        return coeffs;
    }

    private static int[] computeOffsets(int srcSize, int dstSize) {
        double scale = (double) srcSize / dstSize;
        double filterScale = Math.max(1.0, scale);
        double support = SUPPORT * filterScale;
        int[] offsets = new int[dstSize];
        for (int xd = 0; xd < dstSize; xd++) {
            double center = (xd + 0.5) * scale;
            int xmin = (int) Math.ceil(center - support);
            if (xmin < 0) xmin = 0;
            offsets[xd] = xmin;
        }
        return offsets;
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
