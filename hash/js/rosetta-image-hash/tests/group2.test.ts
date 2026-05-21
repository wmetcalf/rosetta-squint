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

describe("colorhash goldens", () => {
  it("byte-exact across all fixtures × binbits", () => {
    const cases = algorithmCases("colorhash");
    const failures: string[] = [];
    for (const c of cases) {
      const img = loadPredecoded(c.fixture);
      const h = rih.colorhash(img, c.size);
      if (h.toHex() !== c.hex) {
        failures.push(`fixture=${c.fixture} binbits=${c.size} got=${h.toHex()} want=${c.hex}`);
      }
    }
    if (failures.length > 0) {
      throw new Error(`${failures.length} failures:\n  ${failures.join("\n  ")}`);
    }
  });
});

describe("phash_simple goldens", () => {
  it("byte-exact across all fixtures × sizes", () => {
    const cases = algorithmCases("phash_simple");
    const failures: string[] = [];
    for (const c of cases) {
      const img = loadPredecoded(c.fixture);
      const h = rih.phashSimple(img, c.size);
      if (h.toHex() !== c.hex) {
        failures.push(`fixture=${c.fixture} size=${c.size} got=${h.toHex()} want=${c.hex}`);
      }
    }
    if (failures.length > 0) {
      throw new Error(`${failures.length} failures:\n  ${failures.join("\n  ")}`);
    }
  });
});

describe("dhash_vertical goldens", () => {
  it("byte-exact across all fixtures × sizes", () => {
    const cases = algorithmCases("dhash_vertical");
    const failures: string[] = [];
    for (const c of cases) {
      const img = loadPredecoded(c.fixture);
      const h = rih.dhashVertical(img, c.size);
      if (h.toHex() !== c.hex) {
        failures.push(`fixture=${c.fixture} size=${c.size} got=${h.toHex()} want=${c.hex}`);
      }
    }
    if (failures.length > 0) {
      throw new Error(`${failures.length} failures:\n  ${failures.join("\n  ")}`);
    }
  });
});

describe("whash_db4 goldens", () => {
  it("byte-exact across all fixtures × sizes", () => {
    const cases = algorithmCases("whash_db4");
    const failures: string[] = [];
    for (const c of cases) {
      const img = loadPredecoded(c.fixture);
      const h = rih.whashDb4(img, c.size);
      if (h.toHex() !== c.hex) {
        failures.push(`fixture=${c.fixture} size=${c.size} got=${h.toHex()} want=${c.hex}`);
      }
    }
    if (failures.length > 0) {
      throw new Error(`${failures.length} failures:\n  ${failures.join("\n  ")}`);
    }
  });
});

describe("colorhash bin encoding (Group 1)", () => {
  it("B=4 quirky encoding (v=8 → 0xc, NOT 0x8)", () => {
    const cases: [number, boolean[]][] = [
      [0,  [false, false, false, false]],
      [1,  [false, false, false, true]],
      [2,  [false, false, true,  false]],
      [4,  [false, true,  true,  false]],
      [7,  [false, true,  true,  true]],
      [8,  [true,  true,  false, false]],
      [15, [true,  true,  true,  true]],
    ];
    for (const [v, expected] of cases) {
      const got = rih.colorhashBinEncode(v, 4);
      expect(got.length).toBe(4);
      for (let i = 0; i < 4; i++) {
        expect(got[i]).toBe(expected[i]);
      }
    }
  });

  it("B=3 simple cases", () => {
    expect(rih.colorhashBinEncode(0, 3)).toEqual([false, false, false]);
    expect(rih.colorhashBinEncode(7, 3)).toEqual([true, true, true]);
  });
});
