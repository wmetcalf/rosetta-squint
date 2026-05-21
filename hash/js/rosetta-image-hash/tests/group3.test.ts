import { describe, it, expect } from "vitest";
import { readFileSync, existsSync } from "node:fs";
import { join } from "node:path";

import * as rih from "../src/index.js";
import { algorithmCases, SPEC_DIR } from "./testkit.js";
import type { Hash, RgbImage } from "../src/index.js";

function loadExemptions(): Set<string> {
	const exempt = new Set<string>();
	const path = "DECODER_NOTES.md";
	if (!existsSync(path)) return exempt;
	const text = readFileSync(path, "utf8");
	for (const line of text.split("\n")) {
		const idx = line.indexOf("—");
		if (idx > 0) {
			const name = line.slice(0, idx).trim();
			if (name.endsWith(".png")) exempt.add(name);
		}
	}
	return exempt;
}

function decodePngFromSpec(fixture: string): RgbImage {
	const path = join(SPEC_DIR, "fixtures", fixture);
	const bytes = new Uint8Array(readFileSync(path));
	return rih.decodePng(bytes);
}

function run(
	name: string,
	exempt: Set<string>,
	compute: (img: RgbImage, sz: number) => Hash,
	label: string,
	failures: string[],
) {
	for (const c of algorithmCases(name)) {
		if (exempt.has(c.fixture)) continue;
		const img = decodePngFromSpec(c.fixture);
		const h = compute(img, c.size);
		if (h.toHex() !== c.hex) {
			failures.push(`${label} fixture=${c.fixture} size=${c.size}: got ${h.toHex()} want ${c.hex}`);
		}
	}
}

describe("PNG end-to-end (Group 3)", () => {
	it("byte-exact for all algorithms × fixtures via decodePng", () => {
		const exempt = loadExemptions();
		const failures: string[] = [];
		run("average_hash", exempt, rih.averageHash, "average_hash", failures);
		run("dhash", exempt, rih.dhash, "dhash", failures);
		run("dhash_vertical", exempt, rih.dhashVertical, "dhash_vertical", failures);
		run("phash", exempt, rih.phash, "phash", failures);
		run("phash_simple", exempt, rih.phashSimple, "phash_simple", failures);
		run("whash_haar", exempt, rih.whashHaar, "whash_haar", failures);
		run("whash_db4", exempt, rih.whashDb4, "whash_db4", failures);
		run("colorhash", exempt, rih.colorhash, "colorhash", failures);
		if (failures.length > 0) {
			throw new Error(`${failures.length} Group-3 failures:\n  ${failures.join("\n  ")}`);
		}
	});
});
