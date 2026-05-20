import { describe, it, expect } from "vitest";
import { Hash, ImageHashError } from "../src/hash.js";

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
