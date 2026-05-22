/**
 * Environment-aware WASM module loader.
 *
 * In browsers (window or worker global): fetch the URL and instantiate via
 * `WebAssembly.compileStreaming`. Pure web platform APIs, no Node imports.
 *
 * In Node.js 18+: read the file via `node:fs.readFileSync` and instantiate via
 * `WebAssembly.compile`. The `node:fs` import is dynamic so browser bundlers
 * tree-shake it out at build time.
 */

const isBrowserLike =
  typeof globalThis !== "undefined" &&
  (typeof (globalThis as { window?: unknown }).window !== "undefined" ||
    typeof (globalThis as { self?: unknown }).self !== "undefined" &&
      typeof (globalThis as { Deno?: unknown }).Deno === "undefined" &&
      typeof (globalThis as { process?: { versions?: { node?: string } } }).process?.versions?.node === "undefined");

export async function loadWasmModule(url: URL): Promise<WebAssembly.Module> {
  if (isBrowserLike && typeof fetch === "function" && typeof WebAssembly.compileStreaming === "function") {
    const resp = await fetch(url);
    if (!resp.ok) {
      throw new Error(`failed to fetch WASM from ${url}: HTTP ${resp.status}`);
    }
    return await WebAssembly.compileStreaming(resp);
  }

  // Node.js: dynamic imports of node: builtins are excluded by browser
  // bundlers (esbuild, vite, webpack 5+) so this branch is dead-code-eliminated
  // in browser builds.
  // eslint-disable-next-line @typescript-eslint/ban-ts-comment
  // @ts-ignore — types resolve at build time for the right environment
  const fs = await import(/* @vite-ignore */ "node:fs");
  // @ts-ignore
  const urlMod = await import(/* @vite-ignore */ "node:url");
  const bytes = fs.readFileSync(urlMod.fileURLToPath(url));
  return await WebAssembly.compile(bytes);
}
