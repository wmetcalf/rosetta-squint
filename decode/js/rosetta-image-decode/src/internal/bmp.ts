import { DecodeError } from "../errors.js";
import type { DecodedImage } from "../types.js";

export function decodeBmp(_bytes: Uint8Array): DecodedImage {
  throw new DecodeError("unsupportedFeature", "bmp", "BMP decoder not yet implemented");
}
