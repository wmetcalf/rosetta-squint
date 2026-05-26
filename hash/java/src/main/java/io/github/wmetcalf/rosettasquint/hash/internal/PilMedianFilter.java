package io.github.wmetcalf.rosettasquint.hash.internal;

import java.util.Arrays;

/**
 * Pillow-compatible MedianFilter with size=3 (3×3 windowed median).
 *
 * Matches PIL.ImageFilter.MedianFilter(size=3) exactly:
 * - For each pixel, collect the 9 values in the 3×3 window, clamping (edge-replicating)
 *   at borders so e.g. pixel (0,0) contributes 4 times.
 * - Sort the 9 values, return the value at index 4 (the median of 9).
 *
 * Reference: PIL spec §3.2 (parity hazard 3.2).
 */
public final class PilMedianFilter {

    /**
     * Apply PIL MedianFilter(size=3) to a uint8 grayscale image.
     *
     * @param src  row-major uint8 array [H][W], values 0-255
     * @return new [H][W] array with median filter applied
     */
    public static int[][] apply(int[][] src) {
        int H = src.length;
        int W = src[0].length;
        int[][] out = new int[H][W];
        int[] window = new int[9];

        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                int idx = 0;
                for (int dy = -1; dy <= 1; dy++) {
                    int sy = clamp(y + dy, 0, H - 1);
                    for (int dx = -1; dx <= 1; dx++) {
                        int sx = clamp(x + dx, 0, W - 1);
                        window[idx++] = src[sy][sx];
                    }
                }
                Arrays.sort(window);
                out[y][x] = window[4];
            }
        }
        return out;
    }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private PilMedianFilter() {}
}
