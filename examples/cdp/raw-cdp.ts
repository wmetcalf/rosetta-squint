/**
 * Raw CDP example — connect to a running Chrome instance via CDP, take a
 * screenshot, hash with rosetta-squint. No Playwright/Puppeteer wrapper.
 *
 * Use this pattern if you're already driving Chrome via CDP (e.g. for some
 * other reason) and just want to bolt on perceptual hashing.
 *
 * Prereqs:
 *   chrome --remote-debugging-port=9222 --headless=new
 *
 * Run:
 *   npm install chrome-remote-interface rosetta-squint
 *   tsx raw-cdp.ts https://example.com
 */

import CDP from "chrome-remote-interface";
import { phashBytes } from "rosetta-squint";

async function main() {
  const url = process.argv[2] ?? "https://example.com";

  const client = await CDP({ host: "localhost", port: 9222 });
  const { Page, Emulation } = client;
  try {
    await Page.enable();
    await Emulation.setDeviceMetricsOverride({
      width: 1280,
      height: 720,
      deviceScaleFactor: 1,
      mobile: false,
    });
    await Page.navigate({ url });
    await Page.loadEventFired();

    // Capture screenshot via raw CDP — returns base64-encoded PNG.
    const { data: b64 } = await Page.captureScreenshot({ format: "png" });
    const pngBytes = new Uint8Array(Buffer.from(b64, "base64"));

    const hash = await phashBytes(pngBytes, 8);
    console.log(`phash(${url}, 8) = ${hash.toString()}`);
  } finally {
    await client.close();
  }
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
