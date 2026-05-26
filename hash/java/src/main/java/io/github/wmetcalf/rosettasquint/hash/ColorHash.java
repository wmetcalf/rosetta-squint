package io.github.wmetcalf.rosettasquint.hash;

import io.github.wmetcalf.rosettasquint.hash.internal.BufferedImageRgb;
import io.github.wmetcalf.rosettasquint.hash.internal.PilGrayscale;
import io.github.wmetcalf.rosettasquint.hash.internal.PilHsv;

import java.awt.image.BufferedImage;

/**
 * colorhash: HSV-binned histogram hash. Bins:
 *   - black: L < 32
 *   - gray: L >= 32 and S < 85
 *   - 6 faint hue bins: L >= 32 and 85 <= S < 170
 *   - 6 bright hue bins: L >= 32 and S > 170
 * Quirky bin encoding (SPEC.md §8) used to pack each bin's count into binbits bits.
 */
public final class ColorHash {
    public static ImageHash compute(BufferedImage img) {
        return compute(img, 3);
    }

    public static ImageHash compute(BufferedImage img, int binbits) {
        if (binbits < 1)
            throw new IllegalArgumentException("binbits must be >= 1");
        // (1 << binbits) overflows for large binbits. Cross-port practical
        // limit is 30 (JS bitwise int range), so cap there for parity.
        // Real use is binbits 3-8.
        if (binbits > 30)
            throw new IllegalArgumentException("binbits must be <= 30");

        int[][] rgb = BufferedImageRgb.toIntArray(img);
        int h = rgb.length;
        int w = rgb[0].length;
        long n = (long) w * h;

        long blackCount = 0;
        long grayCount = 0;
        long colorfulCount = 0;
        long[] faintBins = new long[6];
        long[] brightBins = new long[6];

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int p = rgb[y][x];
                int r = (p >> 16) & 0xFF, g = (p >> 8) & 0xFF, b = p & 0xFF;
                int l = PilGrayscale.toGray(r, g, b);
                if (l < 32) {
                    blackCount++;
                    continue;
                }
                int[] hsv = PilHsv.toHsv(r, g, b);
                int s = hsv[1];
                if (s < 85) {
                    grayCount++;
                    continue;
                }
                colorfulCount++;
                int hueBin = Math.min(5, (int) (hsv[0] * 6.0 / 255.0));
                if (s < 170) {
                    faintBins[hueBin]++;
                } else if (s > 170) {
                    brightBins[hueBin]++;
                }
            }
        }

        int maxVal = 1 << binbits;
        long c = Math.max(1, colorfulCount);
        int[] values = new int[14];
        values[0] = Math.min(maxVal - 1, (int) ((double) blackCount / n * maxVal));
        values[1] = Math.min(maxVal - 1, (int) ((double) grayCount / n * maxVal));
        for (int i = 0; i < 6; i++) {
            values[2 + i] = Math.min(maxVal - 1, (int) ((double) faintBins[i] * maxVal / c));
            values[8 + i] = Math.min(maxVal - 1, (int) ((double) brightBins[i] * maxVal / c));
        }

        boolean[][] bits = new boolean[14][binbits];
        for (int i = 0; i < 14; i++) {
            boolean[] vbits = binEncode(values[i], binbits);
            System.arraycopy(vbits, 0, bits[i], 0, binbits);
        }
        return new ImageHash(bits);
    }

    /**
     * SPEC.md §8 quirky bin encoding. Verified: v=4 -> 0110, v=8 -> 1100 (not standard binary).
     */
    public static boolean[] binEncode(int v, int binbits) {
        boolean[] bits = new boolean[binbits];
        for (int i = 0; i < binbits; i++) {
            int shifted = v >>> (binbits - i - 1);
            int masked = shifted & ((1 << (binbits - i)) - 1);
            bits[i] = masked > 0;
        }
        return bits;
    }

    private ColorHash() {}
}
