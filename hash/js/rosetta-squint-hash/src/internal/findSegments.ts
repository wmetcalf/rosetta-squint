/**
 * _find_all_segments — byte-exact port of Python imagehash._find_all_segments.
 *
 * Finds connected regions in a float32 pixel array (H×W, row-major) using
 * 4-neighbor flood fill.  "Hills" (> threshold) are found first, then
 * "valleys" (<= threshold).  Row-major iteration order (y-then-x) for
 * selecting the start pixel of each new region is required by the spec.
 *
 * Each segment is a set of [y, x] coordinate pairs. Only segments with
 * strictly more than minSegmentSize pixels are kept.
 *
 * Reference: spec/segmentation_cases.json.
 */

/** A segment is a list of [y, x] pairs. */
export type Segment = Array<[number, number]>;

/**
 * findAllSegments — port of imagehash._find_all_segments.
 *
 * @param pixels        Float32 pixel values, row-major, length = height * width.
 * @param height        Number of rows.
 * @param width         Number of columns.
 * @param threshold     Hill/valley boundary (default 128).
 * @param minSegmentSize Minimum segment size — segments with <= this many pixels are discarded (default 500).
 */
export function findAllSegments(
  pixels: Float32Array,
  height: number,
  width: number,
  threshold = 128,
  minSegmentSize = 500,
): Segment[] {
  // thresholdPixels[y * width + x] = true if pixels[y,x] > threshold
  const thresholdPixels = new Uint8Array(height * width);
  for (let i = 0; i < height * width; i++) {
    thresholdPixels[i] = pixels[i] > threshold ? 1 : 0;
  }

  // unassignedPixels[y * width + x] = true initially, cleared as we assign
  const unassignedPixels = new Uint8Array(height * width).fill(1);

  // alreadySegmented is a Set of encoded coordinates.
  // We encode (row, col) as row * (width + 2) + col + 1 shifted to handle negatives:
  // Use string keys for out-of-bound border pixels, numeric keys for in-bounds.
  // Simpler: encode as (row + 1) * (width + 2) + (col + 1) to shift [-1..H] x [-1..W] positive.
  const STRIDE = width + 2; // stride for shifted encoding
  const alreadySegmented = new Set<number>();

  // Add border pixels: row=-1, row=H, col=-1, col=W
  // Encoded: (row + 1) * STRIDE + (col + 1)
  // Row -1: encoded row = 0
  for (let c = 0; c < width; c++) {
    alreadySegmented.add(0 * STRIDE + (c + 1));
  }
  // Row H: encoded row = H + 1
  for (let c = 0; c < width; c++) {
    alreadySegmented.add((height + 1) * STRIDE + (c + 1));
  }
  // Col -1: encoded col = 0
  for (let r = 0; r < height; r++) {
    alreadySegmented.add((r + 1) * STRIDE + 0);
  }
  // Col W: encoded col = W + 1
  for (let r = 0; r < height; r++) {
    alreadySegmented.add((r + 1) * STRIDE + (width + 1));
  }

  const segments: Segment[] = [];

  function encode(r: number, c: number): number {
    return (r + 1) * STRIDE + (c + 1);
  }

  /**
   * BFS flood-fill starting from the first available pixel in the masked set.
   * remainingMask[y * width + x] = 1 if the pixel is available in this pass.
   * Returns the found region as a list of [y, x] pairs, and updates alreadySegmented.
   */
  function findRegion(remainingMask: Uint8Array): Segment {
    // Find first available pixel in row-major order (y then x)
    let startY = -1, startX = -1;
    for (let i = 0; i < height * width; i++) {
      if (remainingMask[i]) {
        startY = Math.trunc(i / width);
        startX = i % width;
        break;
      }
    }
    if (startY === -1) return [];

    const inRegion = new Set<number>();
    const notInRegion = new Set<number>();

    const startKey = encode(startY, startX);
    inRegion.add(startKey);
    alreadySegmented.add(startKey);

    // BFS: newPixels = set of pixels added in the last wave
    let newPixels: Array<[number, number]> = [[startY, startX]];

    while (newPixels.length > 0) {
      const tryNext: Array<[number, number]> = [];
      const tryNextKeys = new Set<number>();

      for (const [py, px] of newPixels) {
        const neighbors: Array<[number, number]> = [
          [py - 1, px],
          [py + 1, px],
          [py, px - 1],
          [py, px + 1],
        ];
        for (const [ny, nx] of neighbors) {
          const nk = encode(ny, nx);
          if (!alreadySegmented.has(nk) && !notInRegion.has(nk) && !tryNextKeys.has(nk)) {
            tryNext.push([ny, nx]);
            tryNextKeys.add(nk);
          }
        }
      }

      newPixels = [];
      for (const [ny, nx] of tryNext) {
        // Valid in-bounds coordinates (border guards handle out-of-bounds via alreadySegmented)
        const idx = ny * width + nx;
        if (remainingMask[idx]) {
          const nk = encode(ny, nx);
          inRegion.add(nk);
          alreadySegmented.add(nk);
          newPixels.push([ny, nx]);
        } else {
          notInRegion.add(encode(ny, nx));
        }
      }
    }

    // Convert back to [y, x] pairs — sort in row-major order to match numpy argwhere
    const result: Segment = [];
    for (let y = 0; y < height; y++) {
      for (let x = 0; x < width; x++) {
        if (inRegion.has(encode(y, x))) {
          result.push([y, x]);
        }
      }
    }
    return result;
  }

  // Find all "hill" regions (pixels > threshold)
  {
    // Combined mask: thresholdPixels AND unassignedPixels
    while (true) {
      // Check if any unassigned hill pixel exists
      let hasHill = false;
      const remainingMask = new Uint8Array(height * width);
      for (let i = 0; i < height * width; i++) {
        if (thresholdPixels[i] && unassignedPixels[i]) {
          remainingMask[i] = 1;
          hasHill = true;
        }
      }
      if (!hasHill) break;

      const segment = findRegion(remainingMask);
      if (segment.length > minSegmentSize) {
        segments.push(segment);
      }
      // Mark all pixels in segment as assigned
      for (const [y, x] of segment) {
        unassignedPixels[y * width + x] = 0;
      }
    }
  }

  // Find all "valley" regions (pixels <= threshold).
  // Python termination: `while len(already_segmented) < img_width * img_height`
  // where img_width * img_height = H * W (pixels.shape).
  // already_segmented already contains 2*(H+W) border pixels before any pixels are
  // added.  For small images where 2*(H+W) >= H*W (e.g. 4×4) the condition is
  // immediately false and the valley loop is skipped entirely.
  {
    while (alreadySegmented.size < height * width) {
      // Build mask: valley pixels that are still unassigned
      let hasValley = false;
      const remainingMask = new Uint8Array(height * width);
      for (let i = 0; i < height * width; i++) {
        if (!thresholdPixels[i] && unassignedPixels[i]) {
          remainingMask[i] = 1;
          hasValley = true;
        }
      }
      if (!hasValley) break;

      const segment = findRegion(remainingMask);
      if (segment.length > minSegmentSize) {
        segments.push(segment);
      }
      for (const [y, x] of segment) {
        unassignedPixels[y * width + x] = 0;
      }
    }
  }

  return segments;
}
