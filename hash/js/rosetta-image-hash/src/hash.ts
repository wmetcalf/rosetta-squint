/**
 * Hash container, ImageHashError, and RgbImage interface.
 *
 * Public surface that consumers use directly. Algorithms create Hash
 * instances via `new Hash(bits)` after constructing the bit array.
 */

import { pack } from "./internal/bitpack.js";

export interface RgbImage {
  width: number;
  height: number;
  /** RGB or RGBA bytes, row-major. Length = width * height * channels. */
  data: Uint8Array;
  channels: 3 | 4;
}

export type ImageHashErrorKind =
  | "InvalidHashSize"
  | "NotPowerOfTwo"
  | "HashSizeTooLarge"
  | "InvalidBinbits"
  | "InvalidHex"
  | "ShapeMismatch"
  | "EmptyBits"
  | "NonRectangular";

export class ImageHashError extends Error {
  readonly kind: ImageHashErrorKind;
  constructor(kind: ImageHashErrorKind, message: string) {
    super(message);
    this.name = "ImageHashError";
    this.kind = kind;
  }
}

export class Hash {
  readonly #bits: boolean[][];

  constructor(bits: boolean[][]) {
    if (bits.length === 0 || bits[0].length === 0) {
      throw new ImageHashError("EmptyBits", "bits must be non-empty");
    }
    const w = bits[0].length;
    for (const row of bits) {
      if (row.length !== w) {
        throw new ImageHashError(
          "NonRectangular",
          `bits must be rectangular; got width ${row.length} vs ${w}`,
        );
      }
    }
    // Defensive copy so external mutation can't corrupt the hash
    this.#bits = bits.map(row => row.slice());
  }

  toHex(): string {
    return pack(this.#bits);
  }

  subtract(other: Hash): number {
    const h1 = this.#bits.length;
    const w1 = this.#bits[0].length;
    const o = other.#bits;
    const h2 = o.length;
    const w2 = o[0].length;
    if (h1 !== h2 || w1 !== w2) {
      throw new ImageHashError(
        "ShapeMismatch",
        `shapes don't match: this=(${h1},${w1}), other=(${h2},${w2})`,
      );
    }
    let diff = 0;
    for (let y = 0; y < h1; y++) {
      for (let x = 0; x < w1; x++) {
        if (this.#bits[y][x] !== o[y][x]) diff++;
      }
    }
    return diff;
  }

  bitCount(): number {
    if (this.#bits.length === 0) return 0;
    return this.#bits.length * this.#bits[0].length;
  }

  equals(other: Hash): boolean {
    if (this.#bits.length !== other.#bits.length) return false;
    for (let y = 0; y < this.#bits.length; y++) {
      const a = this.#bits[y];
      const b = other.#bits[y];
      if (a.length !== b.length) return false;
      for (let x = 0; x < a.length; x++) {
        if (a[x] !== b[x]) return false;
      }
    }
    return true;
  }

  toString(): string {
    return this.toHex();
  }
}
