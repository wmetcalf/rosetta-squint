import { describe, it, expect } from "vitest";

import * as rih from "../src/index.js";
import { algorithmCases, loadPredecoded } from "./testkit.js";

describe("average_hash goldens", () => {
  it("byte-exact across all fixtures × sizes", () => {
    const cases = algorithmCases("average_hash");
    const failures: string[] = [];
    for (const c of cases) {
      const img = loadPredecoded(c.fixture);
      const h = rih.averageHash(img, c.size);
      const got = h.toHex();
      if (got !== c.hex) {
        failures.push(`fixture=${c.fixture} size=${c.size} got=${got} want=${c.hex}`);
      }
    }
    if (failures.length > 0) {
      throw new Error(`${failures.length} failures:\n  ${failures.join("\n  ")}`);
    }
  });
});

describe("dhash goldens", () => {
  it("byte-exact across all fixtures × sizes", () => {
    const cases = algorithmCases("dhash");
    const failures: string[] = [];
    for (const c of cases) {
      const img = loadPredecoded(c.fixture);
      const h = rih.dhash(img, c.size);
      if (h.toHex() !== c.hex) {
        failures.push(`fixture=${c.fixture} size=${c.size} got=${h.toHex()} want=${c.hex}`);
      }
    }
    if (failures.length > 0) {
      throw new Error(`${failures.length} failures:\n  ${failures.join("\n  ")}`);
    }
  });
});

describe("phash goldens", () => {
  it("byte-exact across all fixtures × sizes", () => {
    const cases = algorithmCases("phash");
    const failures: string[] = [];
    for (const c of cases) {
      const img = loadPredecoded(c.fixture);
      const h = rih.phash(img, c.size);
      if (h.toHex() !== c.hex) {
        failures.push(`fixture=${c.fixture} size=${c.size} got=${h.toHex()} want=${c.hex}`);
      }
    }
    if (failures.length > 0) {
      throw new Error(`${failures.length} failures:\n  ${failures.join("\n  ")}`);
    }
  });
});

describe("whash_haar goldens", () => {
  it("byte-exact across all fixtures × sizes", () => {
    const cases = algorithmCases("whash_haar");
    const failures: string[] = [];
    for (const c of cases) {
      const img = loadPredecoded(c.fixture);
      const h = rih.whashHaar(img, c.size);
      if (h.toHex() !== c.hex) {
        failures.push(`fixture=${c.fixture} size=${c.size} got=${h.toHex()} want=${c.hex}`);
      }
    }
    if (failures.length > 0) {
      throw new Error(`${failures.length} failures:\n  ${failures.join("\n  ")}`);
    }
  });
});
