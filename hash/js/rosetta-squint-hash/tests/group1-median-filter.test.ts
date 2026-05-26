import { describe, it, expect } from "vitest";
import { readFileSync } from "node:fs";
import { join } from "node:path";
import { SPEC_DIR } from "./testkit.js";
import { pilMedianFilter } from "../src/internal/pilMedianFilter.js";

interface MedianFilterCase {
  name: string;
  shape: [number, number];
  input: number[][];
  output: number[][];
}

interface MedianFilterCases {
  cases: MedianFilterCase[];
}

describe("pilMedianFilter", () => {
  it("byte-exact against all median_filter_cases.json cases", () => {
    const path = join(SPEC_DIR, "median_filter_cases.json");
    const doc = JSON.parse(readFileSync(path, "utf8")) as MedianFilterCases;
    expect(doc.cases.length).toBeGreaterThan(0);

    const failures: string[] = [];
    for (const c of doc.cases) {
      const [height, width] = c.shape;
      const flat = new Uint8Array(height * width);
      for (let y = 0; y < height; y++) {
        for (let x = 0; x < width; x++) {
          flat[y * width + x] = c.input[y][x];
        }
      }
      const got = pilMedianFilter(flat, width, height);
      for (let y = 0; y < height; y++) {
        for (let x = 0; x < width; x++) {
          const idx = y * width + x;
          if (got[idx] !== c.output[y][x]) {
            failures.push(
              `${c.name} (${y},${x}): got ${got[idx]} want ${c.output[y][x]}`
            );
          }
        }
      }
    }
    if (failures.length > 0) {
      throw new Error(`${failures.length} pixel failures:\n  ${failures.slice(0, 10).join("\n  ")}`);
    }
  });
});
