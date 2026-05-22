package io.rosetta.imagehash;

import io.rosetta.imagehash.internal.BufferedImageRgb;
import io.rosetta.imagehash.internal.FindSegments;
import io.rosetta.imagehash.internal.LanczosFixed;
import io.rosetta.imagehash.internal.PilGaussianBlur;
import io.rosetta.imagehash.internal.PilGrayscale;
import io.rosetta.imagehash.internal.PilMedianFilter;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * crop_resistant_hash: watershed-like segmentation + per-segment dhash.
 *
 * Matches Python imagehash.crop_resistant_hash exactly:
 * 1. Keep original image for per-segment cropping.
 * 2. Convert to grayscale (L mode), Lanczos resize to 300×300.
 * 3. Apply PIL GaussianBlur(radius=2).
 * 4. Apply PIL MedianFilter(size=3).
 * 5. Convert to float32 pixel array.
 * 6. _find_all_segments(threshold=128, min=500).
 * 7. If no segments: synthesize whole-image segment.
 * 8. (limit_segments not implemented for fixed v1 API)
 * 9. Per segment: compute bbox → scale → crop original → dhash(hash_size=8).
 * 10. Return ImageMultiHash.
 *
 * Reference: Python imagehash.crop_resistant_hash (lines 637-702).
 */
public final class CropResistantHash {

    private static final int SEG_SIZE = 300;
    private static final float THRESHOLD = 128.0f;
    private static final int MIN_SEGMENT_SIZE = 500;

    /**
     * Compute crop-resistant hash with default parameters.
     *
     * @param img any BufferedImage (will be internally converted to RGB)
     * @return ImageMultiHash of per-segment dhash values
     */
    public static ImageMultiHash compute(BufferedImage img) {
        return compute(img, null);
    }

    /**
     * Compute crop-resistant hash.
     *
     * @param img            original image
     * @param limitSegments  if non-null, keep only the largest N segments
     */
    public static ImageMultiHash compute(BufferedImage img, Integer limitSegments) {
        // Step 1: keep original
        BufferedImage orig = img;

        // Step 2: grayscale + Lanczos resize to 300×300
        int[][] rgb = BufferedImageRgb.toIntArray(img);
        int srcH = rgb.length;
        int srcW = rgb[0].length;

        // Convert to grayscale
        int[][] gray = new int[srcH][srcW];
        for (int y = 0; y < srcH; y++) {
            for (int x = 0; x < srcW; x++) {
                int p = rgb[y][x];
                int r = (p >> 16) & 0xFF;
                int g = (p >> 8) & 0xFF;
                int b = p & 0xFF;
                gray[y][x] = PilGrayscale.toGray(r, g, b);
            }
        }

        // Lanczos resize to 300×300 (dstW=300, dstH=300)
        int[][] resized = LanczosFixed.resize(gray, SEG_SIZE, SEG_SIZE);

        // Step 3: GaussianBlur(radius=2)
        int[][] blurred = PilGaussianBlur.apply(resized);

        // Step 4: MedianFilter(size=3)
        int[][] filtered = PilMedianFilter.apply(blurred);

        // Step 5: float32 array
        float[][] pixels = new float[SEG_SIZE][SEG_SIZE];
        for (int y = 0; y < SEG_SIZE; y++) {
            for (int x = 0; x < SEG_SIZE; x++) {
                pixels[y][x] = (float) filtered[y][x];
            }
        }

        // Step 6: find all segments
        List<FindSegments.Segment> segments =
                FindSegments.findAllSegments(pixels, THRESHOLD, MIN_SEGMENT_SIZE);

        // Step 7: whole-image fallback
        if (segments.isEmpty()) {
            // Synthesize a single whole-image segment: (0,0) and (SEG_SIZE-1, SEG_SIZE-1)
            List<int[]> wholeImg = new ArrayList<>();
            wholeImg.add(new int[]{0, 0});
            wholeImg.add(new int[]{SEG_SIZE - 1, SEG_SIZE - 1});
            segments = new ArrayList<>();
            segments.add(new FindSegments.Segment(wholeImg));
        }

        // Step 8: optional limit
        if (limitSegments != null && limitSegments < segments.size()) {
            // stable sort descending by size, keep top N
            segments = segments.stream()
                    .sorted((a, b) -> b.pixels().size() - a.pixels().size())
                    .limit(limitSegments)
                    .collect(java.util.stream.Collectors.toList());
        }

        // Step 9: per-segment hash
        int origW = orig.getWidth();
        int origH = orig.getHeight();
        double scaleW = (double) origW / SEG_SIZE;
        double scaleH = (double) origH / SEG_SIZE;

        List<ImageHash> hashes = new ArrayList<>(segments.size());
        for (FindSegments.Segment seg : segments) {
            // Compute bounding box in segmentation coords
            int minY = Integer.MAX_VALUE, minX = Integer.MAX_VALUE;
            int maxY = Integer.MIN_VALUE, maxX = Integer.MIN_VALUE;
            for (int[] p : seg.pixels()) {
                if (p[0] < minY) minY = p[0];
                if (p[0] > maxY) maxY = p[0];
                if (p[1] < minX) minX = p[1];
                if (p[1] > maxX) maxX = p[1];
            }
            // bbox in segmentation coords: (minY, minX, maxY+1, maxX+1)
            // Scale to original image coords (float), then round and convert to int.
            // Pillow 10.4 Image._crop does: x0, y0, x1, y1 = map(int, map(round, box))
            // This is round-then-int, NOT truncate-toward-zero (despite spec §3.4 wording).
            int cropLeft   = (int) Math.round(minX * scaleW);
            int cropTop    = (int) Math.round(minY * scaleH);
            int cropRight  = (int) Math.round((maxX + 1) * scaleW);
            int cropBottom = (int) Math.round((maxY + 1) * scaleH);

            // Clamp to image bounds and ensure w,h >= 1
            cropLeft   = clamp(cropLeft, 0, origW - 1);
            cropTop    = clamp(cropTop, 0, origH - 1);
            cropRight  = clamp(cropRight, cropLeft + 1, origW);
            cropBottom = clamp(cropBottom, cropTop + 1, origH);

            int cropW = cropRight - cropLeft;
            int cropH = cropBottom - cropTop;

            // getSubimage is a view of orig (no copy needed for reading)
            BufferedImage crop = orig.getSubimage(cropLeft, cropTop, cropW, cropH);
            hashes.add(DHash.compute(crop, 8));
        }

        return new ImageMultiHash(hashes);
    }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private CropResistantHash() {}
}
