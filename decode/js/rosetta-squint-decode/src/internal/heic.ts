import { DecodeError } from "../errors.js";
import type { DecodedImage } from "../types.js";
import { sniffHeicDimensions } from "./dimensionSniff.js";
import { checkDimensions } from "./limits.js";

// libheif-js 1.17.1 is a CommonJS package. Use dynamic ESM import + CJS
// interop instead of node:createRequire so this module loads in browsers
// (when consumed via a bundler that handles CJS interop — esbuild, vite,
// webpack 5+, rollup with @rollup/plugin-commonjs).
let libheifPromise: Promise<any> | null = null;
async function loadLibheif(): Promise<any> {
  if (!libheifPromise) {
    libheifPromise = (async () => {
      // @ts-ignore — libheif-js has no .d.ts
      const mod = await import("libheif-js");
      const lh: any = (mod as any).default ?? mod;
      return lh;
    })();
  }
  return libheifPromise;
}

export async function decodeHeic(bytes: Uint8Array): Promise<DecodedImage> {
  // Sniff the container's primary-item ispe dimensions BEFORE invoking
  // libheif. libheif returns dimensions from the underlying HEVC bitstream
  // rather than the container's ispe, so a patched ispe is never seen by
  // get_width/get_height — without this pre-check the file decodes at its
  // HEVC dimensions and the imageTooLarge guard never fires. Spec §3.1.
  const sniffed = sniffHeicDimensions(bytes);
  if (sniffed) {
    checkDimensions(sniffed.width, sniffed.height, "heic");
  }

  const libheif = await loadLibheif();
  // All Emscripten handles obtained via libheif live on the WASM heap and only
  // release when (a) the wrapper's `.free()` is called, or (b) the next call
  // to `decoder.decode()` invokes `heif_context_free` on the prior context.
  // Stash references in the outer scope so the `finally` block can release
  // them regardless of which call below throws — the alternative is leaked
  // heap blocks that grow the WASM memory until Emscripten GC kicks in.
  let images: any[] | undefined;
  let decoder: any;
  try {
    try {
      decoder = new libheif.HeifDecoder();
    } catch (e: any) {
      throw new DecodeError(
        "corruptInput",
        "heic",
        `HeifDecoder ctor failed: ${e?.message ?? String(e)}`,
      );
    }

    try {
      images = decoder.decode(bytes);
    } catch (e: any) {
      throw new DecodeError(
        "corruptInput",
        "heic",
        `HEIF decode failed: ${e?.message ?? String(e)}`,
      );
    }

    if (!images || images.length === 0) {
      throw new DecodeError("corruptInput", "heic", "no images in HEIC");
    }

    const img = images[0];
    const width: number = img.get_width();
    const height: number = img.get_height();
    checkDimensions(width, height, "heic");

    // Check alpha via the underlying C API using the handle pointer.
    // libheif-js wraps handles as Emscripten objects; $$.ptr gives the raw pointer.
    // The HeifImage prototype does NOT expose has_alpha_channel as of 1.17.1
    // (only display/free/get_width/get_height/is_primary), so we fall back to
    // poking the private Emscripten field. Wrap defensively in case a future
    // libheif-js version reshapes the internal binding — losing alpha detection
    // is preferable to a hard crash. Version pinned to 1.17.1 in package.json
    // to scope the fragility.
    let hasAlpha: boolean = false;
    try {
      if (img.handle && img.handle.$$ && typeof img.handle.$$.ptr === "number") {
        hasAlpha = libheif._heif_image_handle_has_alpha_channel(img.handle.$$.ptr) !== 0;
      }
    } catch {
      hasAlpha = false;
    }

    // display() always decodes as RGBA internally; callback receives the filled buffer.
    const rgba = new Uint8ClampedArray(width * height * 4);
    await new Promise<void>((resolve, reject) => {
      img.display({ data: rgba, width, height }, (filled: any) => {
        if (filled === null) {
          reject(new DecodeError("corruptInput", "heic", "HEIC display returned null"));
        } else {
          resolve();
        }
      });
    });

    if (hasAlpha) {
      return {
        width,
        height,
        data: new Uint8Array(rgba.buffer, rgba.byteOffset, rgba.byteLength),
        channels: 4,
        format: "heic",
      };
    }

    // Strip alpha to produce packed RGB output.
    const pixels = width * height;
    const rgb = new Uint8Array(pixels * 3);
    for (let i = 0, di = 0, si = 0; i < pixels; i++, si += 4, di += 3) {
      rgb[di] = rgba[si]!;
      rgb[di + 1] = rgba[si + 1]!;
      rgb[di + 2] = rgba[si + 2]!;
    }
    return { width, height, data: rgb, channels: 3, format: "heic" };
  } finally {
    // Cleanup is best-effort — swallow errors so they don't mask the real
    // exception. libheif-js HeifImage.prototype.free() calls
    // heif_image_handle_release; HeifDecoder doesn't expose .free() but its
    // .decoder field holds a heif_context_t that we can pass to
    // heif_context_free directly. See node_modules/libheif-js/libheif.js
    // (HeifDecoder == t7 in the minified bundle, HeifImage == g8).
    if (images) {
      for (const i of images) {
        try { i?.free?.(); } catch { /* ignore */ }
      }
    }
    try {
      if (decoder?.decoder && typeof libheif?.heif_context_free === "function") {
        libheif.heif_context_free(decoder.decoder);
        decoder.decoder = null;
      }
    } catch { /* ignore */ }
  }
}
