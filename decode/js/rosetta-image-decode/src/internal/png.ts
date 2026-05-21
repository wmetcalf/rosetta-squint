import { PNG } from "pngjs";

import { DecodeError } from "../errors.js";
import type { DecodedImage } from "../types.js";

// Byte offset of bit-depth in a PNG IHDR chunk:
// 8 (magic) + 4 (chunk length) + 4 (chunk type "IHDR") + 4 (width) + 4 (height) = 24
const PNG_IHDR_BITDEPTH_OFFSET = 24;
const PNG_IHDR_COLORTYPE_OFFSET = 25;

function readPngHeaderBitDepth(bytes: Uint8Array): { bitDepth: number; colorType: number } | null {
  if (bytes.length < PNG_IHDR_COLORTYPE_OFFSET + 1) return null;
  return {
    bitDepth: bytes[PNG_IHDR_BITDEPTH_OFFSET]!,
    colorType: bytes[PNG_IHDR_COLORTYPE_OFFSET]!,
  };
}

export function decodePng(bytes: Uint8Array): DecodedImage {
  const buf = Buffer.from(bytes.buffer, bytes.byteOffset, bytes.byteLength);
  const header = readPngHeaderBitDepth(bytes);
  const is16bit = header?.bitDepth === 16;

  let png: PNG;
  try {
    // Use skipRescale only for 16-bit images so we can apply PIL-compatible conversion.
    // For 1/2/4/8-bit images, pngjs's normal scaling is already correct.
    png = PNG.sync.read(buf, is16bit ? { skipRescale: true } : {});
  } catch (e: any) {
    throw new DecodeError("corruptInput", "png", `PNG.sync.read failed: ${e.message ?? e}`);
  }

  const width = png.width;
  const height = png.height;
  const ct = (png as any).colorType as number;

  // Determine if the source image has an alpha channel.
  //   color type 0 = grayscale (no alpha)
  //   color type 2 = RGB (no alpha)
  //   color type 3 = paletted (alpha iff tRNS chunk present — pngjs sets `alpha` boolean)
  //   color type 4 = grayscale+alpha
  //   color type 6 = RGB+alpha
  const sourceAlphaFlag = (png as any).alpha === true;
  const sourceHasAlpha = ct === 4 || ct === 6 || (ct === 3 && sourceAlphaFlag);

  if (is16bit) {
    // pngjs data is a Uint16Array with skipRescale=true: one uint16 per channel (RGBA layout).
    // PIL conversion rules (from PIL/PngImagePlugin.py rawmode handling):
    //   colorType 0 (16-bit grayscale, no alpha): min(value, 255)
    //   all other 16-bit types:                   value >> 8  (top byte)
    const src = png.data as unknown as Uint16Array;
    const useClip = ct === 0; // only 16-bit grayscale uses min(v, 255)

    if (sourceHasAlpha) {
      // Output is RGBA (4 channels)
      const out = new Uint8Array(width * height * 4);
      for (let i = 0; i < width * height * 4; i++) {
        const v = src[i]!;
        out[i] = useClip ? Math.min(v, 255) : (v >> 8);
      }
      return { width, height, data: out, channels: 4, format: "png" };
    } else {
      // Output is RGB (3 channels); pngjs expands grayscale to R=G=B in RGBA buffer
      const out = new Uint8Array(width * height * 3);
      let si = 0;
      let di = 0;
      for (let i = 0; i < width * height; i++) {
        const r = src[si]!;
        const g = src[si + 1]!;
        const b = src[si + 2]!;
        // si+3 is alpha (always 65535 for no-alpha images) — skip
        out[di] = useClip ? Math.min(r, 255) : (r >> 8);
        out[di + 1] = useClip ? Math.min(g, 255) : (g >> 8);
        out[di + 2] = useClip ? Math.min(b, 255) : (b >> 8);
        si += 4;
        di += 3;
      }
      return { width, height, data: out, channels: 3, format: "png" };
    }
  }

  // 8-bit (or 1/2/4-bit expanded to 8-bit by pngjs): data is a Buffer with uint8 RGBA values.
  if (sourceHasAlpha) {
    return {
      width,
      height,
      data: new Uint8Array(png.data),
      channels: 4,
      format: "png",
    };
  }

  // Convert RGBA → RGB by dropping the alpha byte.
  // For grayscale source, pngjs has already expanded R=G=B; we just drop alpha.
  const rgbData = new Uint8Array(width * height * 3);
  let si = 0;
  let di = 0;
  for (let i = 0; i < width * height; i++) {
    rgbData[di] = png.data[si]!;
    rgbData[di + 1] = png.data[si + 1]!;
    rgbData[di + 2] = png.data[si + 2]!;
    si += 4;
    di += 3;
  }
  return {
    width,
    height,
    data: rgbData,
    channels: 3,
    format: "png",
  };
}
