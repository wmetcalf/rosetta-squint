import { describe, it, expect } from "vitest";
import { readFileSync } from "node:fs";
import { join } from "node:path";

import { toGray } from "../src/internal/pilGray.js";
import { toHsv } from "../src/internal/pilHsv.js";
import { SPEC_DIR } from "./testkit.js";

describe("pilGray", () => {
  it("matches all 30 grayscale cases from spec", () => {
    const path = join(SPEC_DIR, "grayscale_cases.json");
    const doc = JSON.parse(readFileSync(path, "utf8")) as {
      cases: { rgb: [number, number, number]; L: number }[];
    };
    expect(doc.cases.length).toBe(30);
    for (const c of doc.cases) {
      const got = toGray(c.rgb[0], c.rgb[1], c.rgb[2]);
      expect(got).toBe(c.L);
    }
  });
});

describe("pilHsv", () => {
  it("matches all 31 hsv cases from spec", () => {
    const path = join(SPEC_DIR, "hsv_cases.json");
    const doc = JSON.parse(readFileSync(path, "utf8")) as {
      cases: { rgb: [number, number, number]; hsv: [number, number, number] }[];
    };
    expect(doc.cases.length).toBe(31);
    for (const c of doc.cases) {
      const got = toHsv(c.rgb[0], c.rgb[1], c.rgb[2]);
      expect(got).toEqual(c.hsv);
    }
  });

  it("negative h_pre wrap: RGB(200,100,150) → (233,127,200)", () => {
    expect(toHsv(200, 100, 150)).toEqual([233, 127, 200]);
  });

  it("half-boundary floor not round: RGB(100,150,200) → (148,127,200)", () => {
    expect(toHsv(100, 150, 200)).toEqual([148, 127, 200]);
  });

  it("saturation 170 boundary: RGB(255,85,85) has S=170", () => {
    expect(toHsv(255, 85, 85)[1]).toBe(170);
  });
});
