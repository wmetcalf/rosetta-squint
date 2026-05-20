/**
 * Boolean array ↔ hex string conversion. Row-major MSB-first, zero-padded
 * to ceil(M*N / 4) chars.
 *
 * Matches Python imagehash's _binary_array_to_hex which treats the bit string
 * as a big integer (RIGHT-aligned in the hex output). For bit counts that are
 * not multiples of 4, this prepends (4 - total%4)%4 leading zero bits so the
 * result agrees with Python's format (e.g. colorhash binbits=3 → 42 bits →
 * 11 hex chars with 2 leading zero bits prepended before the first nibble).
 */

import { ImageHashError } from "../hash.js";

export function pack(bits: boolean[][]): string {
  const h = bits.length;
  if (h === 0 || bits[0].length === 0) return "";
  const w = bits[0].length;
  const total = h * w;
  const width = (total + 3) >> 2; // ceil(total/4)
  // Number of leading zero-padding bits so the bit string is RIGHT-aligned
  // within the hex representation (matches Python's big-integer formatting).
  const pad = (4 - (total & 3)) & 3; // (4 - total%4) % 4
  const paddedTotal = total + pad;
  const byteCount = (paddedTotal + 7) >> 3; // ceil(paddedTotal/8)
  const bytes = new Uint8Array(byteCount);
  let bi = pad; // start writing after the leading zero bits
  for (const row of bits) {
    for (const b of row) {
      if (b) {
        bytes[bi >> 3] |= 1 << (7 - (bi & 7));
      }
      bi++;
    }
  }
  let out = "";
  for (let i = 0; i < width; i++) {
    const bitPos = i * 4;
    const byteIdx = bitPos >> 3;
    const nibble = (bitPos & 7) === 0 ? bytes[byteIdx] >> 4 : bytes[byteIdx] & 0x0f;
    out += nibble.toString(16);
  }
  return out;
}

export function unpackSquare(hex: string): boolean[][] {
  const bits = hexToBits(hex);
  const total = bits.length;
  let n = 0;
  while (n * n < total) n++;
  if (n * n !== total) {
    throw new ImageHashError(
      "InvalidHex",
      `hex length ${hex.length} (${total} bits) is not a square shape`,
    );
  }
  const out: boolean[][] = [];
  let idx = 0;
  for (let y = 0; y < n; y++) {
    const row: boolean[] = new Array(n);
    for (let x = 0; x < n; x++) {
      row[x] = bits[idx++];
    }
    out.push(row);
  }
  return out;
}

export function unpackFlat(hex: string, secondAxis: number): boolean[][] {
  const bits = hexToBits(hex);
  const total = 14 * secondAxis;
  if (bits.length < total) {
    throw new ImageHashError(
      "InvalidHex",
      `hex too short for 14x${secondAxis} shape: ${bits.length} bits`,
    );
  }
  let idx = bits.length - total; // align MSB-first if hex is wider than needed
  const out: boolean[][] = [];
  for (let y = 0; y < 14; y++) {
    const row: boolean[] = new Array(secondAxis);
    for (let x = 0; x < secondAxis; x++) {
      row[x] = bits[idx++];
    }
    out.push(row);
  }
  return out;
}

function hexToBits(hex: string): boolean[] {
  if (hex.length === 0) {
    throw new ImageHashError("InvalidHex", "empty hex");
  }
  for (const c of hex) {
    if (!/[0-9a-f]/.test(c)) {
      throw new ImageHashError("InvalidHex", `invalid char ${JSON.stringify(c)} in ${JSON.stringify(hex)}`);
    }
  }
  const total = hex.length * 4;
  const bits: boolean[] = new Array(total);
  for (let i = 0; i < hex.length; i++) {
    const v = parseInt(hex[i], 16);
    for (let b = 0; b < 4; b++) {
      bits[i * 4 + b] = ((v >> (3 - b)) & 1) === 1;
    }
  }
  return bits;
}
