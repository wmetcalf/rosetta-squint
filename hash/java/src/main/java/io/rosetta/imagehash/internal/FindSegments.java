package io.rosetta.imagehash.internal;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Pillow-compatible _find_all_segments flood fill.
 *
 * Matches Python imagehash._find_all_segments exactly:
 * 1. Find connected "hill" regions (pixels > threshold), row-major order.
 * 2. Find connected "valley" regions (pixels <= threshold), row-major order.
 * 3. Keep only regions with size > minSegmentSize (STRICT greater-than).
 *
 * Iteration order matches numpy.argwhere row-major (y-first, x-second).
 * Connectivity is 4-neighbor (up/down/left/right).
 *
 * Reference: Python imagehash._find_all_segments (lines 589-634).
 */
public final class FindSegments {

    /**
     * Result segment: ordered list of int[]{y, x} pixel coordinates,
     * sorted in the order they were added (row-major BFS from the first
     * unvisited pixel found in row-major scan).
     */
    public record Segment(List<int[]> pixels) {}

    /**
     * @param pixels         float32 grayscale array [H][W]
     * @param threshold      pixel value threshold; hills = pixels > threshold
     * @param minSegmentSize minimum size (strict >) for a segment to be kept
     * @return list of segments, hills first then valleys
     */
    public static List<Segment> findAllSegments(float[][] pixels,
                                                 float threshold,
                                                 int minSegmentSize) {
        int H = pixels.length;
        int W = pixels[0].length;

        // threshold_pixels[y][x] = pixels[y][x] > threshold
        boolean[][] above = new boolean[H][W];
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                above[y][x] = pixels[y][x] > threshold;
            }
        }

        // unassigned_pixels[y][x] = true initially
        boolean[][] unassigned = new boolean[H][W];
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                unassigned[y][x] = true;
            }
        }

        // already_segmented: set of encoded (y,x) coords
        // Python seeds with border pixels (-1,z), (H,z), (z,-1), (z,W)
        // We encode interior pixels as y*W+x in [0, H*W)
        // Use a HashSet<Long> encoding: y*bigW + x with out-of-bounds included
        // For efficiency, use a boolean[][] for in-bounds and a HashSet for out-of-bounds
        boolean[][] segmented = new boolean[H][W]; // for in-bounds coords
        // Out-of-bounds border pixels are pre-seeded in Python's already_segmented
        // but they are only needed to prevent _find_region from stepping outside
        // We model this by just using the in-bounds boolean array, and in our
        // BFS we clamp neighbors to valid range while marking them.
        // The key behavior: after processing all hills, the 'while' for valleys
        // checks: len(already_segmented) < H * W
        // already_segmented includes: border pixels (4*(H+W) entries, but
        // in Python it's 2*H + 2*W + 2*H + 2*W = 4*(H+W) border coords)
        // PLUS all pixels that were part of hill segments.
        // The border pixels are OUTSIDE the image (negative coords or >= H/W),
        // so after hills are done, already_segmented.size = borderSize + hillPixelCount
        // borderSize = H + H + W + W = 2*(H+W) in the original Python code
        // (rows: -1..H inclusive = H+2 entries, but let's count exactly)

        // Python border seeding:
        //   (-1, z) for z in range(img_height=W)  -> W entries
        //   (z, -1) for z in range(img_width=H)   -> H entries
        //   (H, z)  for z in range(W)              -> W entries
        //   (z, W)  for z in range(H)              -> H entries
        // Total: 2*W + 2*H entries
        int borderSize = 2 * H + 2 * W;

        List<Segment> segments = new ArrayList<>();

        // --- Pass 1: hill regions (above[y][x] == true) ---
        int hilledCount = 0;  // pixels assigned to hills
        for (;;) {
            // Find first unassigned hill pixel (row-major)
            int startY = -1, startX = -1;
            outer1:
            for (int y = 0; y < H; y++) {
                for (int x = 0; x < W; x++) {
                    if (above[y][x] && unassigned[y][x]) {
                        startY = y;
                        startX = x;
                        break outer1;
                    }
                }
            }
            if (startY < 0) break; // no more hill pixels

            Segment seg = floodFill(above, unassigned, segmented, H, W, startY, startX, true);
            for (int[] p : seg.pixels()) {
                unassigned[p[0]][p[1]] = false;
                hilledCount++;
            }
            if (seg.pixels().size() > minSegmentSize) {
                segments.add(seg);
            }
        }

        // --- Pass 2: valley regions (above[y][x] == false) ---
        // Python: while len(already_segmented) < img_width * img_height
        // already_segmented.size = borderSize + hilledCount + (valley pixels so far)
        // Initially: borderSize + hilledCount pixels are in already_segmented
        // The loop continues while that total < H * W
        // Since borderSize >= 0, if hilledCount >= H*W the loop doesn't run
        int alreadySegmented = borderSize + hilledCount;
        for (;;) {
            if (alreadySegmented >= H * W) break;

            // Find first unassigned valley pixel (row-major)
            int startY = -1, startX = -1;
            outer2:
            for (int y = 0; y < H; y++) {
                for (int x = 0; x < W; x++) {
                    if (!above[y][x] && unassigned[y][x]) {
                        startY = y;
                        startX = x;
                        break outer2;
                    }
                }
            }
            if (startY < 0) break;

            Segment seg = floodFill(above, unassigned, segmented, H, W, startY, startX, false);
            for (int[] p : seg.pixels()) {
                unassigned[p[0]][p[1]] = false;
            }
            alreadySegmented += seg.pixels().size();
            if (seg.pixels().size() > minSegmentSize) {
                segments.add(seg);
            }
        }

        return segments;
    }

    /**
     * BFS flood-fill from (startY, startX) over pixels matching the given polarity.
     * Adds each found pixel to segmented[][].
     *
     * The Python reference uses a set-based BFS (try_next updates via set operations).
     * We replicate row-major iteration order for in_region by sorting the BFS result.
     *
     * Actually, Python's _find_region adds pixels to in_region in a specific order:
     * - 'new_pixels' contains pixels found in this BFS wave
     * - For each pixel in new_pixels, try its 4 neighbors
     * - A neighbor is added to new_pixels (and in_region) if it passes the threshold test
     * The final set 'in_region' is returned; order doesn't matter for the segment itself,
     * but the NEXT region found is determined by row-major scan of unassigned pixels.
     * We just need the SET of pixels; the row-major re-scan happens at the outer loop level.
     */
    private static Segment floodFill(boolean[][] above, boolean[][] unassigned,
                                      boolean[][] segmented, int H, int W,
                                      int startY, int startX, boolean polarity) {
        List<int[]> inRegion = new ArrayList<>();
        Deque<int[]> queue = new ArrayDeque<>();
        // Track what we've seen (either already in segmented OR in this region)
        // We temporarily mark pixels in segmented as we add them to the region
        Set<Long> notInRegion = new HashSet<>();

        int[] start = {startY, startX};
        inRegion.add(start);
        segmented[startY][startX] = true;
        queue.add(start);

        int[][] dirs = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};

        while (!queue.isEmpty()) {
            int[] cur = queue.poll();
            int cy = cur[0], cx = cur[1];
            for (int[] d : dirs) {
                int ny = cy + d[0], nx = cx + d[1];
                // Out of bounds: these are the border pixels pre-seeded in Python's
                // already_segmented; skip them (they are implicitly "already visited")
                if (ny < 0 || ny >= H || nx < 0 || nx >= W) continue;
                // Already in segmented (hill or previously visited)
                if (segmented[ny][nx]) continue;
                // Already marked as not-in-region by this BFS
                long key = (long) ny * W + nx;
                if (notInRegion.contains(key)) continue;

                if (above[ny][nx] == polarity && unassigned[ny][nx]) {
                    inRegion.add(new int[]{ny, nx});
                    segmented[ny][nx] = true;
                    queue.add(new int[]{ny, nx});
                } else {
                    notInRegion.add(key);
                }
            }
        }

        return new Segment(inRegion);
    }

    private FindSegments() {}
}
