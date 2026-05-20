import { describe, it, expect } from "vitest";
import { readFileSync } from "node:fs";
import { join } from "node:path";

import { dwt2, wavedec2, waverec2 } from "../src/internal/haar.js";

import { dct1d } from "../src/internal/dct.js";

import { resize as lanczosResize } from "../src/internal/lanczos.js";
import { loadLanczosCase } from "./testkit.js";

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

describe("dct", () => {
  it("dct1d matches scipy reference", () => {
    const path = join(SPEC_DIR, "dct_cases.json");
    const doc = JSON.parse(readFileSync(path, "utf8")) as {
      n: number;
      cases: Record<string, { input: number[]; output: number[] }>;
    };
    const tol = 1e-9;
    for (const [name, c] of Object.entries(doc.cases)) {
      const got = dct1d(new Float64Array(c.input));
      expect(got.length).toBe(doc.n);
      for (let k = 0; k < doc.n; k++) {
        if (Math.abs(got[k] - c.output[k]) > tol) {
          throw new Error(`${name} k=${k}: got ${got[k]} want ${c.output[k]}`);
        }
      }
    }
  });

  it("arange first output is 992", () => {
    const x = new Float64Array(32);
    for (let i = 0; i < 32; i++) x[i] = i;
    const y = dct1d(x);
    expect(Math.abs(y[0] - 992.0)).toBeLessThan(1e-9);
  });
});

describe("lanczos", () => {
  const cases = [
    "downsample_64_to_32_gradient",
    "upsample_16_to_32_gradient",
    "identity_32_to_32_random",
    "asymmetric_64x48_to_32x24",
  ];
  for (const name of cases) {
    it(`byte-exact: ${name}`, () => {
      const c = loadLanczosCase(name);
      const got = lanczosResize(c.src, c.srcW, c.srcH, c.dstW, c.dstH);
      expect(got.length).toBe(c.dstW * c.dstH);
      for (let y = 0; y < c.dstH; y++) {
        for (let x = 0; x < c.dstW; x++) {
          const i = y * c.dstW + x;
          if (got[i] !== c.dst[i]) {
            throw new Error(
              `${name} pixel (${y},${x}): got ${got[i]} want ${c.dst[i]}`
            );
          }
        }
      }
    });
  }
});

describe("haar", () => {
  const tol = 1e-12;

  function assertClose(expected: number[][], actual: number[][], label: string) {
    expect(actual.length).toBe(expected.length);
    for (let y = 0; y < expected.length; y++) {
      expect(actual[y].length).toBe(expected[y].length);
      for (let x = 0; x < expected[y].length; x++) {
        const diff = Math.abs(expected[y][x] - actual[y][x]);
        if (diff > tol) {
          throw new Error(
            `${label} (${y},${x}): expected ${expected[y][x]} got ${actual[y][x]} diff ${diff}`
          );
        }
      }
    }
  }

  function loadHaar() {
    return JSON.parse(readFileSync(join(SPEC_DIR, "haar_cases.json"), "utf8")) as {
      input: number[][];
      single_level: { cA: number[][]; cH: number[][]; cV: number[][]; cD: number[][] };
      multi_level_4: { cA: number[][]; reconstructed: number[][] };
    };
  }

  it("single-level matches pywt", () => {
    const doc = loadHaar();
    const { cA, cH, cV, cD } = dwt2(doc.input);
    assertClose(doc.single_level.cA, cA, "cA");
    assertClose(doc.single_level.cH, cH, "cH");
    assertClose(doc.single_level.cV, cV, "cV");
    assertClose(doc.single_level.cD, cD, "cD");
  });

  it("multi-level LL is 1x1 and reconstruction equals input", () => {
    const doc = loadHaar();
    const dec = wavedec2(doc.input, 4);
    expect(dec.cA.length).toBe(1);
    expect(dec.cA[0].length).toBe(1);
    assertClose(doc.multi_level_4.cA, dec.cA, "multi cA");
    const recon = waverec2(dec);
    assertClose(doc.multi_level_4.reconstructed, recon, "reconstructed");
    assertClose(doc.input, recon, "round-trip == input");
  });

  it("zero LL of full decomp removes DC", () => {
    const x: number[][] = [];
    for (let i = 0; i < 4; i++) x.push([7.5, 7.5, 7.5, 7.5]);
    const dec = wavedec2(x, 2);
    dec.cA[0][0] = 0;
    const recon = waverec2(dec);
    for (let y = 0; y < 4; y++) {
      for (let xCol = 0; xCol < 4; xCol++) {
        expect(Math.abs(recon[y][xCol])).toBeLessThan(tol);
      }
    }
  });
});
