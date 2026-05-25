/**
 * ImageMultiHash — container for crop_resistant_hash output.
 *
 * Each instance wraps a list of Hash objects (one per image segment).
 * Distance semantics differ from Hash: subtract() returns a FLOAT (not int),
 * because the matching algorithm weighs matches vs. unmatched segments.
 */

import { Hash, ImageHashError } from "./hash.js";
import { hexToHash } from "./hex.js";

export class ImageMultiHash {
  /**
   * Throws `ImageHashError("ShapeMismatch", ...)` if `segmentHashes` is empty —
   * matches Java's existing guard so all 5 ports reject empty multi-hashes at
   * construction rather than failing later inside `hashDiff` (which would
   * dereference `segmentHashes[0]`).
   */
  constructor(public readonly segmentHashes: Hash[]) {
    if (segmentHashes.length === 0) {
      throw new ImageHashError("ShapeMismatch", "ImageMultiHash requires at least one segment hash");
    }
  }

  /**
   * hash_diff: returns (matches, sumDistance) matching Python's hash_diff.
   *
   * For each segment in `this`, find the closest segment in `other` (by Hamming
   * distance). If that distance <= hammingCutoff, count it as a match.
   *
   * Default hammingCutoff = bitErrorRate * bitCount (bitErrorRate defaults to 0.25).
   */
  hashDiff(
    other: ImageMultiHash,
    hammingCutoff?: number,
    bitErrorRate?: number,
  ): { matches: number; sumDistance: number } {
    // Resolve cutoff
    let cutoff = hammingCutoff;
    if (cutoff === undefined) {
      const ber = bitErrorRate !== undefined ? bitErrorRate : 0.25;
      cutoff = this.segmentHashes[0].bitCount() * ber;
    }

    let matches = 0;
    let sumDistance = 0;

    for (const seg of this.segmentHashes) {
      let lowestDistance = Infinity;
      for (const otherSeg of other.segmentHashes) {
        const d = seg.subtract(otherSeg);
        if (d < lowestDistance) lowestDistance = d;
      }
      if (lowestDistance <= cutoff) {
        matches++;
        sumDistance += lowestDistance;
      }
    }

    return { matches, sumDistance };
  }

  /**
   * subtract: returns a FLOAT distance (matches Python's __sub__).
   *
   * Higher value = more different.  Returns maxDifference (= this.segmentHashes.length)
   * if no segments match.
   */
  subtract(
    other: ImageMultiHash,
    hammingCutoff?: number,
    bitErrorRate?: number,
  ): number {
    const { matches, sumDistance } = this.hashDiff(other, hammingCutoff, bitErrorRate);
    const maxDifference = this.segmentHashes.length;
    if (matches === 0) return maxDifference;
    const maxDistance = matches * this.segmentHashes[0].bitCount();
    const tieBreaker = 0 - sumDistance / maxDistance;
    const matchScore = matches + tieBreaker;
    return maxDifference - matchScore;
  }

  /**
   * matches: returns true if at least regionCutoff segments match.
   */
  matches(
    other: ImageMultiHash,
    regionCutoff = 1,
    hammingCutoff?: number,
    bitErrorRate?: number,
  ): boolean {
    return this.hashDiff(other, hammingCutoff, bitErrorRate).matches >= regionCutoff;
  }

  /**
   * bestMatch: returns the ImageMultiHash in `others` with the smallest subtract() distance.
   */
  bestMatch(
    others: ImageMultiHash[],
    hammingCutoff?: number,
    bitErrorRate?: number,
  ): ImageMultiHash {
    let best = others[0];
    let bestDist = this.subtract(best, hammingCutoff, bitErrorRate);
    for (let i = 1; i < others.length; i++) {
      const d = this.subtract(others[i], hammingCutoff, bitErrorRate);
      if (d < bestDist) {
        bestDist = d;
        best = others[i];
      }
    }
    return best;
  }

  /** Comma-separated hex of each segment hash, matching Python str(multihash). */
  toString(): string {
    return this.segmentHashes.map(h => h.toString()).join(",");
  }
}

/**
 * Parse a comma-separated hex string (as produced by ImageMultiHash.toString())
 * back into an ImageMultiHash.
 */
export function hexToMultiHash(s: string): ImageMultiHash {
  return new ImageMultiHash(s.split(",").map(hexToHash));
}
