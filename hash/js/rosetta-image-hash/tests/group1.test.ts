import { describe, it, expect } from "vitest";
import { readFileSync } from "node:fs";
import { join } from "node:path";

import { toGray } from "../src/internal/pilGray.js";
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
