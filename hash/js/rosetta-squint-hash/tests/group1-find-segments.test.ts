import { describe, it, expect } from "vitest";
import { readFileSync } from "node:fs";
import { join } from "node:path";
import { SPEC_DIR } from "./testkit.js";
import { findAllSegments } from "../src/internal/findSegments.js";

interface SegmentationCase {
  name: string;
  shape: [number, number];
  input: number[][];
  segment_threshold: number;
  min_segment_size: number;
  segments: number[][][];  // list of segments; each segment is list of [y, x] pairs
}

interface SegmentationCases {
  cases: SegmentationCase[];
}

describe("findAllSegments", () => {
  it("matches all segmentation_cases.json cases (segment count and pixel membership)", () => {
    const path = join(SPEC_DIR, "segmentation_cases.json");
    const doc = JSON.parse(readFileSync(path, "utf8")) as SegmentationCases;
    expect(doc.cases.length).toBeGreaterThan(0);

    const failures: string[] = [];
    for (const c of doc.cases) {
      const [height, width] = c.shape;
      const flat = new Float32Array(height * width);
      for (let y = 0; y < height; y++) {
        for (let x = 0; x < width; x++) {
          flat[y * width + x] = c.input[y][x];
        }
      }
      const got = findAllSegments(flat, height, width, c.segment_threshold, c.min_segment_size);

      // Check segment count
      if (got.length !== c.segments.length) {
        failures.push(
          `${c.name}: got ${got.length} segments, want ${c.segments.length}`
        );
        continue;
      }

      // Check each segment's pixel membership (order-insensitive within segment,
      // but segment order must match)
      for (let si = 0; si < c.segments.length; si++) {
        const expectedPixels = c.segments[si];
        const gotSegment = got[si];
        if (gotSegment.length !== expectedPixels.length) {
          failures.push(
            `${c.name} seg ${si}: got ${gotSegment.length} pixels, want ${expectedPixels.length}`
          );
          continue;
        }
        // Build a set from got segment for membership check
        const gotSet = new Set(gotSegment.map(([y, x]) => `${y},${x}`));
        for (const [ey, ex] of expectedPixels) {
          if (!gotSet.has(`${ey},${ex}`)) {
            failures.push(
              `${c.name} seg ${si}: missing pixel (${ey},${ex})`
            );
            break;
          }
        }
      }
    }

    if (failures.length > 0) {
      throw new Error(`${failures.length} failures:\n  ${failures.slice(0, 10).join("\n  ")}`);
    }
  });
});
