import { DecodeError } from "../errors.js";
import type { DecodedImage } from "../types.js";
import { checkDimensions } from "./limits.js";
import { loadWasmModule } from "./loadWasm.js";

// @jsquash/webp: community-maintained libwebp WASM fork of squoosh's webp codec.
import { init, default as webpDecode } from "@jsquash/webp/decode.js";

let wasmInitPromise: Promise<void> | null = null;

async function ensureWasmInit(): Promise<void> {
  if (wasmInitPromise) return wasmInitPromise;

  wasmInitPromise = (async () => {
    // Resolves to file:// in Node, http(s):// or blob: in browser.
    const wasmUrl = new URL(
      "../../node_modules/@jsquash/webp/codec/dec/webp_dec.wasm",
      import.meta.url,
    );
    const wasmModule = await loadWasmModule(wasmUrl);
    await init(wasmModule);
  })();

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
    // then 4 bytes where bit 4 of byte[24] = alpha_is_used
    if (bytes.length < 25) return false;
    return (bytes[24]! & 0x10) !== 0;
  }
  // VP8 (lossy without alpha container) — never has alpha
  return false;
}

export async function decodeWebp(bytes: Uint8Array): Promise<DecodedImage> {
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
