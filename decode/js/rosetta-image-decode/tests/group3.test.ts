import { describe, it, expect } from "vitest";
import { detectFormat, supportedFormats } from "../src/index.js";
import { listValidFixtures, readFixture } from "./testkit.js";

describe("Group 3 — format detection (BMP)", () => {
  it("detects all valid BMP fixtures", () => {
    for (const rel of listValidFixtures("bmp")) {
      const bytes = readFixture(rel);
      expect(detectFormat(bytes), rel).toBe("bmp");
    }
  });

  it("rejects bad signature", () => {
    const bytes = readFixture("bmp/invalid/bad-signature.bmp");
    expect(detectFormat(bytes)).toBeNull();
  });

  it("supported formats contains bmp", () => {
    expect(supportedFormats()).toContain("bmp");
  });
});

describe("Group 3 — format detection (PNG)", () => {
  it("detects all valid PNG fixtures", () => {
    for (const rel of listValidFixtures("png")) {
      const bytes = readFixture(rel);
      expect(detectFormat(bytes), rel).toBe("png");
    }
  });

  it("supported formats contains png", () => {
    expect(supportedFormats()).toContain("png");
  });
});
