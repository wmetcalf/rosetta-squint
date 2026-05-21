import { decodeBmp } from "./internal/bmp.js";
import { decodePng } from "./internal/png.js";
import { DecodeError } from "./errors.js";
import type { DecodedImage, Format } from "./types.js";

export function decode(bytes: Uint8Array): DecodedImage {
  const fmt = detectFormat(bytes);
  if (fmt == null) {
    throw new DecodeError("unsupportedFormat", null, "");
  }
  switch (fmt) {
    case "bmp":
      return decodeBmp(bytes);
    case "png":
      return decodePng(bytes);
    default:
      throw new DecodeError("unsupportedFormat", fmt, "");
  }
}

export function detectFormat(bytes: Uint8Array): Format | null {
  if (bytes.length < 2) return null;
  if (bytes[0] === 0x42 && bytes[1] === 0x4d) return "bmp";
  if (bytes.length >= 8 &&
      bytes[0] === 0x89 && bytes[1] === 0x50 && bytes[2] === 0x4e && bytes[3] === 0x47 &&
      bytes[4] === 0x0d && bytes[5] === 0x0a && bytes[6] === 0x1a && bytes[7] === 0x0a) {
    return "png";
  }
  return null;
}

export function supportedFormats(): Format[] {
  return ["bmp", "png"];
}
