import { describe, it, expect } from "vitest";
import { decode, supportedFormats } from "../src/index.js";
import { listValidFixtures, readFixture } from "./testkit.js";

describe("Group 5 — invariants (BMP)", () => {
  it("all decoded images have valid shape", () => {
    for (const rel of listValidFixtures("bmp")) {
      const bytes = readFixture(rel);
      const img = decode(bytes);
      expect(img.width, rel).toBeGreaterThan(0);
      expect(img.height, rel).toBeGreaterThan(0);
      expect(img.format, rel).toBe("bmp");
      expect(img.data.length, rel).toBe(img.width * img.height * img.channels);
      expect([3, 4]).toContain(img.channels);
    }
  });

  it("supported formats contains only bmp", () => {
    const supported = supportedFormats();
    expect(supported).toHaveLength(1);
    expect(supported[0]).toBe("bmp");
  });
});
