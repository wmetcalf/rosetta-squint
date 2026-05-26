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

/**
 * Resolve a package-relative WASM file path tolerantly to npm's nested-vs-
 * hoisted layout.
 *
 * Background: jpeg.ts and webp.ts both load WASM via
 * `new URL("../../node_modules/@jsquash/.../*.wasm", import.meta.url)`.
 * That pattern hard-codes a non-hoisted layout where every transitive dep
 * sits under each consumer's own `node_modules/`. When an outer package
 * (like `rosetta-squint`) installs `rosetta-squint-decode`, npm hoists the
 * `@jsquash/*` dependency to the outer `node_modules/`, leaving the
 * decode package's nested `node_modules/@jsquash` empty — and the hard-
 * coded relative URL points at a file that doesn't exist.
 *
 * Fix: in Node, use `createRequire().resolve()` so npm's full resolution
 * algorithm (walking up the parent directory chain) finds the hoisted
 * package. In browsers, fall back to the bundler-relative pattern; bundlers
 * (esbuild/vite/webpack/rollup) emit the WASM next to the JS at the
 * bundler-relative path, so the original URL is correct in that context.
 *
 * @param packageSpecifier   bare specifier like
 *   `"@jsquash/jpeg/codec/dec/mozjpeg_dec.wasm"`
 * @param bundlerRelativeFallback   path of the form
 *   `"../../node_modules/@jsquash/jpeg/codec/dec/mozjpeg_dec.wasm"` used in
 *   browsers and as a last-resort fallback in Node when `createRequire`
 *   resolution fails.
 * @param callerMetaUrl   pass `import.meta.url` from the caller.
 */
export async function resolvePackageWasmUrl(
  packageSpecifier: string,
  bundlerRelativeFallback: string,
  callerMetaUrl: string,
): Promise<URL> {
  // Use the same browser-detection heuristic as `loadWasmModule` so the
  // bundler-relative URL stays the canonical browser path.
  const isBrowser =
    typeof globalThis !== "undefined" &&
    (typeof (globalThis as { window?: unknown }).window !== "undefined" ||
      (typeof (globalThis as { self?: unknown }).self !== "undefined" &&
       typeof (globalThis as { process?: { versions?: { node?: string } } })
         .process?.versions?.node === "undefined"));
  if (!isBrowser) {
    try {
      // @ts-ignore — node:module is a built-in, types via @types/node
      const mod = await import(/* @vite-ignore */ "node:module");
      // @ts-ignore — node:url is a built-in
      const urlMod = await import(/* @vite-ignore */ "node:url");
      const req = (mod as { createRequire: (u: string) => { resolve: (s: string) => string } })
        .createRequire(callerMetaUrl);
      const resolved: string = req.resolve(packageSpecifier);
      return (urlMod as { pathToFileURL: (p: string) => URL }).pathToFileURL(resolved);
    } catch {
      // fall through to bundler-relative URL
    }
  }
  return new URL(bundlerRelativeFallback, callerMetaUrl);
}
