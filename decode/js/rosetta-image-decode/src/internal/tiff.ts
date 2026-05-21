import { createRequire } from "node:module";
import { DecodeError } from "../errors.js";
import type { DecodedImage } from "../types.js";

// utif2 is CommonJS; use createRequire to import it from our ESM context.
const require = createRequire(import.meta.url);
// eslint-disable-next-line @typescript-eslint/no-var-requires
const UTIF = require("utif2") as {
  decode(buf: ArrayBuffer): UTIF_IFD[];
  decodeImage(buf: ArrayBuffer, ifd: UTIF_IFD): void;
  toRGBA8(ifd: UTIF_IFD): Uint8Array;
};

interface UTIF_IFD {
  width: number;
  height: number;
  [key: string]: unknown;
}

export function decodeTiff(bytes: Uint8Array): DecodedImage {
  // Produce a clean ArrayBuffer slice (Node Buffer.buffer may share a pool).
  const ab = bytes.buffer.slice(
    bytes.byteOffset,
    bytes.byteOffset + bytes.byteLength,
  ) as ArrayBuffer;

  let ifds: UTIF_IFD[];
  try {
    ifds = UTIF.decode(ab);
  } catch (e: any) {
    throw new DecodeError(
      "corruptInput",
      "tiff",
      `TIFF decode failed: ${e?.message ?? String(e)}`,
    );
  }

  if (!ifds || ifds.length === 0) {
    throw new DecodeError("corruptInput", "tiff", "no images in TIFF");
  }

  const ifd = ifds[0]!;

  try {
    UTIF.decodeImage(ab, ifd);
  } catch (e: any) {
    throw new DecodeError(
      "corruptInput",
      "tiff",
      `TIFF image decode failed: ${e?.message ?? String(e)}`,
    );
  }

  let rgbaBuf: Uint8Array;
  try {
    rgbaBuf = UTIF.toRGBA8(ifd);
  } catch (e: any) {
    throw new DecodeError(
      "corruptInput",
      "tiff",
      `TIFF toRGBA8 failed: ${e?.message ?? String(e)}`,
    );
  }

  const width = ifd.width;
  const height = ifd.height;

  if (!width || !height || width <= 0 || height <= 0) {
    throw new DecodeError("corruptInput", "tiff", "TIFF has zero-size dimensions");
  }

  // Output packed RGB (3 channels), stripping alpha
  const pixels = width * height;
  const rgb = new Uint8Array(pixels * 3);
  for (let i = 0, di = 0, si = 0; i < pixels; i++, si += 4, di += 3) {
    rgb[di] = rgbaBuf[si]!;
    rgb[di + 1] = rgbaBuf[si + 1]!;
    rgb[di + 2] = rgbaBuf[si + 2]!;
  }

  return { width, height, data: rgb, channels: 3, format: "tiff" };
}
