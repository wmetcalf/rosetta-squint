import { describe, it, expect } from "vitest";
import { hexToHash, hexToFlathash, ImageHashError } from "../src/index.js";

describe("hex round-trip (Group 4)", () => {
  it("hex_to_hash and back", () => {
    const hex = "ffd7918181c9ffff";
    const h = hexToHash(hex);
    expect(h.bitCount()).toBe(64);
    expect(h.toHex()).toBe(hex);
  });

  it("hex_to_flathash and back", () => {
    const hex = "0123456789abcd";
    const h = hexToFlathash(hex, 4);
    expect(h.bitCount()).toBe(14 * 4);
    expect(h.toHex()).toBe(hex);
  });

  it("hex_to_hash rejects non-square length", () => {
    expect(() => hexToHash("12345")).toThrow(ImageHashError);
  });

  it("hex_to_hash rejects invalid chars", () => {
    expect(() => hexToHash("xyz!")).toThrow(ImageHashError);
  });

  it("round-trip all zeros", () => {
    const hex = "0000000000000000";
    expect(hexToHash(hex).toHex()).toBe(hex);
  });
});
