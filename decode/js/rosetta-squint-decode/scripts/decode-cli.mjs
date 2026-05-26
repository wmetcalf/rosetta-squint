#!/usr/bin/env node
// decode-cli — decode an image and emit raw bytes (spec/SPEC.md §2 wire
// format: 12-byte header + row-major pixels) to stdout.
//
// Used by tools/cross-port-diff/diff_all.py for live cross-port equivalence.
//
// Usage: node decode-cli.mjs <fixture.path>
// Exit:  0 on success, 1 on decode error, 2 on harness error.

import { readFileSync } from "node:fs";
import { decode, DecodeError } from "../dist/index.js";

const args = process.argv.slice(2);
if (args.length !== 1) {
    process.stderr.write("usage: decode-cli <fixture>\n");
    process.exit(2);
}

let bytes;
try {
    bytes = new Uint8Array(readFileSync(args[0]));
} catch (e) {
    process.stderr.write(`read ${args[0]}: ${e.message}\n`);
    process.exit(2);
}

try {
    const img = await decode(bytes);
    const channels = img.channels === 4 ? 4 : 3;
    const hdr = new Uint8Array(12);
    new DataView(hdr.buffer).setUint32(0, img.width, true);
    new DataView(hdr.buffer).setUint32(4, img.height, true);
    hdr[8] = channels;
    process.stdout.write(hdr);
    process.stdout.write(img.data);
    process.exit(0);
} catch (e) {
    if (e instanceof DecodeError) {
        process.stderr.write(`decode error: ${e.kind}: ${e.detail}\n`);
    } else {
        process.stderr.write(`error: ${e.message}\n`);
    }
    process.exit(1);
}
