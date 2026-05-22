import { describe, it, expect } from "vitest";
import { readFileSync } from "node:fs";
import { join } from "node:path";
import { SPEC_DIR } from "./testkit.js";
import { pilGaussianBlur } from "../src/internal/pilGaussianBlur.js";

interface GaussianBlurCase {
  name: string;
  shape: [number, number];
  input: number[][];
  output: number[][];
}

interface GaussianBlurCases {
  cases: GaussianBlurCase[];
}

describe("pilGaussianBlur", () => {
  it("byte-exact against all 6 gaussian_blur_cases.json cases", () => {
    const path = join(SPEC_DIR, "gaussian_blur_cases.json");
    const doc = JSON.parse(readFileSync(path, "utf8")) as GaussianBlurCases;
    expect(doc.cases.length).toBe(6);

    const failures: string[] = [];
    for (const c of doc.cases) {
      const [height, width] = c.shape;
      const flat = new Uint8Array(height * width);
      for (let y = 0; y < height; y++) {
        for (let x = 0; x < width; x++) {
          flat[y * width + x] = c.input[y][x];
        }
      }
      const got = pilGaussianBlur(flat, width, height);
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
