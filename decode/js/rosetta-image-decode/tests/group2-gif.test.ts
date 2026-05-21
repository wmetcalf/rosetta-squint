import { describe, it } from "vitest";
import { decode } from "../src/index.js";
import { listValidFixtures, readFixture, readGolden } from "./testkit.js";

describe("Group 2 — byte-exact goldens (GIF)", () => {
  it("matches all GIF fixtures byte-exact", () => {
    const fixtures = listValidFixtures("gif");
    if (fixtures.length === 0) throw new Error("no GIF fixtures");
    const failures: string[] = [];
    for (const rel of fixtures) {
      const input = readFixture(rel);
      try {
        const got = decode(input);
        const want = readGolden(rel);
        if (got.width !== want.width || got.height !== want.height || got.channels !== want.channels) {
          failures.push(`${rel}: shape ${got.width}x${got.height}c${got.channels} != ${want.width}x${want.height}c${want.channels}`);
          continue;
        }
        if (got.data.length !== want.pixels.length) {
          failures.push(`${rel}: pixel byte count ${got.data.length} != ${want.pixels.length}`);
          continue;
        }
        for (let i = 0; i < got.data.length; i++) {
          if (got.data[i] !== want.pixels[i]) {
            failures.push(`${rel}: pixel byte ${i} got=${got.data[i]} want=${want.pixels[i]}`);
            break;
          }
        }
      } catch (e: any) {
        failures.push(`${rel}: threw ${e.kind ?? e.constructor.name}: ${e.detail ?? e.message}`);
      }
    }
    if (failures.length > 0) {
      throw new Error(`${failures.length} GIF failures:\n  ${failures.join("\n  ")}`);
    }
  });
});
