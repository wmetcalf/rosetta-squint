import { describe, it, expect } from "vitest";
import { decode } from "../src/index.js";

/**
 * D-M1: BMP biClrUsed must be clamped to bit-depth max so an attacker-controlled
 * value (e.g. 0x10000000 = 256M entries) cannot cause GB-scale palette allocation.
 * Without the clamp, entryCount*4 would overflow signed-32 arithmetic, the
 * truncation check would silently pass, and the palette array allocation would
 * request a huge amount of memory.
 */

function buildPal8BmpWithClrUsed(clrUsed: number): Uint8Array {
  const width = 2;
  const height = 2;
  const paletteBytes = 256 * 4; // actual on-disk palette: 1024 bytes
  const rowStride = ((width + 3) >>> 2) * 4; // = 4
  const pixelDataSize = rowStride * height; // = 8
  const pixelDataOffset = 14 + 40 + paletteBytes; // = 1078
  const fileSize = pixelDataOffset + pixelDataSize;

  const buf = new Uint8Array(fileSize);
  const dv = new DataView(buf.buffer);
  // BMP file header (14 bytes)
  buf[0] = 0x42; // 'B'
  buf[1] = 0x4d; // 'M'
  dv.setUint32(2, fileSize, true);
  // reserved fields zero
  dv.setUint32(10, pixelDataOffset, true);
  // DIB BITMAPINFOHEADER (40 bytes)
  dv.setUint32(14, 40, true); // biSize
  dv.setInt32(18, width, true);
  dv.setInt32(22, height, true);
  dv.setUint16(26, 1, true); // planes
  dv.setUint16(28, 8, true); // bitCount
  dv.setUint32(30, 0, true); // compression BI_RGB
  dv.setUint32(34, pixelDataSize, true); // biSizeImage
  dv.setUint32(38, 2835, true); // x ppm
  dv.setUint32(42, 2835, true); // y ppm
  dv.setUint32(46, clrUsed, true); // ATTACKER-CONTROLLED
  dv.setUint32(50, 0, true); // biClrImportant
  // Palette: 256 entries of (B, G, R, reserved) starting at offset 54
  for (let i = 0; i < 256; i++) {
    const off = 54 + i * 4;
    buf[off] = i;
    buf[off + 1] = i;
    buf[off + 2] = i;
    buf[off + 3] = 0;
  }
  // Pixel data starts at pixelDataOffset = 1078
  buf[pixelDataOffset + 0] = 0;
  buf[pixelDataOffset + 1] = 1;
  buf[pixelDataOffset + 4] = 2;
  buf[pixelDataOffset + 5] = 3;
  return buf;
}

describe("Group 1 — D-M1 BMP biClrUsed clamping", () => {
  it("clamps biClrUsed=0x10000000 (256M-entry bomb) to 256 entries and decodes", async () => {
    const bytes = buildPal8BmpWithClrUsed(0x10000000);
    const img = await decode(bytes);
    expect(img.width).toBe(2);
    expect(img.height).toBe(2);
    // 2x2 RGB = 12 bytes — confirms no excessive allocation occurred.
    expect(img.data.length).toBe(12);
  });

  it("clamps biClrUsed=257 (just over 8-bit max) to 256 entries and decodes", async () => {
    const bytes = buildPal8BmpWithClrUsed(257);
    const img = await decode(bytes);
    expect(img.width).toBe(2);
    expect(img.height).toBe(2);
    expect(img.data.length).toBe(12);
  });

  it("decodes with biClrUsed=0 (legacy default behavior)", async () => {
    const bytes = buildPal8BmpWithClrUsed(0);
    const img = await decode(bytes);
    expect(img.width).toBe(2);
    expect(img.height).toBe(2);
  });

  it("decodes with biClrUsed=100 (under-max pass-through)", async () => {
    const bytes = buildPal8BmpWithClrUsed(100);
    const img = await decode(bytes);
    expect(img.width).toBe(2);
    expect(img.height).toBe(2);
  });
});
