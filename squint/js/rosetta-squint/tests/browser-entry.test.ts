/**
 * Browser-entry smoke test for rosetta-squint.
 *
 * Imports `rosetta-squint/browser` and verifies:
 *   1. All bytes-based convenience functions are exported and callable
 *   2. Path-based functions are NOT exported (no `node:fs`)
 *   3. Hash output matches the main entry for the same byte input
 *      (no behavioral drift between entries)
 *
 * vitest runs this in Node, so we can't directly verify browser execution,
 * but the import-tree check + dynamic-import-of-node:fs branch logic in
 * loadWasm.ts means the same dist/browser.js bundle that loads here will
 * load in browsers when fetched via CDN / served by a bundler.
 */

import { describe, expect, it } from "vitest";
import { readFile } from "node:fs/promises";
import * as browserEntry from "../src/browser.js";
import * as mainEntry from "../src/index.js";

const FIXTURES = new URL("../../../../hash/spec/fixtures/", import.meta.url);

describe("rosetta-squint/browser entry", () => {
  it("exposes bytes-based functions for all 10 algorithms", () => {
    expect(typeof browserEntry.averageHashBytes).toBe("function");
    expect(typeof browserEntry.phashBytes).toBe("function");
    expect(typeof browserEntry.phashSimpleBytes).toBe("function");
    expect(typeof browserEntry.dhashBytes).toBe("function");
    expect(typeof browserEntry.dhashVerticalBytes).toBe("function");
    expect(typeof browserEntry.whashHaarBytes).toBe("function");
    expect(typeof browserEntry.whashDb4Bytes).toBe("function");
    expect(typeof browserEntry.whashDb4RobustBytes).toBe("function");
    expect(typeof browserEntry.colorhashBytes).toBe("function");
    expect(typeof browserEntry.cropResistantHashBytes).toBe("function");
    expect(typeof browserEntry.decodeBytes).toBe("function");
  });

  it("does NOT expose path-based functions (no node:fs)", () => {
    // @ts-expect-error — phash (path) is intentionally not exported from /browser
    expect(browserEntry.phash).toBeUndefined();
    // @ts-expect-error
    expect(browserEntry.decodeFile).toBeUndefined();
  });

  it("phashBytes on imagehash.png matches main entry's phash", async () => {
    const path = new URL("imagehash.png", FIXTURES);
    const bytes = new Uint8Array(await readFile(path));
    const hBrowser = await browserEntry.phashBytes(bytes, 8);
    const hMain = await mainEntry.phashBytes(bytes, 8);
    expect(hBrowser.toString()).toBe(hMain.toString());
    // And matches the cross-port reference value from Go/Java/JS reports
    expect(hBrowser.toString()).toBe("ba8c84536bd3c366");
  });

  it("dhashBytes on imagehash.png produces non-empty hex", async () => {
    const path = new URL("imagehash.png", FIXTURES);
    const bytes = new Uint8Array(await readFile(path));
    const h = await browserEntry.dhashBytes(bytes, 8);
    expect(h.toString()).toMatch(/^[0-9a-f]+$/);
    expect(h.toString().length).toBe(16);
  });

  it("decodeBytes produces an RgbImage shape", async () => {
    const path = new URL("imagehash.png", FIXTURES);
    const bytes = new Uint8Array(await readFile(path));
    const img = await browserEntry.decodeBytes(bytes);
    expect(img.width).toBeGreaterThan(0);
    expect(img.height).toBeGreaterThan(0);
    expect(img.channels === 3 || img.channels === 4).toBe(true);
    expect(img.data.byteLength).toBe(img.width * img.height * img.channels);
  });
});
