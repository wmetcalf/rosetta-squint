import { decodeBmp } from "./internal/bmp.js";
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
    default:
      throw new DecodeError("unsupportedFormat", fmt, "");
  }
}

export function detectFormat(bytes: Uint8Array): Format | null {
  if (bytes.length < 2) return null;
  if (bytes[0] === 0x42 && bytes[1] === 0x4d) return "bmp";
  return null;
}

export function supportedFormats(): Format[] {
  return ["bmp"];
}
