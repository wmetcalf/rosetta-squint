import { describe, it } from "vitest";
import { decode, DecodeError } from "../src/index.js";
import { readErrors, readFixture } from "./testkit.js";

describe("Group 4 — error semantics (BMP)", () => {
  it("all invalid BMP fixtures throw correct kind + detail", async () => {
    const errors = readErrors();
    const failures: string[] = [];
    for (const [key, expected] of Object.entries(errors)) {
      if (!key.startsWith("bmp/")) continue;
      const input = readFixture(key);
      try {
        await decode(input);
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

describe("Group 4 — error semantics (PNG)", () => {
  it("all invalid PNG fixtures throw correct kind + detail", async () => {
    const errors = readErrors();
    const failures: string[] = [];
    for (const [key, expected] of Object.entries(errors)) {
      if (!key.startsWith("png/")) continue;
      const input = readFixture(key);
      try {
        await decode(input);
        failures.push(`${key}: decode succeeded, expected ${expected.expected_kind}`);
      } catch (e: any) {
        if (!(e instanceof DecodeError)) {
          failures.push(`${key}: unexpected error type ${e.constructor.name}: ${e.message}`);
          continue;
        }
        if (e.kind !== expected.expected_kind) {
          failures.push(`${key}: kind ${e.kind} != ${expected.expected_kind}`);
          continue;
        }
        if (expected.expected_detail_substring && !e.detail.includes(expected.expected_detail_substring)) {
          failures.push(`${key}: detail '${e.detail}' does not contain '${expected.expected_detail_substring}'`);
        }
      }
    }
    if (failures.length > 0) {
      throw new Error(`${failures.length} Group-4 PNG failures:\n  ${failures.join("\n  ")}`);
    }
  });
});

describe("Group 4 — error semantics (GIF)", () => {
  it("all invalid GIF fixtures throw correct kind + detail", async () => {
    const errors = readErrors();
    const failures: string[] = [];
    for (const [key, expected] of Object.entries(errors)) {
      if (!key.startsWith("gif/")) continue;
      const input = readFixture(key);
      try {
        await decode(input);
        failures.push(`${key}: decode succeeded, expected ${expected.expected_kind}`);
      } catch (e: any) {
        if (!(e instanceof DecodeError)) {
          failures.push(`${key}: unexpected error type ${e.constructor.name}: ${e.message}`);
          continue;
        }
        if (e.kind !== expected.expected_kind) {
          failures.push(`${key}: kind ${e.kind} != ${expected.expected_kind}`);
          continue;
        }
        if (expected.expected_detail_substring && !e.detail.includes(expected.expected_detail_substring)) {
          failures.push(`${key}: detail '${e.detail}' does not contain '${expected.expected_detail_substring}'`);
        }
      }
    }
    if (failures.length > 0) {
      throw new Error(`${failures.length} Group-4 GIF failures:\n  ${failures.join("\n  ")}`);
    }
  });
});

describe("Group 4 — error semantics (JPEG)", () => {
  it("all invalid JPEG fixtures throw correct kind + detail", async () => {
    const errors = readErrors();
    const failures: string[] = [];
    for (const [key, expected] of Object.entries(errors)) {
      if (!key.startsWith("jpeg/")) continue;
      const input = readFixture(key);
      try {
        await decode(input);
        failures.push(`${key}: decode succeeded, expected ${expected.expected_kind}`);
      } catch (e: any) {
        if (!(e instanceof DecodeError)) {
          failures.push(`${key}: unexpected error type ${e.constructor.name}: ${e.message}`);
          continue;
        }
        if (e.kind !== expected.expected_kind) {
          failures.push(`${key}: kind ${e.kind} != ${expected.expected_kind}`);
          continue;
        }
        if (expected.expected_detail_substring && !e.detail.includes(expected.expected_detail_substring)) {
          failures.push(`${key}: detail '${e.detail}' does not contain '${expected.expected_detail_substring}'`);
        }
      }
    }
    if (failures.length > 0) {
      throw new Error(`${failures.length} Group-4 JPEG failures:\n  ${failures.join("\n  ")}`);
    }
  });
});

describe("Group 4 — error semantics (WebP)", () => {
  it("all invalid WebP fixtures throw correct kind + detail", async () => {
    const errors = readErrors();
    const failures: string[] = [];
    for (const [key, expected] of Object.entries(errors)) {
      if (!key.startsWith("webp/")) continue;
      const input = readFixture(key);
      try {
        await decode(input);
        failures.push(`${key}: decode succeeded, expected ${expected.expected_kind}`);
      } catch (e: any) {
        if (!(e instanceof DecodeError)) {
          failures.push(`${key}: unexpected error type ${e.constructor.name}: ${e.message}`);
          continue;
        }
        if (e.kind !== expected.expected_kind) {
          failures.push(`${key}: kind ${e.kind} != ${expected.expected_kind}`);
          continue;
        }
        if (expected.expected_detail_substring && !e.detail.includes(expected.expected_detail_substring)) {
          failures.push(`${key}: detail '${e.detail}' does not contain '${expected.expected_detail_substring}'`);
        }
      }
    }
    if (failures.length > 0) {
      throw new Error(`${failures.length} Group-4 WebP failures:\n  ${failures.join("\n  ")}`);
    }
  });
});

describe("Group 4 — error semantics (TIFF)", () => {
  it("all invalid TIFF fixtures throw correct kind + detail", async () => {
    const errors = readErrors();
    const failures: string[] = [];
    for (const [key, expected] of Object.entries(errors)) {
      if (!key.startsWith("tiff/")) continue;
      const input = readFixture(key);
      try {
        await decode(input);
        failures.push(`${key}: decode succeeded, expected ${expected.expected_kind}`);
      } catch (e: any) {
        if (!(e instanceof DecodeError)) {
          failures.push(`${key}: unexpected error type ${e.constructor.name}: ${e.message}`);
          continue;
        }
        if (e.kind !== expected.expected_kind) {
          failures.push(`${key}: kind ${e.kind} != ${expected.expected_kind}`);
          continue;
        }
        if (expected.expected_detail_substring && !e.detail.includes(expected.expected_detail_substring)) {
          failures.push(`${key}: detail '${e.detail}' does not contain '${expected.expected_detail_substring}'`);
        }
      }
    }
    if (failures.length > 0) {
      throw new Error(`${failures.length} Group-4 TIFF failures:\n  ${failures.join("\n  ")}`);
    }
  });
});

describe("Group 4 — error semantics (HEIC)", () => {
  it("all invalid HEIC fixtures throw correct kind + detail", async () => {
    const errors = readErrors();
    const failures: string[] = [];
    for (const [key, expected] of Object.entries(errors)) {
      if (!key.startsWith("heic/")) continue;
      const input = readFixture(key);
      try {
        await decode(input);
        failures.push(`${key}: decode succeeded, expected ${expected.expected_kind}`);
      } catch (e: any) {
        if (!(e instanceof DecodeError)) {
          failures.push(`${key}: unexpected error type ${e.constructor.name}: ${e.message}`);
          continue;
        }
        if (e.kind !== expected.expected_kind) {
          failures.push(`${key}: kind ${e.kind} != ${expected.expected_kind}`);
          continue;
        }
        if (expected.expected_detail_substring && !e.detail.includes(expected.expected_detail_substring)) {
          failures.push(`${key}: detail '${e.detail}' does not contain '${expected.expected_detail_substring}'`);
        }
      }
    }
    if (failures.length > 0) {
      throw new Error(`${failures.length} Group-4 HEIC failures:\n  ${failures.join("\n  ")}`);
    }
  });
});
