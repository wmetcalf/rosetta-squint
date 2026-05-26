package io.github.wmetcalf.rosettasquint.hash.internal;

/**
 * Type-II DCT, no normalization. Matches scipy.fftpack.dct(x, type=2, norm=None).
 *
 * y[k] = 2 * sum_{n=0..N-1} x[n] * cos(π * k * (2n+1) / (2N))
 *
 * For power-of-two N, we compute via Makhoul's algorithm (an N-point complex FFT
 * over a permutation of x followed by a twiddle multiply), which matches
 * scipy.fftpack's FFT-based DCT and crucially produces exact zeros for inputs
 * whose DCT is mathematically zero (e.g., constant signals). pHash relies on
 * this exactness because it medians the top-left block and is otherwise
 * perturbed by O(1e-11) cancellation noise.
 *
 * For non-power-of-two N, falls back to the direct O(N²) summation.
 */
public final class Dct {
    /** 1-D DCT-II, no normalization. */
    public static double[] dct1d(double[] x) {
        int n = x.length;
        if (n >= 2 && (n & (n - 1)) == 0) {
            return dct1dMakhoul(x);
        }
        return dct1dDirect(x);
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

    /** Direct O(N²) DCT-II (used when N is not a power of two). */
    static double[] dct1dDirect(double[] x) {
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
     * Makhoul's DCT-II algorithm using an N-point complex FFT.
     *
     *   v[n]        = x[2n]            for n in [0, N/2)
     *   v[N-1-n]    = x[2n+1]          for n in [0, N/2)
     *   V[k]        = FFT(v)[k]
     *   y[k]        = 2 * Re( exp(-iπk/(2N)) * V[k] )
     *
     * This is the algorithm scipy.fftpack.dct ultimately reduces to and produces
     * bit-identical zeros for constant inputs (FFT of a constant has exact zero
     * bins except DC, so the twiddle multiply cannot introduce noise there).
     */
    static double[] dct1dMakhoul(double[] x) {
        int n = x.length;
        double[] re = new double[n];
        double[] im = new double[n];
        int half = n / 2;
        for (int i = 0; i < half; i++) {
            re[i] = x[2 * i];
            re[n - 1 - i] = x[2 * i + 1];
        }
        fftInPlace(re, im);
        double[] out = new double[n];
        for (int k = 0; k < n; k++) {
            double angle = -Math.PI * k / (2.0 * n);
            double c = Math.cos(angle);
            double s = Math.sin(angle);
            // Re((c + i*s) * (re[k] + i*im[k])) = c*re[k] - s*im[k]
            out[k] = 2.0 * (c * re[k] - s * im[k]);
        }
        return out;
    }

    /** Iterative radix-2 Cooley-Tukey FFT in-place. n must be a power of two. */
    private static void fftInPlace(double[] re, double[] im) {
        int n = re.length;
        // Bit-reversal permutation.
        int j = 0;
        for (int i = 1; i < n; i++) {
            int bit = n >> 1;
            for (; (j & bit) != 0; bit >>= 1) {
                j ^= bit;
            }
            j ^= bit;
            if (i < j) {
                double t = re[i]; re[i] = re[j]; re[j] = t;
                t = im[i]; im[i] = im[j]; im[j] = t;
            }
        }
        // Butterflies.
        for (int len = 2; len <= n; len <<= 1) {
            int half = len >> 1;
            double angle = -2.0 * Math.PI / len;
            double wRe = Math.cos(angle);
            double wIm = Math.sin(angle);
            for (int i = 0; i < n; i += len) {
                double curRe = 1.0;
                double curIm = 0.0;
                for (int k = 0; k < half; k++) {
                    int a = i + k;
                    int b = a + half;
                    double tRe = curRe * re[b] - curIm * im[b];
                    double tIm = curRe * im[b] + curIm * re[b];
                    re[b] = re[a] - tRe;
                    im[b] = im[a] - tIm;
                    re[a] = re[a] + tRe;
                    im[a] = im[a] + tIm;
                    double nRe = curRe * wRe - curIm * wIm;
                    double nIm = curRe * wIm + curIm * wRe;
                    curRe = nRe;
                    curIm = nIm;
                }
            }
        }
    }

    private Dct() {}
}
