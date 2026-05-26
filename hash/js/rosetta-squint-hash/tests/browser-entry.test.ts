/**
 * Browser-entry smoke test.
 *
 * Imports rosetta-squint-hash/browser and verifies:
 *   1. All hash functions are exported and callable
 *   2. No accidental Node-only dep crept into the transitive graph
 *      (vitest would crash on `node:fs` etc. — well, actually it wouldn't,
 *      but a bundler targeting browser would; this test mostly guards
 *      against accidental removal of the browser entry)
 *   3. Hash output matches the main entry for the same input (no
 *      behavior divergence between entries)
 */

import { describe, expect, it } from "vitest";

import * as mainEntry from "../src/index.js";
import * as browserEntry from "../src/browser.js";

describe("browser entry", () => {
  it("exposes all hash algorithms", () => {
    expect(typeof browserEntry.averageHash).toBe("function");
    expect(typeof browserEntry.phash).toBe("function");
    expect(typeof browserEntry.phashSimple).toBe("function");
    expect(typeof browserEntry.dhash).toBe("function");
    expect(typeof browserEntry.dhashVertical).toBe("function");
    expect(typeof browserEntry.whashHaar).toBe("function");
    expect(typeof browserEntry.whashDb4).toBe("function");
    expect(typeof browserEntry.whashDb4Robust).toBe("function");
    expect(typeof browserEntry.colorhash).toBe("function");
    expect(typeof browserEntry.cropResistantHash).toBe("function");
    expect(typeof browserEntry.hexToHash).toBe("function");
    expect(typeof browserEntry.hexToFlathash).toBe("function");
    expect(typeof browserEntry.hexToMultiHash).toBe("function");
  });

  it("does NOT expose decodePng (browsers use canvas/createImageBitmap)", () => {
    // @ts-expect-error — decodePng should not exist on browser entry
    expect(browserEntry.decodePng).toBeUndefined();
  });

  it("produces same phash as the main entry on identical input", () => {
    // Synthetic 16x16 RGB checker pattern — no decoding needed.
    const width = 16, height = 16, channels = 3;
    const data = new Uint8Array(width * height * channels);
    for (let y = 0; y < height; y++) {
      for (let x = 0; x < width; x++) {
        const i = (y * width + x) * channels;
        const v = ((x + y) % 2 === 0) ? 255 : 0;
        data[i] = data[i + 1] = data[i + 2] = v;
      }
    }
    const img = { width, height, data, channels: 3 as const };
    const hMain = mainEntry.phash(img, 8);
    const hBrowser = browserEntry.phash(img, 8);
    expect(hBrowser.toString()).toBe(hMain.toString());
  });
});
