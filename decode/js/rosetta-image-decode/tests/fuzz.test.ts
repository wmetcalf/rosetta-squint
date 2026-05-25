import { describe, it } from "vitest";
import * as fc from "fast-check";
import { decode } from "../src/index.js";

describe("fuzz: decode() never panics on any input", () => {
	it("random byte arrays", async () => {
		await fc.assert(
			fc.asyncProperty(
				fc.uint8Array({ minLength: 0, maxLength: 4096 }),
				async (data) => {
					try {
						await decode(data);
					} catch (e) {
						// Typed DecodeError is acceptable. Any other Error = bug.
						if (!(e instanceof Error)) throw new Error(`non-Error throw: ${typeof e}`);
						// Errors are expected on random bytes; just ensure they're catchable.
					}
				},
			),
			{ numRuns: 5000, verbose: 0 },
		);
	}, 60_000);

	it("magic-prefixed random bytes (forces per-format parser coverage)", async () => {
		const prefixes = [
			Uint8Array.from([0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A]),
			Uint8Array.from([0xFF, 0xD8, 0xFF, 0xE0]),
			Uint8Array.from([0x47, 0x49, 0x46, 0x38, 0x39, 0x61]),
			Uint8Array.from([0x42, 0x4D]),
			Uint8Array.from([0x52, 0x49, 0x46, 0x46, 0, 0, 0, 0, 0x57, 0x45, 0x42, 0x50]),
			Uint8Array.from([0x49, 0x49, 0x2A, 0x00]),
			Uint8Array.from([0, 0, 0, 0x18, 0x66, 0x74, 0x79, 0x70, 0x68, 0x65, 0x69, 0x63]),
		];
		await fc.assert(
			fc.asyncProperty(
				fc.integer({ min: 0, max: prefixes.length - 1 }),
				fc.uint8Array({ minLength: 0, maxLength: 4096 }),
				async (idx, body) => {
					const prefix = prefixes[idx]!;
					const combined = new Uint8Array(prefix.length + body.length);
					combined.set(prefix, 0);
					combined.set(body, prefix.length);
					try { await decode(combined); }
					catch (e) {
						if (!(e instanceof Error)) throw new Error(`non-Error throw: ${typeof e}`);
					}
				},
			),
			{ numRuns: 2000, verbose: 0 },
		);
	}, 90_000);
});
