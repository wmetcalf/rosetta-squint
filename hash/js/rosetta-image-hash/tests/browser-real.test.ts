/**
 * REAL-BROWSER test for the `rosetta-image-hash/browser` entry point.
 *
 * Most of our JS tests run in Node. This one launches actual Chromium via
 * Playwright, loads the browser entry into a page, decodes an image via
 * the canvas API the way a real browser caller would, computes phash on
 * the resulting pixel buffer, and verifies the output matches what the
 * Node-side reference produces for the same input.
 *
 * Run:
 *   npm install playwright
 *   npx playwright install chromium
 *   npx vitest run tests/browser-real.test.ts
 *
 * If playwright isn't installed the test skips itself rather than failing
 * (so contributors without browser deps can still run the regular suite).
 */

import { describe, it, expect, beforeAll, afterAll } from "vitest";
import { readFileSync, existsSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

import { phash, decodePng } from "../src/index.js";

const __dirname = dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = join(__dirname, "..", "..", "..", "..");
const FIXTURE = join(REPO_ROOT, "hash", "spec", "fixtures", "imagehash.png");

// Inline the entire dist/browser.js + dist/internal/*.js into a single ESM
// blob that we can hand to Playwright as a data:URL. Avoids needing a local
// web server. Each ESM module gets registered under a synthetic URL.
function buildBrowserBundle(): string {
  // Just read dist/browser.js + its transitive deps and concatenate them
  // into a single module — TypeScript already compiled each to ESM and the
  // imports are relative. We rewrite the relative imports to inline.
  // For simplicity we use a tiny bundler invocation if esbuild is available,
  // else fall back to a single-file build via TypeScript.
  // Simpler still: invoke esbuild as a one-shot to produce a single iife.

  // Actually simplest: write a tiny HTML file that uses dynamic import maps
  // to load our dist files. We'll serve them from a local file:// URL via
  // Playwright's setContent + addScriptTag patterns.
  throw new Error("not used in this implementation — see test body");
}

describe("rosetta-image-hash/browser — real Chromium execution", () => {
  let playwright: typeof import("playwright") | null = null;

  beforeAll(async () => {
    try {
      // eslint-disable-next-line @typescript-eslint/no-var-requires
      playwright = await import("playwright");
    } catch {
      playwright = null;
    }
  });

  it("loads dist/browser.js in Chromium and produces the same phash as Node", async () => {
    if (!playwright) {
      console.warn("playwright not installed — skipping real-browser test");
      return;
    }

    // Build a single-file ESM bundle of dist/browser.js using esbuild via
    // dynamic import. esbuild may not be installed; we attempt it and skip
    // if absent.
    let esbuild: typeof import("esbuild") | null = null;
    try {
      // @ts-ignore — esbuild may not have types
      esbuild = await import("esbuild");
    } catch {
      esbuild = null;
    }

    let browserBundle: string;
    if (esbuild) {
      const result = await esbuild.build({
        entryPoints: [join(__dirname, "..", "dist", "browser.js")],
        bundle: true,
        format: "esm",
        write: false,
        target: "es2020",
      });
      browserBundle = result.outputFiles[0]!.text;
    } else {
      console.warn("esbuild not installed — skipping (would need to bundle browser.js for browser load)");
      return;
    }

    // Try to launch; if the chromium binary isn't installed (common in CI
     // setups without `npx playwright install chromium`), skip gracefully.
    let browser: import("playwright").Browser;
    try {
      browser = await playwright.chromium.launch();
    } catch (e: unknown) {
      const msg = e instanceof Error ? e.message : String(e);
      if (msg.includes("Executable doesn't exist") || msg.includes("chrome-headless-shell")) {
        console.warn("chromium binary not installed (run `npx playwright install chromium`) — skipping");
        return;
      }
      throw e;
    }
    try {
      const ctx = await browser.newContext();
      const page = await ctx.newPage();

      // Real-browser load: an HTML page with our bundled module attached as
      // a <script type="module"> that publishes phash on window. Then
      // page.evaluate runs in that context and reads window.RIH.
      //
      // The esbuild output is an ESM module — wrap it so its exports land
      // on window for the page.evaluate consumer.
      const pngBytes = readFileSync(FIXTURE);
      const pngBase64 = pngBytes.toString("base64");

      const wrappedBundle = `
        ${browserBundle}
        ;
        // The bundle is ESM and esbuild leaves the exports as top-level
        // declarations. Re-expose the ones we need on globalThis so the
        // page.evaluate body can find them.
        globalThis.RIH = { phash, dhash, averageHash };
      `;

      await page.setContent("<!doctype html><html><body></body></html>");
      await page.addScriptTag({ content: wrappedBundle, type: "module" });

      // Wait until the script tag finishes setting up window.RIH.
      await page.waitForFunction(() => !!(globalThis as any).RIH);

      const result: { observed: string; width: number; height: number } = await page.evaluate(
        async (b64) => {
          const RIH = (globalThis as any).RIH;
          const blob = await (await fetch("data:image/png;base64," + b64)).blob();
          const bitmap = await createImageBitmap(blob);
          const cv = new OffscreenCanvas(bitmap.width, bitmap.height);
          const cx = cv.getContext("2d")!;
          cx.drawImage(bitmap, 0, 0);
          const imageData = cx.getImageData(0, 0, bitmap.width, bitmap.height);
          const img = {
            width: imageData.width,
            height: imageData.height,
            data: new Uint8Array(imageData.data),
            channels: 4 as const,
          };
          const h = RIH.phash(img, 8);
          return { observed: h.toString(), width: img.width, height: img.height };
        },
        pngBase64,
      );

      // Sanity: width/height match the source PNG.
      const nodeImg = decodePng(new Uint8Array(pngBytes));
      expect(result.width).toBe(nodeImg.width);
      expect(result.height).toBe(nodeImg.height);

      // The phash computed in the browser from canvas pixels MUST match
      // what Node produces from the same source PNG (modulo the channels
      // shape — canvas gives RGBA, decodePng gives RGB, but phash strips
      // alpha so both should produce the same result).
      const nodeHash = phash(nodeImg, 8);
      expect(result.observed).toBe(nodeHash.toString());

      // And matches the cross-port reference value confirmed across
      // Go/Java/JS/Python/Rust/Swift for the same fixture at size 8.
      expect(result.observed).toBe("ba8c84536bd3c366");
    } finally {
      await browser.close();
    }
  }, 60_000);
});
