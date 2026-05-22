import { DecodeError } from "../errors.js";
import type { DecodedImage } from "../types.js";
import { checkDimensions } from "./limits.js";

// utif2 is shipped as CommonJS. Use a dynamic ESM import with default interop;
// modern bundlers (esbuild, vite, rollup, webpack 5+) handle CJS→ESM for npm
// packages automatically. Browser users without a bundler need to load utif2
// separately and call decodeTiff with the UTIF object — see decodeTiffWithUtif.
interface UTIFLib {
  decode(buf: ArrayBuffer): UTIF_IFD[];
  decodeImage(buf: ArrayBuffer, ifd: UTIF_IFD): void;
  toRGBA8(ifd: UTIF_IFD): Uint8Array;
}

let utifPromise: Promise<UTIFLib> | null = null;
async function loadUtif(): Promise<UTIFLib> {
  if (!utifPromise) {
    utifPromise = (async () => {
      const mod = await import("utif2");
      // CJS interop: utif2's CJS module.exports lands on either default or root.
      const utif: any = (mod as any).default ?? mod;
      return utif as UTIFLib;
    })();
  }
  return utifPromise;
}

interface UTIF_IFD {
  width: number;
  height: number;
  [key: string]: unknown;
}

export async function decodeTiff(bytes: Uint8Array): Promise<DecodedImage> {
  const UTIF = await loadUtif();
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

  // utif2: UTIF.decode() populates TIFF tag arrays (t256 = ImageWidth, t257 = ImageLength)
  // but does NOT set ifd.width / ifd.height until after UTIF.decodeImage().
  // Read dimensions from the tag arrays to guard before any size-proportional allocation.
  {
    const tagW = (ifd as any).t256;
    const tagH = (ifd as any).t257;
    const w: number = Array.isArray(tagW) ? (tagW[0] as number) : (tagW as number);
    const h: number = Array.isArray(tagH) ? (tagH[0] as number) : (tagH as number);
    checkDimensions(w, h, "tiff");
  }

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
