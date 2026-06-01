import type { DecodedImage } from "../types.js";
import { sniffHeicDimensions } from "./dimensionSniff.js";
import { checkDimensions, MAX_PIXELS } from "./limits.js";
import { decodeHeicWasm } from "./heicwasm.js";

// HEIC is decoded by the shared libheif+libde265 WASM module (decode/wasm/),
// the same artifact every port uses — so HEIC output is byte-identical across
// languages. This replaces libheif-js, whose emscripten build disables the HEVC
// deblocking + SAO filters and so diverged ±1-2 px (the old ±2 tolerance).
export async function decodeHeic(bytes: Uint8Array): Promise<DecodedImage> {
  // Sniff the container's primary-item ispe dimensions BEFORE decoding.
  // libheif reports HEVC-bitstream dimensions, not the container's ispe, so a
  // patched ispe is only caught here. Spec §3.1.
  const sniffed = sniffHeicDimensions(bytes);
  if (sniffed) {
    checkDimensions(sniffed.width, sniffed.height, "heic");
  }

  const res = await decodeHeicWasm(bytes, MAX_PIXELS);
  return {
    width: res.width,
    height: res.height,
    data: res.data,
    channels: res.channels,
    format: "heic",
  };
}
