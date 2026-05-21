import { GifReader } from "omggif";

import { DecodeError } from "../errors.js";
import type { DecodedImage } from "../types.js";
import { checkDimensions } from "./limits.js";

export function decodeGif(bytes: Uint8Array): DecodedImage {
  let reader: GifReader;
  try {
    reader = new GifReader(Buffer.from(bytes.buffer, bytes.byteOffset, bytes.byteLength));
  } catch (e: any) {
    throw new DecodeError("corruptInput", "gif", `GifReader failed: ${e.message ?? e}`);
  }

  if (reader.numFrames() === 0) {
    throw new DecodeError("corruptInput", "gif", "no frames in GIF");
  }

  const width = reader.width;
  const height = reader.height;
  checkDimensions(width, height, "gif");

  // Detect transparency for first frame.
  let frameInfo;
  try {
    frameInfo = reader.frameInfo(0);
  } catch (e: any) {
    throw new DecodeError("corruptInput", "gif", `frameInfo failed: ${e.message ?? e}`);
  }
  const transparentIndex = frameInfo.transparent_index;
  const hasAlpha = transparentIndex !== null && transparentIndex >= 0;

  // omggif's decodeAndBlitFrameRGBA skips transparent pixels (leaves them as zero
  // in the output buffer). PIL preserves the original palette RGB for transparent
  // pixels (alpha=0, but R/G/B = palette[transparent_index]). We must restore that.
  const rgbaBuf = new Uint8Array(width * height * 4);
  try {
    reader.decodeAndBlitFrameRGBA(0, rgbaBuf);
  } catch (e: any) {
    throw new DecodeError("corruptInput", "gif", `decodeAndBlitFrameRGBA failed: ${e.message ?? e}`);
  }

  if (hasAlpha) {
    // Restore palette RGB for transparent pixels to match PIL behavior.
    // frameInfo.palette_offset is the byte offset within `bytes` where the
    // color table starts (global or local). We read palette[transparentIndex]
    // directly from the original bytes.
    const paletteOffset = (frameInfo as any).palette_offset as number | null;
    if (paletteOffset !== null && paletteOffset !== undefined) {
      const tIdx = transparentIndex! * 3;
      const tR = bytes[paletteOffset + tIdx]!;
      const tG = bytes[paletteOffset + tIdx + 1]!;
      const tB = bytes[paletteOffset + tIdx + 2]!;
      for (let i = 0; i < width * height; i++) {
        if (rgbaBuf[i * 4 + 3] === 0) {
          rgbaBuf[i * 4] = tR;
          rgbaBuf[i * 4 + 1] = tG;
          rgbaBuf[i * 4 + 2] = tB;
        }
      }
    }
    return { width, height, data: rgbaBuf, channels: 4, format: "gif" };
  }

  // No transparency: strip alpha channel, output RGB.
  const rgb = new Uint8Array(width * height * 3);
  for (let i = 0, di = 0, si = 0; i < width * height; i++, si += 4, di += 3) {
    rgb[di] = rgbaBuf[si]!;
    rgb[di + 1] = rgbaBuf[si + 1]!;
    rgb[di + 2] = rgbaBuf[si + 2]!;
  }
  return { width, height, data: rgb, channels: 3, format: "gif" };
}
