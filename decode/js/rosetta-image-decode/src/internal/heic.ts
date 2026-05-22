import { DecodeError } from "../errors.js";
import type { DecodedImage } from "../types.js";
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
  const libheif = await loadLibheif();
  let decoder: any;
  try {
    decoder = new libheif.HeifDecoder();
  } catch (e: any) {
    throw new DecodeError(
      "corruptInput",
      "heic",
      `HeifDecoder ctor failed: ${e?.message ?? String(e)}`,
    );
  }

  let images: any[];
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
  const hasAlpha: boolean =
    img.handle && img.handle.$$ && typeof img.handle.$$.ptr === "number"
      ? (libheif._heif_image_handle_has_alpha_channel(img.handle.$$.ptr) !== 0)
      : false;

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
}
