import { describe, it } from "vitest";
import * as fc from "fast-check";
import { hexToHash, hexToFlathash, hexToMultiHash } from "../src/index.js";

describe("fuzz: hex parsers never panic", () => {
	it("hexToHash on arbitrary strings", () => {
		fc.assert(
			fc.property(fc.string({ minLength: 0, maxLength: 256 }), (s) => {
				try { hexToHash(s); }
				catch (e) { if (!(e instanceof Error)) throw new Error("non-Error throw"); }
			}),
			{ numRuns: 10000 },
		);
	});

	it("hexToFlathash on (size, string) pairs", () => {
		fc.assert(
			fc.property(
				fc.integer({ min: 0, max: 255 }),
				fc.string({ minLength: 0, maxLength: 256 }),
				(size, s) => {
					try { hexToFlathash(s, size); }
					catch (e) { if (!(e instanceof Error)) throw new Error("non-Error throw"); }
				},
			),
			{ numRuns: 10000 },
		);
	});

	it("hexToMultiHash on arbitrary strings (including comma-laden)", () => {
		fc.assert(
			fc.property(
				fc.oneof(
					fc.string({ minLength: 0, maxLength: 256 }),
					fc.array(fc.string({ minLength: 0, maxLength: 64 }), { minLength: 0, maxLength: 10 })
						.map((parts) => parts.join(",")),
				),
				(s) => {
					try { hexToMultiHash(s); }
					catch (e) { if (!(e instanceof Error)) throw new Error("non-Error throw"); }
				},
			),
			{ numRuns: 10000 },
		);
	});
});
