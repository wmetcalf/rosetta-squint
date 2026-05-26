import { describe, it, expect } from "vitest";
import { decode } from "../src/index.js";
import { listValidFixtures, readFixture, readGolden } from "./testkit.js";

// HEIC goldens are produced by system libheif 1.17.6. libheif-js bundles its
// own WASM libheif build that diverges by ±1-2 per pixel due to differing YCbCr
// → RGB conversion paths. JS HEIC is verified to a max-delta tolerance rather
// than byte-exact. See DECODER_NOTES.md.
const HEIC_MAX_DELTA = 2;

describe("Group 2 — HEIC goldens (within ±2 px tolerance)", () => {
  // Smoke test for alpha detection — this is the only signal we'll get if
  // a future libheif-js version reshapes the private `$$.ptr` field that
  // heic.ts pokes for `has_alpha_channel`. Pinned via package.json, but
  // belt-and-braces.
  it("detects alpha channel on RGBA HEIC fixtures", async () => {
    const rgbaFixtures = listValidFixtures("heic").filter((f) =>
      f.includes("-rgba.")
    );
    if (rgbaFixtures.length === 0) {
      throw new Error("no RGBA HEIC fixtures available for alpha smoke test");
    }
    for (const rel of rgbaFixtures) {
      const got = await decode(readFixture(rel));
      expect(got.channels, `${rel} should be 4-channel (RGBA)`).toBe(4);
    }
  });

  it("decodes all HEIC fixtures within tolerance of system libheif", async () => {
    const fixtures = listValidFixtures("heic");
    if (fixtures.length === 0) throw new Error("no HEIC fixtures");
    const failures: string[] = [];
    for (const rel of fixtures) {
      const input = readFixture(rel);
      try {
        const got = await decode(input);
        const want = readGolden(rel);
        if (
          got.width !== want.width ||
          got.height !== want.height ||
          got.channels !== want.channels
        ) {
          failures.push(
            `${rel}: shape ${got.width}x${got.height}c${got.channels} != ${want.width}x${want.height}c${want.channels}`
          );
          continue;
        }
        if (got.data.length !== want.pixels.length) {
          failures.push(
            `${rel}: pixel byte count ${got.data.length} != ${want.pixels.length}`
          );
          continue;
        }
        let maxDelta = 0;
        let badIdx = -1;
        for (let i = 0; i < got.data.length; i++) {
          const d = Math.abs(got.data[i] - want.pixels[i]);
          if (d > maxDelta) {
            maxDelta = d;
            badIdx = i;
          }
        }
        if (maxDelta > HEIC_MAX_DELTA) {
          failures.push(
            `${rel}: max delta ${maxDelta} at byte ${badIdx} exceeds ${HEIC_MAX_DELTA}`
          );
        }
      } catch (e: any) {
        failures.push(
          `${rel}: threw ${e.kind ?? e.constructor.name}: ${e.detail ?? e.message}`
        );
      }
    }
    if (failures.length > 0) {
      throw new Error(`${failures.length} HEIC failures:\n  ${failures.join("\n  ")}`);
    }
  });
});
