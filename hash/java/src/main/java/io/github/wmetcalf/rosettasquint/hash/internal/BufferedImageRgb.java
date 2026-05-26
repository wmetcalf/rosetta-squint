package io.github.wmetcalf.rosettasquint.hash.internal;

import java.awt.image.BufferedImage;

/**
 * Normalize a BufferedImage of any type to row-major int[H][W] where each cell
 * is packed 0x00RRGGBB. Non-TYPE_INT_RGB inputs are composited against opaque
 * black using the truncated 8-bit alpha-premultiplication formula
 * <pre>
 *     out_c = (src_c * alpha) / 255   // integer truncation, NOT rounded
 * </pre>
 * matching the formula used by the Rust, Go, JS, and Swift ports.
 *
 * <p>Why the explicit loop instead of {@code Graphics2D.drawImage(...)} onto a
 * TYPE_INT_RGB background? Java's Porter-Duff {@code AlphaComposite.SrcOver}
 * uses round-half-up rounding ({@code (src*a + 127)/255}) which gives ±1 LSB
 * drift versus the truncated formula on any partial-alpha pixel. That drift
 * cascades through Lanczos / DCT / wavelet pipelines into hash bit
 * disagreements that the cross-port differential fuzzer surfaces. Doing the
 * truncated multiply in a tight pixel loop is both simpler and the
 * spec-normative behavior.</p>
 */
public final class BufferedImageRgb {
    public static int[][] toIntArray(BufferedImage src) {
        BufferedImage rgb = (src.getType() == BufferedImage.TYPE_INT_RGB)
                ? src
                : convertToIntRgb(src);
        int w = rgb.getWidth();
        int h = rgb.getHeight();
        int[][] out = new int[h][w];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                out[y][x] = rgb.getRGB(x, y) & 0xFFFFFF;
            }
        }
        return out;
    }

    /**
     * Convert any non-TYPE_INT_RGB BufferedImage into a fresh TYPE_INT_RGB
     * image whose pixels have been alpha-premultiplied against opaque black
     * via the truncated 8-bit formula. The intermediate is built row-by-row
     * to avoid the per-pixel {@code setRGB}/{@code getRGB} overhead while
     * still going through {@link BufferedImage#getRGB(int, int, int, int,
     * int[], int, int)}, which normalizes every input image type
     * (TYPE_4BYTE_ABGR, TYPE_BYTE_INDEXED, custom ColorModels, etc.) into
     * a standard packed ARGB int via Java's own ColorModel translation.
     * That gives us the alpha byte we need to multiply by, regardless of
     * the source ColorModel.
     */
    private static BufferedImage convertToIntRgb(BufferedImage src) {
        int w = src.getWidth();
        int h = src.getHeight();
        BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        int[] argb = new int[w];
        for (int y = 0; y < h; y++) {
            // getRGB always returns 0xAARRGGBB regardless of source type.
            src.getRGB(0, y, w, 1, argb, 0, w);
            for (int x = 0; x < w; x++) {
                int p = argb[x];
                int a = (p >>> 24) & 0xFF;
                if (a == 0xFF) {
                    // Fast path: fully opaque — no multiply needed.
                    // Mask to 0x00RRGGBB so TYPE_INT_RGB doesn't see stray
                    // alpha bits (it ignores them anyway, but be explicit).
                    argb[x] = p & 0x00FFFFFF;
                    continue;
                }
                int r = (p >>> 16) & 0xFF;
                int g = (p >>> 8) & 0xFF;
                int b = p & 0xFF;
                // Truncated 8-bit alpha-premul over opaque black:
                //   out_c = floor((src_c * alpha) / 255)
                // For alpha == 0 this collapses to (0,0,0). For alpha == 255
                // it's identity (handled above). For intermediate alpha it
                // matches Rust / Go / JS / Swift / Python truncated formula.
                int nr = (r * a) / 255;
                int ng = (g * a) / 255;
                int nb = (b * a) / 255;
                argb[x] = (nr << 16) | (ng << 8) | nb;
            }
            dst.setRGB(0, y, w, 1, argb, 0, w);
        }
        return dst;
    }

    private BufferedImageRgb() {}
}
