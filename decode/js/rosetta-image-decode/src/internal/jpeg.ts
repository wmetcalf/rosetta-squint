import { DecodeError } from "../errors.js";
import type { DecodedImage } from "../types.js";
import { sniffJpegDimensions } from "./dimensionSniff.js";
import { checkDimensions } from "./limits.js";
import { loadWasmModule, resolvePackageWasmUrl } from "./loadWasm.js";

// @jsquash/jpeg: community-maintained mozjpeg WASM fork of squoosh's jpeg codec.
// Uses the same mozjpeg C library as Rust's mozjpeg-sys, ensuring byte-exact output.
import { init, default as jsquashDecode } from "@jsquash/jpeg/decode.js";

let wasmInitPromise: Promise<void> | null = null;

async function ensureWasmInit(): Promise<void> {
  if (wasmInitPromise) return wasmInitPromise;

  wasmInitPromise = (async () => {
    // Locate the WASM binary tolerantly: in Node, resolve via the package
    // graph (handles hoisted node_modules layouts); in browsers, fall back
    // to the bundler-relative URL pattern that esbuild/vite/webpack rewrite
    // to point at the emitted asset path.
    const wasmUrl = await resolvePackageWasmUrl(
      "@jsquash/jpeg/codec/dec/mozjpeg_dec.wasm",
      "../../node_modules/@jsquash/jpeg/codec/dec/mozjpeg_dec.wasm",
      import.meta.url,
    );
    const wasmModule = await loadWasmModule(wasmUrl);
    await init(wasmModule);
  })();

  // Reset on rejection so a transient failure (e.g., file missing during a
  // hot-reload) doesn't permanently poison every future call with the same
  // rejected promise.
  wasmInitPromise.catch(() => { wasmInitPromise = null; });

  return wasmInitPromise;
}

/**
 * Scan the JPEG stream to detect a 4-component color space (CMYK/YCCK)
 * before passing to the WASM decoder.  mozjpeg calls exit(1) on CMYK
 * which would terminate the Node.js process, so we must bail out early.
 *
 * Strategy: find the first SOF marker and read the number of components.
 * JPEG 4-component images are always CMYK or YCCK — never RGBA.
 */
function detectCmyk(bytes: Uint8Array): boolean {
  if (bytes.length < 4) return false;
  // Skip SOI (FF D8)
  let i = 2;
  while (i + 3 < bytes.length) {
    if (bytes[i] !== 0xff) break;
    const marker = bytes[i + 1]!;
    if (marker === 0xd9) break; // EOI

    // Stand-alone markers (no length field): RST0-RST7, SOI
    if (marker === 0xd8 || (marker >= 0xd0 && marker <= 0xd7)) {
      i += 2;
      continue;
    }

    if (i + 3 >= bytes.length) break;
    const length = ((bytes[i + 2]! << 8) | bytes[i + 3]!) >>> 0;
    if (length < 2) break;

    // SOF markers: C0 baseline, C1 extended, C2 progressive, C3 lossless, etc.
    if (
      marker === 0xc0 || marker === 0xc1 || marker === 0xc2 || marker === 0xc3 ||
      marker === 0xc5 || marker === 0xc6 || marker === 0xc7 ||
      marker === 0xc9 || marker === 0xca || marker === 0xcb ||
      marker === 0xcd || marker === 0xce || marker === 0xcf
    ) {
      // SOF layout: [length(2), precision(1), height(2), width(2), components(1), ...]
      // i points at 0xFF; segment data starts at i+2 (after marker bytes)
      // Offset from i: +4=precision, +5,+6=height, +7,+8=width, +9=nComponents
      if (i + 9 < bytes.length) {
        const numComponents = bytes[i + 9]!;
        if (numComponents === 4) return true;
      }
      break;
    }

    i += 2 + length;
  }
  return false;
}

export async function decodeJpeg(bytes: Uint8Array): Promise<DecodedImage> {
  // Pre-decode CMYK check — mozjpeg calls exit(1) on CMYK images which kills Node
  if (detectCmyk(bytes)) {
    throw new DecodeError("unsupportedFeature", "jpeg", "CMYK color space is not supported");
  }

  // Sniff dimensions from the first SOF marker BEFORE the WASM decoder
  // allocates the raster. Spec §3.1 requires this ordering: a file whose
  // header declares a huge canvas must error with imageTooLarge rather
  // than allocating then complaining.
  const sniffed = sniffJpegDimensions(bytes);
  if (sniffed) {
    checkDimensions(sniffed.width, sniffed.height, "jpeg");
  }

  await ensureWasmInit();

  // Node.js Buffer.buffer may be a shared pool with non-zero byteOffset;
  // always slice to get a fresh ArrayBuffer aligned to the payload.
  const arrayBuffer = bytes.buffer.slice(
    bytes.byteOffset,
    bytes.byteOffset + bytes.byteLength,
  ) as ArrayBuffer;

  let result: ImageData;
  try {
    result = await jsquashDecode(arrayBuffer);
  } catch (e: any) {
    const msg: string = e?.message ?? String(e);
    throw new DecodeError("corruptInput", "jpeg", `JPEG decode failed: ${msg}`);
  }

  if (!result) {
    throw new DecodeError("corruptInput", "jpeg", "JPEG decode returned null");
  }

  checkDimensions(result.width, result.height, "jpeg");

  // mozjpeg returns RGBA; strip the alpha channel to produce packed RGB
  const pixels = result.width * result.height;
  const rgb = new Uint8Array(pixels * 3);
  const rgba = result.data;
  for (let i = 0, di = 0, si = 0; i < pixels; i++, si += 4, di += 3) {
    rgb[di] = rgba[si]!;
    rgb[di + 1] = rgba[si + 1]!;
    rgb[di + 2] = rgba[si + 2]!;
  }

  return {
    width: result.width,
    height: result.height,
    data: rgb,
    channels: 3,
    format: "jpeg",
  };
}
