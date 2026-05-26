package io.github.wmetcalf.rosettasquint.hash.internal;

/** PIL 'HSV' conversion using PIL's integer formula (libImaging/Convert.c rgb2hsv_row). */
public final class PilHsv {
    /**
     * Returns {h, s, v} each in 0..255 for uint8 RGB input.
     * Matches PIL convert('HSV') byte-exact.
     */
    public static int[] toHsv(int r, int g, int b) {
        int maxc = Math.max(r, Math.max(g, b));
        int minc = Math.min(r, Math.min(g, b));
        int v = maxc;
        if (maxc == 0) return new int[]{0, 0, 0};
        int s = (255 * (maxc - minc)) / maxc;
        if (minc == maxc) return new int[]{0, s, v};
        int delta = maxc - minc;
        int rc = ((maxc - r) * 255) / delta;
        int gc = ((maxc - g) * 255) / delta;
        int bc = ((maxc - b) * 255) / delta;
        int hPre;
        if (r == maxc) {
            hPre = bc - gc;
        } else if (g == maxc) {
            hPre = 2 * 255 + rc - bc;
        } else {
            hPre = 4 * 255 + gc - rc;
        }
        if (hPre < 0) hPre += 6 * 255;
        int h = hPre / 6;
        return new int[]{h, s, v};
    }

    private PilHsv() {}
}
