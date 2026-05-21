import { DecodeError } from "../errors.js";
import type { Format } from "../types.js";

export const MAX_PIXELS = 256 * 1024 * 1024; // 268_435_456

export function checkDimensions(width: number, height: number, format: Format): void {
  if (!Number.isInteger(width) || !Number.isInteger(height) || width <= 0 || height <= 0) {
    throw new DecodeError("corruptInput", format, `non-positive dimensions ${width}x${height}`);
  }
  const pixels = width * height; // JS numbers are double; safe up to 2^53
  if (pixels > MAX_PIXELS) {
    throw new DecodeError(
      "imageTooLarge",
      format,
      `declared dimensions ${width}x${height} = ${pixels} pixels exceeds MAX_PIXELS = ${MAX_PIXELS}`,
    );
  }
}
