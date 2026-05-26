import { describe, it, expect } from "vitest";
import { Hash, ImageHashError } from "../src/hash.js";
import { ImageMultiHash } from "../src/multiHash.js";

describe("Hash semantics (Group 5)", () => {
  it("Hamming distance is zero for equal hashes", () => {
    const a = new Hash([[true, false], [true, true]]);
    const b = new Hash([[true, false], [true, true]]);
    expect(a.subtract(b)).toBe(0);
  });

  it("Hamming distance counts differing bits", () => {
    const a = new Hash([[true, false], [true, true]]);
    const b = new Hash([[false, false], [true, false]]);
    expect(a.subtract(b)).toBe(2);
  });

  it("bitCount is height * width", () => {
    const bits: boolean[][] = [];
    for (let i = 0; i < 8; i++) bits.push(new Array(8).fill(false));
    expect(new Hash(bits).bitCount()).toBe(64);
  });

  it("equals is value-based", () => {
    const a = new Hash([[true, false]]);
    const b = new Hash([[true, false]]);
    const c = new Hash([[false, false]]);
    expect(a.equals(b)).toBe(true);
    expect(a.equals(c)).toBe(false);
  });

  it("toHex format", () => {
    const bits: boolean[][] = [];
    for (let i = 0; i < 8; i++) bits.push(new Array(8).fill(true));
    expect(new Hash(bits).toHex()).toBe("ffffffffffffffff");
  });

  it("subtract requires matching shape", () => {
    const a = new Hash([[true, false]]);
    const b = new Hash([[true, false], [true, false]]);
    expect(() => a.subtract(b)).toThrow(ImageHashError);
    try {
      a.subtract(b);
    } catch (e) {
      expect(e).toBeInstanceOf(ImageHashError);
      expect((e as ImageHashError).kind).toBe("ShapeMismatch");
    }
  });

  it("Hash rejects empty bits", () => {
    expect(() => new Hash([])).toThrow(ImageHashError);
    expect(() => new Hash([[]])).toThrow(ImageHashError);
  });

  it("Hash rejects non-rectangular bits", () => {
    expect(() => new Hash([[true, false], [true]])).toThrow(ImageHashError);
  });
});

import { averageHash, colorhash, dhash, dhashVertical, phash, phashSimple, whashHaar, whashDb4, hexToHash, hexToFlathash } from "../src/index.js";
import type { RgbImage } from "../src/hash.js";

function tinyImage(): RgbImage {
  return { width: 8, height: 8, data: new Uint8Array(8 * 8 * 3), channels: 3 };
}

function smallImage(): RgbImage {
  const data = new Uint8Array(32 * 32 * 3);
  for (let i = 0; i < 32 * 32; i++) {
    data[i * 3] = 128;
    data[i * 3 + 1] = 64;
    data[i * 3 + 2] = 192;
  }
  return { width: 32, height: 32, data, channels: 3 };
}

describe("error semantics (Group 5)", () => {
  it("averageHash rejects hashSize < 2", () => {
    expect(() => averageHash(tinyImage(), 1)).toThrow(ImageHashError);
    expect(() => averageHash(tinyImage(), 0)).toThrow(ImageHashError);
  });

  it("dhash rejects hashSize < 2", () => {
    expect(() => dhash(tinyImage(), 1)).toThrow(ImageHashError);
  });

  it("dhashVertical rejects hashSize < 2", () => {
    expect(() => dhashVertical(tinyImage(), 1)).toThrow(ImageHashError);
  });

  it("phash rejects hashSize < 2", () => {
    expect(() => phash(tinyImage(), 1)).toThrow(ImageHashError);
  });

  it("phashSimple rejects hashSize < 2", () => {
    expect(() => phashSimple(tinyImage(), 1)).toThrow(ImageHashError);
  });

  it("whashHaar rejects hashSize < 2", () => {
    expect(() => whashHaar(smallImage(), 1)).toThrow(ImageHashError);
  });

  it("whashDb4 rejects hashSize < 2", () => {
    expect(() => whashDb4(smallImage(), 1)).toThrow(ImageHashError);
  });

  it("whashDb4 rejects non-power-of-two", () => {
    expect(() => whashDb4(smallImage(), 3)).toThrow(ImageHashError);
    try {
      whashDb4(smallImage(), 3);
    } catch (e) {
      expect((e as ImageHashError).kind).toBe("NotPowerOfTwo");
    }
  });

  it("whashHaar rejects non-power-of-two", () => {
    expect(() => whashHaar(smallImage(), 3)).toThrow(ImageHashError);
    try {
      whashHaar(smallImage(), 3);
    } catch (e) {
      expect((e as ImageHashError).kind).toBe("NotPowerOfTwo");
    }
    expect(() => whashHaar(smallImage(), 5)).toThrow(ImageHashError);
  });

  it("colorhash rejects binbits < 1", () => {
    expect(() => colorhash(tinyImage(), 0)).toThrow(ImageHashError);
  });

  it("colorhash rejects non-integer binbits (H-L1)", () => {
    expect(() => colorhash(tinyImage(), 3.5)).toThrow(ImageHashError);
    try {
      colorhash(tinyImage(), 3.5);
    } catch (e) {
      expect((e as ImageHashError).kind).toBe("InvalidBinbits");
    }
  });

  it("colorhash rejects binbits > 30 (H-L2)", () => {
    expect(() => colorhash(tinyImage(), 31)).toThrow(ImageHashError);
    try {
      colorhash(tinyImage(), 31);
    } catch (e) {
      expect((e as ImageHashError).kind).toBe("InvalidBinbits");
    }
  });

  it("averageHash rejects non-integer hashSize (H-L1)", () => {
    expect(() => averageHash(tinyImage(), 8.5)).toThrow(ImageHashError);
  });

  it("phash rejects non-integer hashSize (H-L1)", () => {
    expect(() => phash(tinyImage(), 8.5)).toThrow(ImageHashError);
    try {
      phash(tinyImage(), 8.5);
    } catch (e) {
      expect((e as ImageHashError).kind).toBe("InvalidHashSize");
    }
  });

  it("phashSimple rejects non-integer hashSize (H-L1)", () => {
    expect(() => phashSimple(tinyImage(), 8.5)).toThrow(ImageHashError);
  });

  it("dhash rejects non-integer hashSize (H-L1)", () => {
    expect(() => dhash(tinyImage(), 8.5)).toThrow(ImageHashError);
  });

  it("dhashVertical rejects non-integer hashSize (H-L1)", () => {
    expect(() => dhashVertical(tinyImage(), 8.5)).toThrow(ImageHashError);
  });

  it("whashHaar rejects non-integer hashSize (H-L1)", () => {
    expect(() => whashHaar(smallImage(), 8.5)).toThrow(ImageHashError);
  });

  it("whashDb4 rejects non-integer hashSize (H-L1)", () => {
    expect(() => whashDb4(smallImage(), 8.5)).toThrow(ImageHashError);
  });

  it("hexToHash rejects non-square", () => {
    expect(() => hexToHash("12345")).toThrow(ImageHashError);
  });

  it("hexToHash rejects invalid chars", () => {
    expect(() => hexToHash("xyz!")).toThrow(ImageHashError);
  });

  it("hexToFlathash rejects hashSize < 1", () => {
    expect(() => hexToFlathash("00", 0)).toThrow(ImageHashError);
  });
});

import { cropResistantHash, whashDb4Robust } from "../src/index.js";

describe("RgbImage buffer validation (Group 5)", () => {
  function makeBadImg(width: number, height: number, dataLen: number, channels: 3 | 4): RgbImage {
    return { width, height, data: new Uint8Array(dataLen), channels };
  }

  it("averageHash rejects mismatched data length", () => {
    // expects 8*8*3 = 192 bytes; pass only 100
    const img = makeBadImg(8, 8, 100, 3);
    expect(() => averageHash(img, 8)).toThrow(ImageHashError);
    try {
      averageHash(img, 8);
    } catch (e) {
      expect((e as ImageHashError).kind).toBe("ShapeMismatch");
    }
  });

  it("phash rejects mismatched data length", () => {
    const img = makeBadImg(8, 8, 100, 3);
    expect(() => phash(img, 8)).toThrow(ImageHashError);
  });

  it("phashSimple rejects mismatched data length", () => {
    const img = makeBadImg(8, 8, 100, 3);
    expect(() => phashSimple(img, 8)).toThrow(ImageHashError);
  });

  it("dhash rejects mismatched data length", () => {
    const img = makeBadImg(8, 8, 100, 3);
    expect(() => dhash(img, 8)).toThrow(ImageHashError);
  });

  it("dhashVertical rejects mismatched data length", () => {
    const img = makeBadImg(8, 8, 100, 3);
    expect(() => dhashVertical(img, 8)).toThrow(ImageHashError);
  });

  it("whashHaar rejects mismatched data length", () => {
    const img = makeBadImg(32, 32, 100, 3);
    expect(() => whashHaar(img, 8)).toThrow(ImageHashError);
  });

  it("whashDb4 rejects mismatched data length", () => {
    const img = makeBadImg(32, 32, 100, 3);
    expect(() => whashDb4(img, 8)).toThrow(ImageHashError);
  });

  it("whashDb4Robust rejects mismatched data length", () => {
    const img = makeBadImg(32, 32, 100, 3);
    expect(() => whashDb4Robust(img, 8)).toThrow(ImageHashError);
  });

  it("colorhash rejects mismatched data length", () => {
    const img = makeBadImg(8, 8, 100, 3);
    expect(() => colorhash(img, 3)).toThrow(ImageHashError);
  });

  it("cropResistantHash rejects mismatched data length", () => {
    const img = makeBadImg(32, 32, 100, 3);
    expect(() => cropResistantHash(img)).toThrow(ImageHashError);
  });

  it("accepts RGBA buffer with correct length", () => {
    const img = { width: 8, height: 8, data: new Uint8Array(8 * 8 * 4), channels: 4 as const };
    expect(() => averageHash(img, 8)).not.toThrow();
  });

  it("rejects RGBA with RGB-sized buffer", () => {
    const img = { width: 8, height: 8, data: new Uint8Array(8 * 8 * 3), channels: 4 as const };
    expect(() => averageHash(img, 8)).toThrow(ImageHashError);
    try {
      averageHash(img, 8);
    } catch (e) {
      expect((e as ImageHashError).kind).toBe("ShapeMismatch");
    }
  });
});

describe("ImageMultiHash construction (Group 5, H-L9)", () => {
  it("rejects empty segmentHashes", () => {
    expect(() => new ImageMultiHash([])).toThrow(ImageHashError);
    try {
      new ImageMultiHash([]);
    } catch (e) {
      expect((e as ImageHashError).kind).toBe("ShapeMismatch");
    }
  });
});
