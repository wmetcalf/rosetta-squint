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

describe("Group 3 — format detection (GIF)", () => {
  it("detects all valid GIF fixtures", () => {
    for (const rel of listValidFixtures("gif")) {
      const bytes = readFixture(rel);
      expect(detectFormat(bytes), rel).toBe("gif");
    }
  });

  it("rejects bad magic", () => {
    const bytes = readFixture("gif/invalid/bad-magic.gif");
    expect(detectFormat(bytes)).toBeNull();
  });

  it("supported formats contains gif", () => {
    expect(supportedFormats()).toContain("gif");
  });
});

describe("Group 3 — format detection (JPEG)", () => {
  it("detects all valid JPEG fixtures", () => {
    for (const rel of listValidFixtures("jpeg")) {
      const bytes = readFixture(rel);
      expect(detectFormat(bytes), rel).toBe("jpeg");
    }
  });

  it("rejects bad magic JPEG", () => {
    const bytes = readFixture("jpeg/invalid/bad-magic.jpg");
    expect(detectFormat(bytes)).toBeNull();
  });

  it("supported formats contains jpeg", () => {
    expect(supportedFormats()).toContain("jpeg");
  });
});

describe("Group 3 — format detection (WebP)", () => {
  it("detects all valid WebP fixtures", () => {
    for (const rel of listValidFixtures("webp")) {
      const bytes = readFixture(rel);
      expect(detectFormat(bytes), rel).toBe("webp");
    }
  });

  it("rejects bad magic WebP", () => {
    const bytes = readFixture("webp/invalid/bad-magic.webp");
    expect(detectFormat(bytes)).toBeNull();
  });

  it("supported formats contains webp", () => {
    expect(supportedFormats()).toContain("webp");
  });
});

describe("Group 3 — format detection (TIFF)", () => {
  it("detects all valid TIFF fixtures", () => {
    for (const rel of listValidFixtures("tiff")) {
      const bytes = readFixture(rel);
      expect(detectFormat(bytes), rel).toBe("tiff");
    }
  });

  it("rejects bad magic TIFF", () => {
    const bytes = readFixture("tiff/invalid/bad-magic.tif");
    expect(detectFormat(bytes)).toBeNull();
  });

  it("supported formats contains tiff", () => {
    expect(supportedFormats()).toContain("tiff");
  });
});
