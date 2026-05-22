# rosetta-squint examples

Worked examples for using the cross-language perceptual hash library in real settings.

## Browser-test workflows (the main reason most people will land here)

| Directory | Tool | Use case |
|---|---|---|
| [`playwright/`](./playwright/) | Playwright | Visual-regression-by-hash. Take a screenshot of a page, hash it, compare against a stored golden. Hamming distance ≤ N means the page still looks right. |
| [`puppeteer/`](./puppeteer/) | Puppeteer | Same pattern with Puppeteer. Puppeteer is closer to raw CDP — `page.screenshot()` is `Page.captureScreenshot` under the hood. |
| [`cdp/`](./cdp/) | Raw `chrome-remote-interface` | If you're already driving Chrome via CDP for some other reason and just want to bolt on perceptual hashing. |

These are the "natural" patterns. The hashing happens in Node, after the screenshot bytes come back from CDP. No extension or in-browser code required.

## Bigger lift: extension that hashes from inside the page

| Directory | What it is | When you'd want it |
|---|---|---|
| [`playwright-extension/`](./playwright-extension/) | Sketch of a Chrome extension that uses `rosetta-squint/browser` internally, driven from Playwright | You actually need the hash computed in the page (e.g. live canvas content, dozens of element hashes per page where round-tripping to Node would dominate, or you're testing an extension's own hashing logic) |

This is genuinely more setup — extension bundling, MV3 CSP work, service-worker addressing. Not recommended unless the simpler pattern doesn't fit your use case.

## "Can you trigger extension behavior via CDP?"

Short answer: yes, but not directly. CDP doesn't have an extension-messaging command. The path is:

1. Launch Chrome with `--load-extension=...` (Playwright `launchPersistentContext` exposes this)
2. Access the extension's service worker via `browser.serviceWorkers()` — that's how you address the extension's background context from outside
3. Either:
   - Run code in the SW with `sw.evaluate(fn)` (works for in-extension state inspection)
   - Run code in a page that calls `chrome.runtime.sendMessage(extensionId, msg)` (works for normal extension API surface)

`playwright-extension/README.md` has the full pattern in code. But re-read the table above — most workflows don't actually need this.

## Running the examples

Each subdirectory has its own `package.json`. From within a subdirectory:

```bash
npm install
# Playwright also needs:
npx playwright install chromium
# Puppeteer:
npx puppeteer browsers install chrome
# CDP raw:
# Start chrome with --remote-debugging-port=9222 yourself.

# Then:
npx tsx visual-regression.ts
```

(Or run any of the `.ts` files via `node --experimental-strip-types` on Node 22+.)

## What's NOT here

- **Visual regression CI tooling.** This isn't a competitor to Percy/Argos/Chromatic — those store actual screenshots and do pixel diffs. The `rosetta-squint` approach is leaner (single hex per assertion, no screenshot storage) but trades off — pixel diffs catch subtler regressions than 64-bit perceptual hashes.
- **GitHub Actions wiring.** These examples are stand-alone scripts. Wrap them in your test runner of choice (Vitest/Jest/Mocha/`@playwright/test`) and call them from CI.
