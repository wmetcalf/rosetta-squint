package io.rosetta.imagehash.internal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Daubechies-4 (db4) DWT/IDWT with PyWavelets 'symmetric' boundary mode.
 *
 * <p>Filter bank (length 8) taken directly from {@code pywt.Wavelet('db4')}:
 * <ul>
 *   <li>Analysis lowpass  (dec_lo): convolution with even downsampling, symmetric extension.</li>
 *   <li>Analysis highpass (dec_hi): same.</li>
 *   <li>Synthesis lowpass  (rec_lo): linear (zero-padded) convolution then slice.</li>
 *   <li>Synthesis highpass (rec_hi): same.</li>
 * </ul>
 *
 * <h2>Forward DWT (analysis)</h2>
 * For 1-D input {@code x} of length {@code n}:
 * <ol>
 *   <li>Extend symmetrically by {@code FILTER_LEN - 1 = 7} samples on each side using
 *       <em>whole-sample symmetric</em> (period {@code 2n}) reflection.</li>
 *   <li>For each output index {@code i} in {@code [0, out_len)}, where
 *       {@code out_len = (n + 7) / 2}:
 *       {@code cA[i] = dot(dec_lo_reversed, x_ext[2i+1 .. 2i+1+8])}</li>
 *   <li>Same for {@code cD} with {@code dec_hi_reversed}.</li>
 * </ol>
 *
 * <h2>Inverse DWT (synthesis)</h2>
 * For coefficient arrays {@code cA, cD} each of length {@code half}, recovering
 * original length {@code out_len}:
 * <ol>
 *   <li>Upsample: insert a zero after each sample → arrays of length {@code 2*half}.</li>
 *   <li>Linear (zero-padded) convolution of each upsampled array with rec_lo / rec_hi.</li>
 *   <li>Add the two convolved arrays.</li>
 *   <li>Slice {@code [FILTER_LEN-2 .. FILTER_LEN-2+out_len]} = {@code [6 .. 6+out_len]}.</li>
 * </ol>
 *
 * <h2>2-D DWT</h2>
 * Columns first (axis 0), then rows (axis 1) — matching pywt's evaluation order.
 *
 * <h2>wavedec2 / waverec2</h2>
 * Multi-level recursion on the LL band. Sizes at each level are stored so that
 * {@code waverec2} can reconstruct with the exact original dimensions.
 */
public final class Db4Dwt {

    // Coefficients from pywt.Wavelet('db4') — double literals match IEEE-754 exactly.
    private static final double[] DEC_LO = {
        -0.010597401785069032,  0.0328830116668852,    0.030841381835560764,
        -0.18703481171909309,  -0.027983769416859854,  0.6308807679298589,
         0.7148465705529157,    0.2303778133088965
    };
    private static final double[] DEC_HI = {
        -0.2303778133088965,    0.7148465705529157,   -0.6308807679298589,
        -0.027983769416859854,  0.18703481171909309,   0.030841381835560764,
        -0.0328830116668852,   -0.010597401785069032
    };
    private static final double[] REC_LO = {
         0.2303778133088965,    0.7148465705529157,    0.6308807679298589,
        -0.027983769416859854, -0.18703481171909309,   0.030841381835560764,
         0.0328830116668852,   -0.010597401785069032
    };
    private static final double[] REC_HI = {
        -0.010597401785069032, -0.0328830116668852,    0.030841381835560764,
         0.18703481171909309,  -0.027983769416859854, -0.6308807679298589,
         0.7148465705529157,   -0.2303778133088965
    };

    private static final int FILTER_LEN = 8;  // DEC_LO.length

    // -------------------------------------------------------------------------
    // Public data structures
    // -------------------------------------------------------------------------

    public static final class WavedecResult {
        public final double[][] cA;
        /** Details in pywt order: index 0 is outermost level (last decomposed). */
        public final List<double[][][]> details;  // each [cH, cV, cD]
        /** Original (h, w) of the input at each level; index 0 = outermost. */
        public final List<int[]> sizes;

        public WavedecResult(double[][] cA, List<double[][][]> details, List<int[]> sizes) {
            this.cA = cA;
            this.details = details;
            this.sizes = sizes;
        }
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public static WavedecResult wavedec2(double[][] x, int level) {
        double[][] current = x;
        List<double[][][]> details = new ArrayList<>();
        List<int[]> sizes = new ArrayList<>();
        for (int l = 0; l < level; l++) {
            int h = current.length, w = current[0].length;
            sizes.add(new int[]{h, w});
            double[][][] r = dwt2(current);
            details.add(r);
            current = r[0];  // cA
        }
        // Reverse to pywt order (outermost first)
        Collections.reverse(details);
        Collections.reverse(sizes);
        return new WavedecResult(current, details, sizes);
    }

    public static double[][] waverec2(WavedecResult dec) {
        double[][] current = dec.cA;
        for (int i = 0; i < dec.details.size(); i++) {
            double[][][] detail = dec.details.get(i);
            int[] sz = dec.sizes.get(i);
            double[][] cA = current;
            double[][] cH = detail[1];
            double[][] cV = detail[2];
            double[][] cD = detail[3];
            current = idwt2(cA, cH, cV, cD, sz[0], sz[1]);
        }
        return current;
    }

    // -------------------------------------------------------------------------
    // Internal: 2-D DWT / IDWT
    // -------------------------------------------------------------------------

    /**
     * Returns [cA, cH, cV, cD] (not a Haar-style Dwt2Result; simpler array).
     * Columns (axis 0) processed first, then rows (axis 1).
     */
    private static double[][][] dwt2(double[][] x) {
        int h = x.length, w = x[0].length;
        int outH = (h + FILTER_LEN - 1) / 2;
        int outW = (w + FILTER_LEN - 1) / 2;

        // Pass 1: along columns
        double[][] colLo = new double[outH][w];
        double[][] colHi = new double[outH][w];
        double[] col = new double[h];
        for (int c = 0; c < w; c++) {
            for (int r = 0; r < h; r++) col[r] = x[r][c];
            double[][] ld = dwt1d(col);
            for (int r = 0; r < outH; r++) {
                colLo[r][c] = ld[0][r];
                colHi[r][c] = ld[1][r];
            }
        }

        // Pass 2: along rows
        double[][] cA = new double[outH][outW];
        double[][] cH = new double[outH][outW];
        double[][] cV = new double[outH][outW];
        double[][] cD = new double[outH][outW];
        for (int r = 0; r < outH; r++) {
            double[][] ldLo = dwt1d(colLo[r]);
            double[][] ldHi = dwt1d(colHi[r]);
            // colLo → low → cA; colLo → high → cV
            // colHi → low → cH; colHi → high → cD
            cA[r] = ldLo[0];
            cV[r] = ldLo[1];
            cH[r] = ldHi[0];
            cD[r] = ldHi[1];
        }

        return new double[][][]{cA, cH, cV, cD};
    }

    /**
     * Inverse 2-D DWT with explicit output dimensions {@code (inH, inW)}.
     * Rows first (undo axis 1), then columns (undo axis 0).
     */
    private static double[][] idwt2(double[][] cA, double[][] cH, double[][] cV, double[][] cD,
                                    int inH, int inW) {
        int outH = cA.length;

        // Undo row pass
        double[][] colLo = new double[outH][inW];
        double[][] colHi = new double[outH][inW];
        for (int r = 0; r < outH; r++) {
            colLo[r] = idwt1d(cA[r], cV[r], inW);
            colHi[r] = idwt1d(cH[r], cD[r], inW);
        }

        // Undo column pass
        double[][] out = new double[inH][inW];
        double[] lo = new double[outH];
        double[] hi = new double[outH];
        for (int c = 0; c < inW; c++) {
            for (int r = 0; r < outH; r++) {
                lo[r] = colLo[r][c];
                hi[r] = colHi[r][c];
            }
            double[] recon = idwt1d(lo, hi, inH);
            for (int r = 0; r < inH; r++) out[r][c] = recon[r];
        }
        return out;
    }

    // -------------------------------------------------------------------------
    // Internal: 1-D DWT / IDWT
    // -------------------------------------------------------------------------

    /**
     * 1-D forward DWT. Returns [cA, cD].
     * Uses whole-sample symmetric extension (period 2n), offset 1.
     */
    private static double[][] dwt1d(double[] x) {
        int n = x.length;
        int outLen = (n + FILTER_LEN - 1) / 2;
        int pad = FILTER_LEN - 1;  // = 7

        // Build extended signal
        double[] ext = new double[pad + n + pad];
        for (int i = 0; i < ext.length; i++) {
            ext[i] = x[reflectIdx(i - pad, n)];
        }

        double[] cA = new double[outLen];
        double[] cD = new double[outLen];
        for (int i = 0; i < outLen; i++) {
            int start = 2 * i + 1;
            double a = 0.0, d = 0.0;
            for (int k = 0; k < FILTER_LEN; k++) {
                // PyWavelets accumulation order: DEC_LO[k] * ext[start + FILTER_LEN-1-k]
                // This matches pywt's C inner loop and produces exact zeros for
                // structured inputs (e.g., alternating ±0.5) where our reversed
                // order would accumulate to ~1e-17 instead of 0.0.
                double v = ext[start + FILTER_LEN - 1 - k];
                a += DEC_LO[k] * v;
                d += DEC_HI[k] * v;
            }
            cA[i] = a;
            cD[i] = d;
        }
        return new double[][]{cA, cD};
    }

    /**
     * 1-D inverse DWT. Upsample cA/cD, linear convolve with rec_lo/rec_hi,
     * sum, then slice {@code [FILTER_LEN-2 .. FILTER_LEN-2+outLen]}.
     */
    private static double[] idwt1d(double[] cA, double[] cD, int outLen) {
        int half = cA.length;
        int upLen = 2 * half;

        // Linear convolution: result length = upLen + FILTER_LEN - 1
        int convLen = upLen + FILTER_LEN - 1;
        double[] conv = new double[convLen];

        // Convolve with rec_lo (cA upsampled, zeros at odd indices)
        for (int i = 0; i < half; i++) {
            double a = cA[i];
            double d = cD[i];
            int base = 2 * i;  // position of sample in upsampled array
            for (int k = 0; k < FILTER_LEN; k++) {
                conv[base + k] += REC_LO[k] * a;
                conv[base + k] += REC_HI[k] * d;
            }
        }

        // Slice [FILTER_LEN-2 .. FILTER_LEN-2+outLen]
        int start = FILTER_LEN - 2;  // = 6
        double[] out = new double[outLen];
        System.arraycopy(conv, start, out, 0, outLen);
        return out;
    }

    // -------------------------------------------------------------------------
    // Utility
    // -------------------------------------------------------------------------

    /**
     * Map index {@code i} to {@code [0, n)} via whole-sample symmetric reflection
     * (period {@code 2n}).  Matches PyWavelets 'symmetric' boundary mode.
     */
    private static int reflectIdx(int i, int n) {
        if (n == 1) return 0;
        int period = 2 * n;
        i = i % period;
        if (i < 0) i += period;
        if (i >= n) i = period - 1 - i;
        return i;
    }

    private Db4Dwt() {}
}
