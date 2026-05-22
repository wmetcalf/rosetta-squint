import { decodeBmp } from "./internal/bmp.js";
import { decodeGif } from "./internal/gif.js";
import { decodeHeic } from "./internal/heic.js";
import { decodeJpeg } from "./internal/jpeg.js";
import { decodePng } from "./internal/png.js";
import { decodeTiff } from "./internal/tiff.js";
import { decodeWebp } from "./internal/webp.js";
import { DecodeError } from "./errors.js";
import type { DecodedImage, Format } from "./types.js";

export async function decode(bytes: Uint8Array): Promise<DecodedImage> {
  const fmt = detectFormat(bytes);
  if (fmt == null) {
    throw new DecodeError("unsupportedFormat", null, "");
  }
  switch (fmt) {
    case "bmp":
      return decodeBmp(bytes);
    case "png":
      return decodePng(bytes);
    case "gif":
      return decodeGif(bytes);
    case "jpeg":
      return await decodeJpeg(bytes);
    case "webp":
      return await decodeWebp(bytes);
    case "tiff":
      return await decodeTiff(bytes);
    case "heic":
      return await decodeHeic(bytes);
    default:
      throw new DecodeError("unsupportedFormat", fmt, "");
  }
}

export function detectFormat(bytes: Uint8Array): Format | null {
  if (bytes.length < 2) return null;
  if (bytes[0] === 0x42 && bytes[1] === 0x4d) return "bmp";
  if (
    bytes.length >= 8 &&
    bytes[0] === 0x89 && bytes[1] === 0x50 && bytes[2] === 0x4e && bytes[3] === 0x47 &&
    bytes[4] === 0x0d && bytes[5] === 0x0a && bytes[6] === 0x1a && bytes[7] === 0x0a
  ) {
    return "png";
  }
  if (
    bytes.length >= 6 &&
    bytes[0] === 0x47 && bytes[1] === 0x49 && bytes[2] === 0x46 && bytes[3] === 0x38 &&
    (bytes[4] === 0x37 || bytes[4] === 0x39) && bytes[5] === 0x61
  ) {
    return "gif";
  }
  if (bytes.length >= 2 && bytes[0] === 0xff && bytes[1] === 0xd8) {
    return "jpeg";
  }
  if (
    bytes.length >= 12 &&
    bytes[0] === 0x52 && bytes[1] === 0x49 && bytes[2] === 0x46 && bytes[3] === 0x46 &&
    bytes[8] === 0x57 && bytes[9] === 0x45 && bytes[10] === 0x42 && bytes[11] === 0x50
  ) {
    return "webp";
  }
  if (
    bytes.length >= 4 && (
      // Little-endian TIFF: II + 42
      (bytes[0] === 0x49 && bytes[1] === 0x49 && bytes[2] === 0x2a && bytes[3] === 0x00) ||
      // Big-endian TIFF: MM + 42
      (bytes[0] === 0x4d && bytes[1] === 0x4d && bytes[2] === 0x00 && bytes[3] === 0x2a)
    )
  ) {
    return "tiff";
  }
  // HEIC/HEIF: ISO Base Media File Format with ftyp box at bytes 4..7
  // Bytes 8..11 = major brand; only accept HEVC-based brands, reject avif.
  if (
    bytes.length >= 12 &&
    bytes[4] === 0x66 && bytes[5] === 0x74 && bytes[6] === 0x79 && bytes[7] === 0x70
  ) {
    const brand = String.fromCharCode(bytes[8]!, bytes[9]!, bytes[10]!, bytes[11]!);
    if (["heic", "heix", "mif1", "msf1", "hevc", "hevx"].includes(brand)) {
      return "heic";
    }
    // avif and other unrecognized ftyp brands → unsupportedFormat
    return null;
  }
  return null;
}

export function supportedFormats(): Format[] {
  return ["bmp", "png", "gif", "jpeg", "webp", "tiff", "heic"];
}
