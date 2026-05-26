package io.github.wmetcalf.rosettasquint.hash.internal;

/** PIL 'L' (grayscale) conversion via ITU-R 601 fixed-point luma. */
public final class PilGrayscale {
    /** Returns grayscale value 0..255 for uint8 RGB input. */
    public static int toGray(int r, int g, int b) {
        return (r * 19595 + g * 38470 + b * 7471 + 32768) >>> 16;
    }

    private PilGrayscale() {}
}
