import { describe, it, expect } from "vitest";
import { decode } from "../src/index.js";
import { listValidFixtures, readFixture, readGolden } from "./testkit.js";

describe("Group 2 — byte-exact goldens (BMP)", () => {
  it("matches all 30 BMP fixtures byte-exact", async () => {
    const fixtures = listValidFixtures("bmp");
    expect(fixtures.length).toBe(30);
    const failures: string[] = [];
    for (const rel of fixtures) {
      const input = readFixture(rel);
      try {
        const got = await decode(input);
        const want = readGolden(rel);
        if (
          got.width !== want.width ||
          got.height !== want.height ||
          got.channels !== want.channels
        ) {
          failures.push(
            `${rel}: shape ${got.width}x${got.height}c${got.channels} != ${want.width}x${want.height}c${want.channels}`
          );
          continue;
        }
        if (got.data.length !== want.pixels.length) {
          failures.push(
            `${rel}: pixel byte count ${got.data.length} != ${want.pixels.length}`
          );
          continue;
        }
        for (let i = 0; i < got.data.length; i++) {
          if (got.data[i] !== want.pixels[i]) {
            failures.push(
              `${rel}: pixel byte ${i} got=${got.data[i]} want=${want.pixels[i]}`
            );
            break;
          }
        }
      } catch (e: any) {
        failures.push(
          `${rel}: threw ${e.kind ?? e.constructor.name}: ${e.detail ?? e.message}`
        );
      }
    }
    if (failures.length > 0) {
      throw new Error(`${failures.length} failures:\n  ${failures.join("\n  ")}`);
    }
  });
});
