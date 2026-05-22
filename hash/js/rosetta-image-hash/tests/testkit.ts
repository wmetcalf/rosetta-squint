import { readFileSync } from "node:fs";
import { join } from "node:path";

import type { RgbImage } from "../src/hash.js";

export const SPEC_DIR = join("..", "..", "spec");

export interface AlgorithmCase {
  fixture: string;
  size: number;
  hex: string;
}

interface GoldensJson {
  algorithms: Record<
    string,
    { fixtures: Record<string, Record<string, string | null>> }
  >;
}

let goldensCache: GoldensJson | undefined;

function loadGoldens(): GoldensJson {
  if (!goldensCache) {
    const path = join(SPEC_DIR, "goldens.json");
    goldensCache = JSON.parse(readFileSync(path, "utf8")) as GoldensJson;
  }
  return goldensCache;
}

/** Returns (fixture, size, expectedHex) triples for the algorithm, skipping null hex (small fixtures for whash). */
export function algorithmCases(algorithm: string): AlgorithmCase[] {
  const g = loadGoldens();
  const entry = g.algorithms[algorithm];
  if (!entry) throw new Error(`algorithm ${algorithm} not in goldens.json`);
  const out: AlgorithmCase[] = [];
  const fixtureNames = Object.keys(entry.fixtures).sort();
  for (const fixture of fixtureNames) {
    const sizes = entry.fixtures[fixture];
    const sizeKeys = Object.keys(sizes).sort();
    for (const sizeStr of sizeKeys) {
      const hex = sizes[sizeStr];
      if (hex === null) continue;
      out.push({ fixture, size: Number.parseInt(sizeStr, 10), hex });
    }
  }
  return out;
}

/** Reads spec/decoded/<name>.rgb.bin into an RgbImage with channels=3. */
export function loadPredecoded(name: string): RgbImage {
  const path = join(SPEC_DIR, "decoded", `${name}.rgb.bin`);
  const buf = readFileSync(path);
  if (buf.length < 8) throw new Error(`decoded ${name} too short`);
  const view = new DataView(buf.buffer, buf.byteOffset, buf.byteLength);
  const width = view.getUint32(0, true);
  const height = view.getUint32(4, true);
  const expected = 8 + width * height * 3;
  if (buf.length !== expected) {
    throw new Error(`decoded ${name} length mismatch: got ${buf.length}, expected ${expected}`);
  }
  const data = new Uint8Array(buf.buffer, buf.byteOffset + 8, width * height * 3);
  // Copy to detach from the Buffer's underlying allocation
  return { width, height, data: new Uint8Array(data), channels: 3 };
}

export interface LanczosCase {
  srcW: number;
  srcH: number;
  dstW: number;
  dstH: number;
  src: Uint8Array;
  dst: Uint8Array;
}

/** Reads spec/lanczos_cases/<name>.bin into typed buffers. */
export function loadLanczosCase(name: string): LanczosCase {
  const path = join(SPEC_DIR, "lanczos_cases", `${name}.bin`);
  const buf = readFileSync(path);
  if (buf.length < 16) throw new Error(`lanczos case ${name} too short`);
  const view = new DataView(buf.buffer, buf.byteOffset, buf.byteLength);
  const srcW = view.getUint32(0, true);
  const srcH = view.getUint32(4, true);
  const dstW = view.getUint32(8, true);
  const dstH = view.getUint32(12, true);
  if (buf.length !== 16 + srcW * srcH + dstW * dstH) {
    throw new Error(`lanczos ${name} length mismatch`);
  }
  const src = new Uint8Array(buf.subarray(16, 16 + srcW * srcH));
  const dst = new Uint8Array(buf.subarray(16 + srcW * srcH));
  return { srcW, srcH, dstW, dstH, src, dst };
}
