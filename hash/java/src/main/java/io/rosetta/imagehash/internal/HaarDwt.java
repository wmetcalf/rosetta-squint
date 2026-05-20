package io.rosetta.imagehash.internal;

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
    private static final double SQRT2_INV = 1.0 / Math.sqrt(2.0);
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
        double[][] rowLow = new double[h][];
        double[][] rowHigh = new double[h][];
        for (int y = 0; y < h; y++) {
            double[][] lh = dwt1dHaar(x[y]);
            rowLow[y] = lh[0];
            rowHigh[y] = lh[1];
        }
        int outW = rowLow[0].length;
        int outH = (h + 1) / 2;
        double[][] cA = new double[outH][outW];
        double[][] cH = new double[outH][outW];
        double[][] cV = new double[outH][outW];
        double[][] cD = new double[outH][outW];
        for (int x_ = 0; x_ < outW; x_++) {
            double[] colLow = new double[h];
            double[] colHigh = new double[h];
            for (int y = 0; y < h; y++) {
                colLow[y] = rowLow[y][x_];
                colHigh[y] = rowHigh[y][x_];
            }
            double[][] lhLow = dwt1dHaar(colLow);
            double[][] lhHigh = dwt1dHaar(colHigh);
            for (int y = 0; y < outH; y++) {
                cA[y][x_] = lhLow[0][y];
                cH[y][x_] = lhLow[1][y];
                cV[y][x_] = lhHigh[0][y];
                cD[y][x_] = lhHigh[1][y];
            }
        }
        return new Dwt2Result(cA, cH, cV, cD);
    }

    public static double[][] idwt2(double[][] cA, double[][] cH, double[][] cV, double[][] cD) {
        int sh = cA.length, sw = cA[0].length;
        int outH = sh * 2;
        int outW = sw;
        double[][] rowLow = new double[outH][outW];
        double[][] rowHigh = new double[outH][outW];
        for (int x_ = 0; x_ < outW; x_++) {
            double[] cAcol = new double[sh];
            double[] cVcol = new double[sh];
            double[] cHcol = new double[sh];
            double[] cDcol = new double[sh];
            for (int y = 0; y < sh; y++) {
                cAcol[y] = cA[y][x_];
                cVcol[y] = cV[y][x_];
                cHcol[y] = cH[y][x_];
                cDcol[y] = cD[y][x_];
            }
            double[] low = idwt1dHaar(cAcol, cHcol);
            double[] high = idwt1dHaar(cVcol, cDcol);
            for (int y = 0; y < outH; y++) {
                rowLow[y][x_] = low[y];
                rowHigh[y][x_] = high[y];
            }
        }
        int finalW = outW * 2;
        double[][] out = new double[outH][finalW];
        for (int y = 0; y < outH; y++) {
            out[y] = idwt1dHaar(rowLow[y], rowHigh[y]);
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
