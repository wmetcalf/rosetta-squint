/**
 * Visual-regression-by-perceptual-hash example using Playwright + rosetta-squint.
 *
 * Take a screenshot of a page → compute phash → compare against stored
 * golden hash. Pass if Hamming distance <= TOLERANCE; fail otherwise.
 *
 * This is much cheaper than pixel-diff visual-regression tools because:
 *   - Stored "golden" is a single hex string (~16 chars), not a PNG
 *   - Comparison is Hamming distance on a 64-bit int — sub-microsecond
 *   - "Visually similar" inputs that differ by anti-aliasing, font
 *     rendering, etc. produce the same or very-close hash — fewer
 *     false positives than strict pixel-diff
 *
 * The same pattern works in any Node-side test runner (Vitest, Jest,
 * Mocha, Playwright Test, etc.). The hashing happens in Node — no
 * browser extension needed.
 *
 * Run:
 *   npm install playwright rosetta-squint
 *   npx playwright install chromium       # one-time, downloads Chromium
 *   tsx visual-regression.ts              # or `node --experimental-strip-types`
 */

import { chromium } from "playwright";
import {
  phashBytes,
  hexToHash,
  type Hash,
} from "rosetta-squint";

interface VisualCase {
  name: string;
  url: string;
  /** Stored golden hash (the "this looked right last time" hex). */
  goldenHex: string;
  /** Max Hamming distance to accept (0 = strict identity; 5–10 = tolerant). */
  tolerance: number;
  /** Viewport size for repeatability. */
  viewport?: { width: number; height: number };
  /** Wait until this selector exists before screenshotting. */
  waitForSelector?: string;
}

const CASES: VisualCase[] = [
  {
    name: "example.com homepage",
    url: "https://example.com",
    // First run: run this script, copy the actual hex into goldenHex.
    goldenHex: "0000000000000000",
    tolerance: 5,
    viewport: { width: 1280, height: 720 },
  },
];

async function captureAndHash(
  url: string,
  options: { viewport?: { width: number; height: number }; waitForSelector?: string },
): Promise<{ hash: Hash; pngBytes: Uint8Array }> {
  const browser = await chromium.launch();
  try {
    const context = await browser.newContext({
      viewport: options.viewport ?? { width: 1280, height: 720 },
      // Deterministic rendering: lock to a specific timezone + locale so
      // any time-based UI doesn't drift between runs.
      timezoneId: "UTC",
      locale: "en-US",
      // Disable animations so animated UI doesn't randomly pick frames.
      reducedMotion: "reduce",
    });
    const page = await context.newPage();
    await page.goto(url, { waitUntil: "networkidle" });
    if (options.waitForSelector) {
      await page.waitForSelector(options.waitForSelector);
    }
    const pngBuffer = await page.screenshot({ type: "png", fullPage: false });
    const pngBytes = new Uint8Array(pngBuffer);
    const hash = await phashBytes(pngBytes, 8);
    return { hash, pngBytes };
  } finally {
    await browser.close();
  }
}

function hammingDistance(a: string, b: string): number {
  // Both are hex strings of the same length.
  // Convert to bits and XOR, then count.
  if (a.length !== b.length) {
    throw new Error(`hash length mismatch: ${a.length} vs ${b.length}`);
  }
  let dist = 0;
  for (let i = 0; i < a.length; i++) {
    const x = parseInt(a[i]!, 16) ^ parseInt(b[i]!, 16);
    // Popcount on a 4-bit nibble.
    dist += [0, 1, 1, 2, 1, 2, 2, 3, 1, 2, 2, 3, 2, 3, 3, 4][x]!;
  }
  return dist;
}

async function main() {
  let failures = 0;
  for (const c of CASES) {
    process.stdout.write(`[${c.name}] `);
    const { hash } = await captureAndHash(c.url, {
      viewport: c.viewport,
      waitForSelector: c.waitForSelector,
    });
    const observed = hash.toString();
    const distance = hammingDistance(observed, c.goldenHex);
    if (distance <= c.tolerance) {
      console.log(`PASS  observed=${observed}  distance=${distance}/${c.tolerance}`);
    } else {
      console.log(`FAIL  observed=${observed}  golden=${c.goldenHex}  distance=${distance} > ${c.tolerance}`);
      failures++;
    }
  }
  process.exit(failures > 0 ? 1 : 0);
}

main().catch((e) => {
  console.error("unhandled error:", e);
  process.exit(2);
});
