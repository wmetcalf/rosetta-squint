package io.github.wmetcalf.rosettasquint.hash.internal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Haar DWT/IDWT with pywt's 'symmetric' boundary mode.
 *
 * For Haar (filter length 2) on power-of-2 inputs, this reduces to the simple
 * (a+b)/sqrt(2) / (a-b)/sqrt(2) pair without any boundary handling.
 */
public final class HaarDwt {
    // Use Math.sqrt(0.5) to match pywt's filter coefficient 0.7071067811865476
    // (1.0/Math.sqrt(2.0) yields 0.7071067811865475 — one ULP lower — causing
    // accumulated rounding mismatches in deep wavedec/waverec stacks).
    private static final double SQRT2_INV = Math.sqrt(0.5);
    private static final double[] LOWPASS = {SQRT2_INV, SQRT2_INV};
    private static final double[] HIGHPASS = {SQRT2_INV, -SQRT2_INV};

    public static final class Dwt2Result {
        public final double[][] cA, cH, cV, cD;
        public Dwt2Result(double[][] cA, double[][] cH, double[][] cV, double[][] cD) {
            this.cA = cA; this.cH = cH; this.cV = cV; this.cD = cD;
        }
    }

    public static final class WavedecResult {
        public final double[][] cA;
        public final List<double[][][]> details; // each element is (cH, cV, cD)
        public WavedecResult(double[][] cA, List<double[][][]> details) {
            this.cA = cA; this.details = details;
        }
    }

    public static Dwt2Result dwt2(double[][] x) {
        int h = x.length, w = x[0].length;
        // Match pywt's evaluation order: filter along axis=-2 (cols) FIRST,
        // then along axis=-1 (rows). This is bit-exact only when the order
        // of floating-point additions matches pywt.
        int outH = (h + 1) / 2;
        int outW = (w + 1) / 2;
        double[][] colLow = new double[outH][w];
        double[][] colHigh = new double[outH][w];
        for (int x_ = 0; x_ < w; x_++) {
            double[] col = new double[h];
            for (int y = 0; y < h; y++) col[y] = x[y][x_];
            double[][] lh = dwt1dHaar(col);
            for (int y = 0; y < outH; y++) {
                colLow[y][x_] = lh[0][y];
                colHigh[y][x_] = lh[1][y];
            }
        }
        double[][] cA = new double[outH][outW];
        double[][] cH = new double[outH][outW];
        double[][] cV = new double[outH][outW];
        double[][] cD = new double[outH][outW];
        for (int y = 0; y < outH; y++) {
            double[][] lhL = dwt1dHaar(colLow[y]);
            double[][] lhH = dwt1dHaar(colHigh[y]);
            // colLow row -> low along cols (axis=-1) = cA; high along cols (axis=-1) = cV
            // colHigh row -> low = cH; high = cD
            cA[y] = lhL[0];
            cV[y] = lhL[1];
            cH[y] = lhH[0];
            cD[y] = lhH[1];
        }
        return new Dwt2Result(cA, cH, cV, cD);
    }

    public static double[][] idwt2(double[][] cA, double[][] cH, double[][] cV, double[][] cD) {
        // Inverse of cols-first dwt2: undo axis=-1 (rows) first to recover
        // colLow (from cA,cV) and colHigh (from cH,cD), then undo axis=-2 (cols).
        int sh = cA.length, sw = cA[0].length;
        int outH = sh * 2;
        int outW = sw * 2;
        double[][] colLow = new double[sh][outW];
        double[][] colHigh = new double[sh][outW];
        for (int y = 0; y < sh; y++) {
            colLow[y] = idwt1dHaar(cA[y], cV[y]);   // low+high along cols → colLow
            colHigh[y] = idwt1dHaar(cH[y], cD[y]);  // low+high along cols → colHigh
        }
        double[][] out = new double[outH][outW];
        for (int x_ = 0; x_ < outW; x_++) {
            double[] cl = new double[sh];
            double[] ch = new double[sh];
            for (int y = 0; y < sh; y++) {
                cl[y] = colLow[y][x_];
                ch[y] = colHigh[y][x_];
            }
            double[] col = idwt1dHaar(cl, ch);
            for (int y = 0; y < outH; y++) out[y][x_] = col[y];
        }
        return out;
    }

    public static WavedecResult wavedec2(double[][] x, int level) {
        double[][] current = x;
        List<double[][][]> details = new ArrayList<>();
        for (int l = 0; l < level; l++) {
            Dwt2Result r = dwt2(current);
            details.add(new double[][][]{r.cH, r.cV, r.cD});
            current = r.cA;
        }
        Collections.reverse(details);
        return new WavedecResult(current, details);
    }

    public static double[][] waverec2(WavedecResult dec) {
        double[][] current = dec.cA;
        for (double[][][] level : dec.details) {
            current = idwt2(current, level[0], level[1], level[2]);
        }
        return current;
    }

    private static double[][] dwt1dHaar(double[] x) {
        int n = x.length;
        if ((n & 1) != 0) {
            double[] xExt = new double[n + 1];
            System.arraycopy(x, 0, xExt, 0, n);
            xExt[n] = x[n - 1];
            x = xExt;
            n = n + 1;
        }
        int half = n / 2;
        double[] low = new double[half];
        double[] high = new double[half];
        for (int i = 0; i < half; i++) {
            double a = x[2 * i];
            double b = x[2 * i + 1];
            low[i] = LOWPASS[0] * a + LOWPASS[1] * b;
            high[i] = HIGHPASS[0] * a + HIGHPASS[1] * b;
        }
        return new double[][]{low, high};
    }

    private static double[] idwt1dHaar(double[] low, double[] high) {
        int half = low.length;
        int n = half * 2;
        double[] out = new double[n];
        for (int i = 0; i < half; i++) {
            out[2 * i] = LOWPASS[0] * low[i] + HIGHPASS[0] * high[i];
            out[2 * i + 1] = LOWPASS[1] * low[i] + HIGHPASS[1] * high[i];
        }
        return out;
    }

    private HaarDwt() {}
}
