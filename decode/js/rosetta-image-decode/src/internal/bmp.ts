import { DecodeError } from "../errors.js";
import type { Channels, DecodedImage } from "../types.js";
import { checkDimensions } from "./limits.js";

const BI_RGB = 0;
const BI_RLE8 = 1;
const BI_RLE4 = 2;
const BI_BITFIELDS = 3;
const BI_JPEG = 4;
const BI_PNG = 5;
const BI_ALPHABITFIELDS = 6;

interface BmpHeader {
  width: number;
  height: number;
  topDown: boolean;
  bitCount: number;
  compression: number;
  clrUsed: number;
  redMask: number;
  greenMask: number;
  blueMask: number;
  alphaMask: number;
  pixelDataOffset: number;
  dibHeaderSize: number;
}

function trailingZeros32(n: number): number {
  if (n === 0) return 32;
  let count = 0;
  let v = n >>> 0;
  while ((v & 1) === 0) {
    count++;
    v >>>= 1;
  }
  return count;
}

export function decodeBmp(bytes: Uint8Array): DecodedImage {
  const hdr = parseHeader(bytes);

  if (hdr.compression === BI_RGB) {
    if (hdr.bitCount === 24) return decodeRgb24(bytes, hdr);
    if (hdr.bitCount === 32) return decodeRgb32(bytes, hdr);
    if (hdr.bitCount === 8) return decodePal8(bytes, hdr);
    if (hdr.bitCount === 4) return decodePal4(bytes, hdr);
    if (hdr.bitCount === 1) return decodePal1(bytes, hdr);
    throw new DecodeError("corruptInput", "bmp", `biBitCount ${hdr.bitCount} for BI_RGB`);
  }
  if (hdr.compression === BI_BITFIELDS || hdr.compression === BI_ALPHABITFIELDS) {
    if (hdr.bitCount === 16) return decodeBitfields(bytes, hdr, 16);
    if (hdr.bitCount === 32) return decodeBitfields(bytes, hdr, 32);
    throw new DecodeError("corruptInput", "bmp", `BI_BITFIELDS with biBitCount ${hdr.bitCount}`);
  }
  if (hdr.compression === BI_RLE8) return decodeRle(bytes, hdr, 8);
  if (hdr.compression === BI_RLE4) return decodeRle(bytes, hdr, 4);
  throw new DecodeError("corruptInput", "bmp", `biCompression ${hdr.compression} unreachable`);
}

function parseHeader(bytes: Uint8Array): BmpHeader {
  if (bytes.length < 14) {
    throw new DecodeError("truncated", "bmp", "file header truncated");
  }
  if (bytes[0] !== 0x42 || bytes[1] !== 0x4d) {
    throw new DecodeError("corruptInput", "bmp", "Not a BMP file (no 'BM' signature)");
  }

  const view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);

  const bfOffBits = view.getUint32(10, true);

  if (bytes.length < 18) {
    throw new DecodeError("truncated", "bmp", "DIB header size not readable");
  }
  const biSize = view.getUint32(14, true);

  if (biSize === 12) {
    throw new DecodeError("unsupportedFeature", "bmp", "OS/2 BMP header (size 12)");
  }
  if (biSize !== 40 && biSize !== 52 && biSize !== 56 && biSize !== 108 && biSize !== 124) {
    throw new DecodeError("corruptInput", "bmp", `DIB header size ${biSize} not supported`);
  }
  if (bytes.length < 14 + biSize) {
    throw new DecodeError("truncated", "bmp", "DIB header truncated");
  }

  const biWidth = view.getInt32(18, true);
  const biHeight = view.getInt32(22, true);
  const biPlanes = view.getUint16(26, true);
  const biBitCount = view.getUint16(28, true);
  const biCompression = view.getUint32(30, true);
  const biClrUsed = view.getUint32(46, true);

  if (biWidth <= 0) {
    throw new DecodeError("corruptInput", "bmp", "biWidth must be positive");
  }
  if (biHeight === 0) {
    throw new DecodeError("corruptInput", "bmp", "biHeight must be non-zero");
  }
  if (biPlanes !== 1) {
    throw new DecodeError("corruptInput", "bmp", "biPlanes must be 1");
  }
  if (
    biBitCount !== 1 &&
    biBitCount !== 4 &&
    biBitCount !== 8 &&
    biBitCount !== 16 &&
    biBitCount !== 24 &&
    biBitCount !== 32
  ) {
    throw new DecodeError("corruptInput", "bmp", `biBitCount ${biBitCount} not supported`);
  }
  if (biCompression > 6) {
    throw new DecodeError("corruptInput", "bmp", `biCompression ${biCompression} not supported`);
  }
  if (biCompression === BI_JPEG) {
    throw new DecodeError("unsupportedFeature", "bmp", "embedded JPEG");
  }
  if (biCompression === BI_PNG) {
    throw new DecodeError("unsupportedFeature", "bmp", "embedded PNG");
  }

  // Masks if applicable
  let redMask = 0;
  let greenMask = 0;
  let blueMask = 0;
  let alphaMask = 0;

  const hasMasks =
    biCompression === BI_BITFIELDS ||
    biCompression === BI_ALPHABITFIELDS ||
    biSize >= 52;

  if (hasMasks) {
    if (bytes.length < 14 + 40 + 12) {
      throw new DecodeError("truncated", "bmp", "BI_BITFIELDS masks truncated");
    }
    redMask = view.getUint32(54, true);
    greenMask = view.getUint32(58, true);
    blueMask = view.getUint32(62, true);

    if (biCompression === BI_ALPHABITFIELDS || biSize >= 56) {
      if (bytes.length < 14 + 40 + 16) {
        throw new DecodeError("truncated", "bmp", "alpha mask truncated");
      }
      alphaMask = view.getUint32(66, true);
    }

    if (biCompression === BI_BITFIELDS) {
      if (redMask === 0 || greenMask === 0 || blueMask === 0) {
        throw new DecodeError("corruptInput", "bmp", "BI_BITFIELDS mask is zero");
      }
    }
  }

  const topDown = biHeight < 0;
  const absHeight = Math.abs(biHeight);

  checkDimensions(biWidth, absHeight, "bmp");

  return {
    width: biWidth,
    height: absHeight,
    topDown,
    bitCount: biBitCount,
    compression: biCompression,
    clrUsed: biClrUsed,
    redMask,
    greenMask,
    blueMask,
    alphaMask,
    pixelDataOffset: bfOffBits,
    dibHeaderSize: biSize,
  };
}

function decodeRgb24(bytes: Uint8Array, hdr: BmpHeader): DecodedImage {
  const stride = Math.trunc((hdr.width * 3 + 3) / 4) * 4;
  if (bytes.length - hdr.pixelDataOffset < stride * hdr.height) {
    throw new DecodeError("truncated", "bmp", "pixel data truncated (24-bit RGB)");
  }
  const pixels = new Uint8Array(hdr.width * hdr.height * 3);
  for (let srcRow = 0; srcRow < hdr.height; srcRow++) {
    const dstRow = hdr.topDown ? srcRow : hdr.height - 1 - srcRow;
    for (let x = 0; x < hdr.width; x++) {
      const srcIdx = hdr.pixelDataOffset + srcRow * stride + x * 3;
      const dstIdx = (dstRow * hdr.width + x) * 3;
      pixels[dstIdx] = bytes[srcIdx + 2]!;     // R (from BGR+2)
      pixels[dstIdx + 1] = bytes[srcIdx + 1]!; // G
      pixels[dstIdx + 2] = bytes[srcIdx]!;     // B (from BGR+0)
    }
  }
  return { width: hdr.width, height: hdr.height, data: pixels, channels: 3, format: "bmp" };
}

function decodeRgb32(bytes: Uint8Array, hdr: BmpHeader): DecodedImage {
  const stride = hdr.width * 4;
  if (bytes.length - hdr.pixelDataOffset < stride * hdr.height) {
    throw new DecodeError("truncated", "bmp", "pixel data truncated (32-bit RGB)");
  }
  // Always output RGB (discard alpha) to match Pillow 11 behavior
  const pixels = new Uint8Array(hdr.width * hdr.height * 3);
  for (let srcRow = 0; srcRow < hdr.height; srcRow++) {
    const dstRow = hdr.topDown ? srcRow : hdr.height - 1 - srcRow;
    for (let x = 0; x < hdr.width; x++) {
      const srcIdx = hdr.pixelDataOffset + srcRow * stride + x * 4;
      const dstIdx = (dstRow * hdr.width + x) * 3;
      pixels[dstIdx] = bytes[srcIdx + 2]!;     // R (from BGRA+2)
      pixels[dstIdx + 1] = bytes[srcIdx + 1]!; // G
      pixels[dstIdx + 2] = bytes[srcIdx]!;     // B (from BGRA+0)
      // alpha byte at srcIdx+3 discarded
    }
  }
  return { width: hdr.width, height: hdr.height, data: pixels, channels: 3, format: "bmp" };
}

/**
 * Clamp biClrUsed to the maximum entries the given bit-depth can index.
 * If clrUsed <= 0, returns the bit-depth maximum (existing default).
 * If clrUsed > bitDepthMax, clamps to bitDepthMax (PIL-lenient parsing).
 * Defends against attacker-controlled values (e.g. 0x40000000) that would
 * cause excessive palette allocation.
 */
function clampEntryCount(clrUsed: number, bitDepth: number): number {
  const bitDepthMax = 1 << bitDepth;
  if (clrUsed <= 0) return bitDepthMax;
  return Math.min(clrUsed, bitDepthMax);
}

function readColorTable(bytes: Uint8Array, hdr: BmpHeader, entryCount: number): number[][] {
  const colorTableOffset = 14 + hdr.dibHeaderSize;
  const colorTableEnd = colorTableOffset + entryCount * 4;
  if (bytes.length < colorTableEnd) {
    throw new DecodeError("truncated", "bmp", "color table truncated");
  }
  const palette: number[][] = [];
  for (let i = 0; i < entryCount; i++) {
    const off = colorTableOffset + i * 4;
    palette.push([
      bytes[off + 2]! & 0xff, // R
      bytes[off + 1]! & 0xff, // G
      bytes[off]! & 0xff,     // B
    ]);
  }
  return palette;
}

function decodePal8(bytes: Uint8Array, hdr: BmpHeader): DecodedImage {
  const entryCount = clampEntryCount(hdr.clrUsed, 8);
  const colorTableOffset = 14 + hdr.dibHeaderSize;
  const colorTableEnd = colorTableOffset + entryCount * 4;
  if (bytes.length < colorTableEnd) {
    throw new DecodeError("truncated", "bmp", "color table truncated (8-bit paletted)");
  }
  const palette: number[][] = [];
  for (let i = 0; i < entryCount; i++) {
    const off = colorTableOffset + i * 4;
    palette.push([
      bytes[off + 2]! & 0xff, // R
      bytes[off + 1]! & 0xff, // G
      bytes[off]! & 0xff,     // B
    ]);
  }
  const stride = Math.trunc((hdr.width + 3) / 4) * 4;
  if (bytes.length - hdr.pixelDataOffset < stride * hdr.height) {
    throw new DecodeError("truncated", "bmp", "pixel data truncated (8-bit paletted)");
  }
  const pixels = new Uint8Array(hdr.width * hdr.height * 3);
  for (let srcRow = 0; srcRow < hdr.height; srcRow++) {
    const dstRow = hdr.topDown ? srcRow : hdr.height - 1 - srcRow;
    for (let x = 0; x < hdr.width; x++) {
      const srcIdx = hdr.pixelDataOffset + srcRow * stride + x;
      let paletteIndex = bytes[srcIdx]! & 0xff;
      if (paletteIndex >= entryCount) paletteIndex = entryCount - 1;
      const dstIdx = (dstRow * hdr.width + x) * 3;
      const rgb = palette[paletteIndex]!;
      pixels[dstIdx] = rgb[0]!;
      pixels[dstIdx + 1] = rgb[1]!;
      pixels[dstIdx + 2] = rgb[2]!;
    }
  }
  return { width: hdr.width, height: hdr.height, data: pixels, channels: 3, format: "bmp" };
}

function decodePal4(bytes: Uint8Array, hdr: BmpHeader): DecodedImage {
  const entryCount = clampEntryCount(hdr.clrUsed, 4);
  const palette = readColorTable(bytes, hdr, entryCount);
  const stride = Math.trunc((hdr.width * 4 + 31) / 32) * 4;
  if (bytes.length - hdr.pixelDataOffset < stride * hdr.height) {
    throw new DecodeError("truncated", "bmp", "pixel data truncated (4-bit paletted)");
  }
  const pixels = new Uint8Array(hdr.width * hdr.height * 3);
  for (let srcRow = 0; srcRow < hdr.height; srcRow++) {
    const dstRow = hdr.topDown ? srcRow : hdr.height - 1 - srcRow;
    for (let x = 0; x < hdr.width; x++) {
      const byteOff = hdr.pixelDataOffset + srcRow * stride + Math.trunc(x / 2);
      const b = bytes[byteOff]! & 0xff;
      let idx = x % 2 === 0 ? b >> 4 : b & 0xf;
      if (idx >= entryCount) idx = entryCount - 1;
      const dstIdx = (dstRow * hdr.width + x) * 3;
      const rgb = palette[idx]!;
      pixels[dstIdx] = rgb[0]!;
      pixels[dstIdx + 1] = rgb[1]!;
      pixels[dstIdx + 2] = rgb[2]!;
    }
  }
  return { width: hdr.width, height: hdr.height, data: pixels, channels: 3, format: "bmp" };
}

function decodePal1(bytes: Uint8Array, hdr: BmpHeader): DecodedImage {
  const entryCount = clampEntryCount(hdr.clrUsed, 1);
  const palette = readColorTable(bytes, hdr, entryCount);
  const stride = Math.trunc((hdr.width + 31) / 32) * 4;
  if (bytes.length - hdr.pixelDataOffset < stride * hdr.height) {
    throw new DecodeError("truncated", "bmp", "pixel data truncated (1-bit paletted)");
  }
  const pixels = new Uint8Array(hdr.width * hdr.height * 3);
  for (let srcRow = 0; srcRow < hdr.height; srcRow++) {
    const dstRow = hdr.topDown ? srcRow : hdr.height - 1 - srcRow;
    for (let x = 0; x < hdr.width; x++) {
      const byteOff = hdr.pixelDataOffset + srcRow * stride + Math.trunc(x / 8);
      const b = bytes[byteOff]! & 0xff;
      // MSB first: bit 7 is pixel 0
      let idx = (b >> (7 - (x % 8))) & 1;
      if (idx >= entryCount) idx = entryCount - 1;
      const dstIdx = (dstRow * hdr.width + x) * 3;
      const rgb = palette[idx]!;
      pixels[dstIdx] = rgb[0]!;
      pixels[dstIdx + 1] = rgb[1]!;
      pixels[dstIdx + 2] = rgb[2]!;
    }
  }
  return { width: hdr.width, height: hdr.height, data: pixels, channels: 3, format: "bmp" };
}

function decodeBitfields(bytes: Uint8Array, hdr: BmpHeader, bitsPerPixel: number): DecodedImage {
  const hasAlpha = hdr.alphaMask !== 0;
  const channels: Channels = hasAlpha ? 4 : 3;

  const redShift = trailingZeros32(hdr.redMask);
  const greenShift = trailingZeros32(hdr.greenMask);
  const blueShift = trailingZeros32(hdr.blueMask);
  // Use float division to avoid int32 sign issues with high bits
  const redRange = (hdr.redMask >>> 0) / Math.pow(2, redShift);
  const greenRange = (hdr.greenMask >>> 0) / Math.pow(2, greenShift);
  const blueRange = (hdr.blueMask >>> 0) / Math.pow(2, blueShift);
  const alphaShift = hasAlpha ? trailingZeros32(hdr.alphaMask) : 0;
  const alphaRange = hasAlpha ? (hdr.alphaMask >>> 0) / Math.pow(2, alphaShift) : 1;

  const view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);

  const stride =
    bitsPerPixel === 16
      ? Math.trunc((hdr.width * 2 + 3) / 4) * 4
      : hdr.width * 4;

  if (bytes.length - hdr.pixelDataOffset < stride * hdr.height) {
    throw new DecodeError(
      "truncated",
      "bmp",
      `pixel data truncated (BI_BITFIELDS ${bitsPerPixel}-bit)`
    );
  }

  const pixels = new Uint8Array(hdr.width * hdr.height * channels);
  for (let srcRow = 0; srcRow < hdr.height; srcRow++) {
    const dstRow = hdr.topDown ? srcRow : hdr.height - 1 - srcRow;
    for (let x = 0; x < hdr.width; x++) {
      const srcIdx = hdr.pixelDataOffset + srcRow * stride;
      // Use unsigned 32-bit number (getUint16/getUint32 both return non-negative)
      const pixel =
        bitsPerPixel === 16
          ? view.getUint16(srcIdx + x * 2, true)
          : view.getUint32(srcIdx + x * 4, true);

      // Use float division for mask extraction to avoid int32 sign wrap on high bits
      const rMasked = ((pixel >>> 0) & (hdr.redMask >>> 0)) / Math.pow(2, redShift);
      const gMasked = ((pixel >>> 0) & (hdr.greenMask >>> 0)) / Math.pow(2, greenShift);
      const bMasked = ((pixel >>> 0) & (hdr.blueMask >>> 0)) / Math.pow(2, blueShift);

      const r = Math.floor((rMasked * 255) / redRange);
      const g = Math.floor((gMasked * 255) / greenRange);
      const b = Math.floor((bMasked * 255) / blueRange);

      const dstIdx = (dstRow * hdr.width + x) * channels;
      pixels[dstIdx] = r;
      pixels[dstIdx + 1] = g;
      pixels[dstIdx + 2] = b;

      if (hasAlpha) {
        const aMasked = ((pixel >>> 0) & (hdr.alphaMask >>> 0)) / Math.pow(2, alphaShift);
        const a = Math.floor((aMasked * 255) / alphaRange);
        pixels[dstIdx + 3] = a;
      }
    }
  }
  return { width: hdr.width, height: hdr.height, data: pixels, channels, format: "bmp" };
}

function decodeRle(bytes: Uint8Array, hdr: BmpHeader, bitsPerPixel: number): DecodedImage {
  const entryCount = clampEntryCount(hdr.clrUsed, bitsPerPixel);
  const palette = readColorTable(bytes, hdr, entryCount);

  const xsize = hdr.width;
  const ysize = hdr.height;

  // Replicate Pillow's BmpRleDecoder: accumulate pixel indices into flat buffer
  const dataBuf: number[] = [];
  let x = 0;
  let pos = hdr.pixelDataOffset;
  const end = bytes.length;

  outer: while (dataBuf.length < xsize * ysize) {
    if (pos + 1 >= end) break;
    const numPixelsRaw = bytes[pos++]! & 0xff;
    const dataByte = bytes[pos++]! & 0xff;

    if (numPixelsRaw !== 0) {
      // Encoded mode: clip at end of row (Pillow behavior)
      let numPixels = numPixelsRaw;
      if (x + numPixels > xsize) {
        numPixels = Math.max(0, xsize - x);
      }
      if (bitsPerPixel === 8) {
        for (let i = 0; i < numPixels; i++) dataBuf.push(dataByte);
      } else {
        // RLE4: alternating high/low nibble
        for (let i = 0; i < numPixels; i++) {
          dataBuf.push(i % 2 === 0 ? dataByte >> 4 : dataByte & 0xf);
        }
      }
      x += numPixels;
    } else {
      if (dataByte === 0) {
        // EOL: pad with zeros to next row boundary (Pillow behavior)
        while (dataBuf.length % xsize !== 0) dataBuf.push(0);
        x = 0;
      } else if (dataByte === 1) {
        // End of bitmap
        break outer;
      } else if (dataByte === 2) {
        // Delta: Pillow reads 4 bytes (first 2 discarded, second 2 are dx/dy)
        if (pos + 3 >= end) break;
        pos += 2; // skip first 2 bytes (Pillow bug we must match)
        const right = bytes[pos++]! & 0xff;
        const up = bytes[pos++]! & 0xff;
        const zeros = right + up * xsize;
        for (let i = 0; i < zeros; i++) dataBuf.push(0);
        x = dataBuf.length % xsize;
      } else {
        // Absolute mode: dataByte >= 3 pixels follow
        const numAbs = dataByte;
        let byteCount: number;
        if (bitsPerPixel === 8) {
          byteCount = numAbs;
        } else {
          // RLE4: Pillow uses floor division (numAbs // 2), NOT ceil
          byteCount = Math.trunc(numAbs / 2);
        }
        if (pos + byteCount > end) break;
        if (bitsPerPixel === 8) {
          for (let i = 0; i < byteCount; i++) {
            dataBuf.push(bytes[pos + i]! & 0xff);
          }
        } else {
          // RLE4: emit both nibbles of each byte read
          for (let i = 0; i < byteCount; i++) {
            const b = bytes[pos + i]! & 0xff;
            dataBuf.push(b >> 4);
            dataBuf.push(b & 0xf);
          }
        }
        x += numAbs;
        pos += byteCount;
        // Word-align: check if position relative to pixelDataOffset is odd
        if ((pos - hdr.pixelDataOffset) % 2 !== 0) {
          pos++; // skip padding byte
        }
      }
    }
  }

  // Detect RLE overrun
  if (dataBuf.length < xsize * ysize) {
    throw new DecodeError(
      "corruptInput",
      "bmp",
      `RLE stream ended with ${dataBuf.length} pixels, expected ${xsize * ysize}`
    );
  }

  // Build output pixels
  // Pillow's set_as_raw with direction=-1 reverses rows for bottom-up
  const pixels = new Uint8Array(xsize * ysize * 3);
  for (let bufRow = 0; bufRow < ysize; bufRow++) {
    const imgRow = hdr.topDown ? bufRow : ysize - 1 - bufRow;
    for (let col = 0; col < xsize; col++) {
      let palIdx = dataBuf[bufRow * xsize + col]!;
      if (palIdx >= entryCount) palIdx = entryCount - 1;
      const rgb = palette[palIdx]!;
      const dstIdx = (imgRow * xsize + col) * 3;
      pixels[dstIdx] = rgb[0]!;
      pixels[dstIdx + 1] = rgb[1]!;
      pixels[dstIdx + 2] = rgb[2]!;
    }
  }

  return { width: xsize, height: ysize, data: pixels, channels: 3, format: "bmp" };
}
