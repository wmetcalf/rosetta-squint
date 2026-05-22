# Playwright + extension that hashes on-page elements with rosetta-squint

A worked example of the **harder** pattern from "can you trigger plugin behavior via CDP": load a Chrome extension that uses `rosetta-squint/browser` internally to hash images on the page, and drive it from Playwright.

## When this is the right pattern

**Most visual-regression workflows do NOT need this.** Compute the hash in Node from `page.screenshot()` bytes — see `../playwright/visual-regression.ts`. That's simpler, faster, no extension to bundle.

You'd reach for the extension pattern when:

- You want the hash computed **inside the browser** for some reason (e.g. the page is doing real-time rendering and you need to capture the GPU output, or you're hashing many elements per page and round-tripping bytes to Node is the bottleneck)
- You're testing the extension itself (its hashing logic is the unit under test)
- You're integrating with another extension that exposes a `chrome.runtime.sendMessage` API and you want to drive it

## Layout

```
playwright-extension/
├── extension/                  # the actual Chrome extension (MV3)
│   ├── manifest.json
│   ├── background.js           # service worker — loads squint browser bundle
│   ├── content.js              # injected into pages — receives messages
│   └── squint.bundle.js        # `esbuild --bundle src/browser.ts`
├── driver.ts                   # Playwright script that loads + drives the extension
└── README.md
```

## How CDP "drives" the extension

CDP itself has no first-class extension messaging API. The pattern works via three handles Playwright gives you:

```ts
// 1. Launch persistent context with the extension loaded.
const ctx = await chromium.launchPersistentContext("./profile", {
  headless: false,
  args: [
    `--disable-extensions-except=${EXT_PATH}`,
    `--load-extension=${EXT_PATH}`,
  ],
});

// 2. Find the extension's service worker — this is the only way to
//    address the extension's background context from outside.
let [bgWorker] = ctx.serviceWorkers();
if (!bgWorker) {
  bgWorker = await ctx.waitForEvent("serviceworker");
}
const extensionId = bgWorker.url().split("/")[2];

// 3. Drive the extension via:
//    (a) page-side: `chrome.runtime.sendMessage(extId, msg)` from page.evaluate
//    (b) sw-side: `bgWorker.evaluate(fn)` runs code inside the service worker

// Example: ask the extension to hash an <img> on the page.
const result = await page.evaluate(async (extId) => {
  return await chrome.runtime.sendMessage(extId, {
    type: "hash-element",
    selector: "img.hero",
    algo: "phash",
  });
}, extensionId);
```

The extension's content script (or background worker) does the actual squint call. The Playwright driver just relays messages and reads results.

## Trade-offs vs the "screenshot + Node hash" pattern

| | Screenshot + Node hash | In-extension hash |
|---|---|---|
| Setup complexity | install playwright, npm install rosetta-squint | bundle the extension, configure MV3 CSP, manage extension reload |
| Per-test runtime | ~1 screenshot per assertion | element-level granularity, but pays bundle init cost |
| Determinism | high (you control the viewport) | depends on extension lifecycle |
| Cross-browser | works in Firefox via WebKit driver too | extension API is mostly Chrome-only |
| What you can hash | rectangular regions of the rendered page | DOM elements, canvas content, individual images |

## Status

This directory is a **sketch** of the pattern, not a runnable example. The bundled extension would require:

1. `esbuild --bundle ../../squint/js/rosetta-squint/dist/browser.js --format=iife --outfile=extension/squint.bundle.js`
2. A real `manifest.json` with `host_permissions` for the test URL and `"content_security_policy": { "extension_pages": "script-src 'self' 'wasm-unsafe-eval'" }`
3. Bundling the WASM blobs as `web_accessible_resources` so the extension's CSP allows them

Wiring all of that up is a follow-up if you actually want to use this pattern. For most visual-regression use cases, the `../playwright/visual-regression.ts` example covers what you need.
