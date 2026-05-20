import { Hash, ImageHashError } from "./hash.js";
import { unpackFlat, unpackSquare } from "./internal/bitpack.js";

export function hexToHash(hex: string): Hash {
  const bits = unpackSquare(hex);
  return new Hash(bits);
}

export function hexToFlathash(hex: string, hashSize: number): Hash {
  if (hashSize < 1) {
    throw new ImageHashError("InvalidBinbits", `hashSize must be >= 1, got ${hashSize}`);
  }
  const bits = unpackFlat(hex, hashSize);
  return new Hash(bits);
}
