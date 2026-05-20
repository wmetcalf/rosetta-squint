package io.rosetta.imagehash.internal;

/**
 * Type-II DCT, no normalization. Matches scipy.fftpack.dct(x, type=2, norm=None).
 *
 * y[k] = 2 * sum_{n=0..N-1} x[n] * cos(π * k * (2n+1) / (2N))
 *
 * Direct O(N²) implementation. For pHash's N=32 (32x32 = 64 DCTs total per image)
 * this is fast enough; an FFT trick would be a micro-optimization.
 */
public final class Dct {
    /** 1-D DCT-II, no normalization. */
    public static double[] dct1d(double[] x) {
        int n = x.length;
        double[] y = new double[n];
        double factor = Math.PI / (2.0 * n);
        for (int k = 0; k < n; k++) {
            double sum = 0.0;
            for (int i = 0; i < n; i++) {
                sum += x[i] * Math.cos(factor * k * (2 * i + 1));
            }
            y[k] = 2.0 * sum;
        }
        return y;
    }

    /**
     * 2-D DCT-II via column-wise 1-D then row-wise 1-D, matching
     * Python `dct(dct(pixels, axis=0), axis=1)`.
     */
    public static double[][] dct2d(double[][] pixels) {
        int h = pixels.length;
        int w = pixels[0].length;
        double[][] mid = new double[h][w];
        double[] col = new double[h];
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) col[y] = pixels[y][x];
            double[] dctCol = dct1d(col);
            for (int y = 0; y < h; y++) mid[y][x] = dctCol[y];
        }
        double[][] out = new double[h][w];
        for (int y = 0; y < h; y++) {
            out[y] = dct1d(mid[y]);
        }
        return out;
    }

    private Dct() {}
}
