/**
 * Same pattern as ../playwright/visual-regression.ts but using Puppeteer.
 *
 * Puppeteer is closer to raw CDP — page.screenshot() under the hood calls
 * Page.captureScreenshot via CDP. Either way you get PNG bytes; rosetta-squint
 * hashes them in Node.
 *
 * Run:
 *   npm install puppeteer rosetta-squint
 *   npx puppeteer browsers install chrome     # one-time
 *   tsx visual-regression.ts
 */

import puppeteer from "puppeteer";
import { phashBytes } from "rosetta-squint";

const CASES = [
  { name: "example.com", url: "https://example.com", goldenHex: "0000000000000000", tolerance: 5 },
];

function hammingDistance(a: string, b: string): number {
  if (a.length !== b.length) throw new Error("length mismatch");
  let d = 0;
  for (let i = 0; i < a.length; i++) {
    d += [0, 1, 1, 2, 1, 2, 2, 3, 1, 2, 2, 3, 2, 3, 3, 4][
      parseInt(a[i]!, 16) ^ parseInt(b[i]!, 16)
    ]!;
  }
  return d;
}

async function main() {
  const browser = await puppeteer.launch({ headless: true });
  try {
    let failures = 0;
    for (const c of CASES) {
      const page = await browser.newPage();
      await page.setViewport({ width: 1280, height: 720 });
      await page.goto(c.url, { waitUntil: "networkidle0" });
      const buf = await page.screenshot({ type: "png" });
      const hash = await phashBytes(new Uint8Array(buf as Buffer), 8);
      const observed = hash.toString();
      const distance = hammingDistance(observed, c.goldenHex);
      const status = distance <= c.tolerance ? "PASS" : "FAIL";
      console.log(
        `${status}  ${c.name}  observed=${observed}  distance=${distance}/${c.tolerance}`,
      );
      if (status === "FAIL") failures++;
      await page.close();
    }
    process.exit(failures > 0 ? 1 : 0);
  } finally {
    await browser.close();
  }
}

main().catch((e) => {
  console.error(e);
  process.exit(2);
});
