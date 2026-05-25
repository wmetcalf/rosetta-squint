import { DecodeError } from "../errors.js";
import type { DecodedImage } from "../types.js";
import { sniffWebpDimensions } from "./dimensionSniff.js";
import { checkDimensions } from "./limits.js";
import { loadWasmModule, resolvePackageWasmUrl } from "./loadWasm.js";

// @jsquash/webp: community-maintained libwebp WASM fork of squoosh's webp codec.
import { init, default as webpDecode } from "@jsquash/webp/decode.js";

let wasmInitPromise: Promise<void> | null = null;

async function ensureWasmInit(): Promise<void> {
  if (wasmInitPromise) return wasmInitPromise;

  wasmInitPromise = (async () => {
    // Locate the WASM binary tolerantly: in Node, resolve via the package
    // graph (handles hoisted node_modules layouts); in browsers, fall back
    // to the bundler-relative URL pattern that esbuild/vite/webpack rewrite
    // to point at the emitted asset path.
    const wasmUrl = await resolvePackageWasmUrl(
      "@jsquash/webp/codec/dec/webp_dec.wasm",
      "../../node_modules/@jsquash/webp/codec/dec/webp_dec.wasm",
      import.meta.url,
    );
    const wasmModule = await loadWasmModule(wasmUrl);
    await init(wasmModule);
  })();

  // Reset on rejection so a transient failure doesn't poison every future call.
  wasmInitPromise.catch(() => { wasmInitPromise = null; });

  return wasmInitPromise;
}

/**
 * Detect whether a WebP bitstream carries an alpha channel.
 *
 * WebP has three container layouts:
 *   VP8  (lossy, no alpha) — never has alpha
 *   VP8L (lossless)       — has alpha when bit 4 of the first transform/flag byte is set
 *   VP8X (extended)       — has alpha when bit 4 of the features byte is set
 *
 * RIFF header: RIFF(4) + size(4) + WEBP(4) = 12 bytes
 * Then chunk FourCC at bytes[12..15], chunk size at bytes[16..19], chunk data starts at bytes[20]
 */
function detectWebpAlpha(bytes: Uint8Array): boolean {
  if (bytes.length < 20) return false;
  const chunkType = String.fromCharCode(bytes[12]!, bytes[13]!, bytes[14]!, bytes[15]!);
  if (chunkType === "VP8X") {
    // Extended WebP — features flags byte at offset 20
    // Bit 4 (0x10) = alpha channel present
    if (bytes.length < 21) return false;
    return (bytes[20]! & 0x10) !== 0;
  }
  if (chunkType === "VP8L") {
    // Lossless WebP — VP8L signature 0x2F at offset 20,
    // then 4 bytes where bit 4 of byte[24] = alpha_is_used.
    // Validate the 0x2F signature byte first; a hostile non-VP8L file with
    // a forged "VP8L" fourcc but garbage signature byte must not mislead
    // the alpha-detection heuristic.
    if (bytes.length < 25) return false;
    if (bytes[20] !== 0x2F) return false;
    return (bytes[24]! & 0x10) !== 0;
  }
  // VP8 (lossy without alpha container) — never has alpha
  return false;
}

export async function decodeWebp(bytes: Uint8Array): Promise<DecodedImage> {
  // Sniff VP8X canvas dimensions BEFORE the WASM decoder runs. libwebp's
  // WebPGetFeatures rejects oversized VP8X canvases with VP8_STATUS_BITSTREAM_ERROR
  // which would surface as corruptInput; this pre-check produces the canonical
  // imageTooLarge error required by Spec §3.1.
  const sniffed = sniffWebpDimensions(bytes);
  if (sniffed) {
    checkDimensions(sniffed.width, sniffed.height, "webp");
  }

  await ensureWasmInit();

  // @jsquash/webp decode() accepts ArrayBuffer.
  // Node.js Buffer.buffer may be a shared pool with non-zero byteOffset;
  // always slice to get a fresh ArrayBuffer aligned to the payload.
  const arrayBuffer = bytes.buffer.slice(
    bytes.byteOffset,
    bytes.byteOffset + bytes.byteLength,
  ) as ArrayBuffer;

  let result: ImageData;
  try {
    result = await webpDecode(arrayBuffer);
  } catch (e: any) {
    const msg: string = e?.message ?? String(e);
    throw new DecodeError("corruptInput", "webp", `WebP decode failed: ${msg}`);
  }

  if (!result) {
    throw new DecodeError("corruptInput", "webp", "WebP decode returned null");
  }

  const width = result.width;
  const height = result.height;
  checkDimensions(width, height, "webp");
  const rgba = result.data; // Uint8ClampedArray, always RGBA from libwebp

  // Detect whether the source WebP had alpha; if not, strip the channel → RGB
  const hasAlpha = detectWebpAlpha(bytes);

  if (hasAlpha) {
    return {
      width,
      height,
      data: new Uint8Array(rgba),
      channels: 4,
      format: "webp",
    };
  }

  // Drop alpha → packed RGB
  const pixels = width * height;
  const rgb = new Uint8Array(pixels * 3);
  for (let i = 0, di = 0, si = 0; i < pixels; i++, si += 4, di += 3) {
    rgb[di] = rgba[si]!;
    rgb[di + 1] = rgba[si + 1]!;
    rgb[di + 2] = rgba[si + 2]!;
  }

  return {
    width,
    height,
    data: rgb,
    channels: 3,
    format: "webp",
  };
}
