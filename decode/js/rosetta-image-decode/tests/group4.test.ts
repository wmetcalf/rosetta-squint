import { describe, it } from "vitest";
import { decode, DecodeError } from "../src/index.js";
import { readErrors, readFixture } from "./testkit.js";

describe("Group 4 — error semantics (BMP)", () => {
  it("all invalid BMP fixtures throw correct kind + detail", () => {
    const errors = readErrors();
    const failures: string[] = [];
    for (const [key, expected] of Object.entries(errors)) {
      if (!key.startsWith("bmp/")) continue;
      const input = readFixture(key);
      try {
        decode(input);
        failures.push(`${key}: decode succeeded, expected ${expected.expected_kind}`);
      } catch (e: any) {
        if (!(e instanceof DecodeError)) {
          failures.push(
            `${key}: unexpected error type ${e.constructor.name}: ${e.message}`
          );
          continue;
        }
        if (e.kind !== expected.expected_kind) {
          failures.push(`${key}: kind ${e.kind} != ${expected.expected_kind}`);
          continue;
        }
        if (
          expected.expected_detail_substring &&
          !e.detail.includes(expected.expected_detail_substring)
        ) {
          failures.push(
            `${key}: detail '${e.detail}' does not contain '${expected.expected_detail_substring}'`
          );
        }
      }
    }
    if (failures.length > 0) {
      throw new Error(`${failures.length} Group-4 failures:\n  ${failures.join("\n  ")}`);
    }
  });
});
