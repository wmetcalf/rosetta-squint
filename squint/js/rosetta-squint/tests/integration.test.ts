import { describe, it, expect } from "vitest";
import { readFile } from "node:fs/promises";
import { join } from "node:path";

import {
    phash, phashBytes,
    averageHash, averageHashBytes,
    dhash, dhashBytes,
    dhashVertical, dhashVerticalBytes,
    phashSimple, phashSimpleBytes,
    whashHaar, whashHaarBytes,
    whashDb4, whashDb4Bytes,
    colorhash, colorhashBytes,
    cropResistantHash, cropResistantHashBytes,
    decodeFile,
    hexToHash, hexToFlathash, hexToMultiHash,
    ImageMultiHash,
} from "../src/index.js";
import * as rih from "rosetta-image-hash";

const FIXTURES_PNG = join(
    new URL(".", import.meta.url).pathname,
    "../../../../decode/spec/fixtures/png/valid",
);
const FIXTURES_JPEG = join(
    new URL(".", import.meta.url).pathname,
    "../../../../decode/spec/fixtures/jpeg/valid",
);

const PNG_FILES = [
    "imagehash.png",
    "peppers.png",
    "checker-256.png",
];
const JPEG_FILES = [
    "larger-photo-128.jpg",
    "64x64-quality-50.jpg",
];

// ---------------------------------------------------------------------------
// phash
// ---------------------------------------------------------------------------
describe("phash", () => {
    for (const name of PNG_FILES) {
        const path = join(FIXTURES_PNG, name);

        it(`returns a Hash for ${name}`, async () => {
            const h = await phash(path, 8);
            expect(h).toBeInstanceOf(rih.Hash);
            expect(h.toHex().length).toBe(16); // 8*8 bits = 64 bits = 16 hex chars
        });

        it(`phash === phashBytes for ${name}`, async () => {
            const [hPath, hBytes] = await Promise.all([
                phash(path, 8),
                readFile(path).then(b => phashBytes(new Uint8Array(b), 8)),
            ]);
            expect(hPath.toHex()).toBe(hBytes.toHex());
        });

        it(`phash matches rih.phash(decodeFile) for ${name}`, async () => {
            const [hSquint, img] = await Promise.all([
                phash(path, 8),
                decodeFile(path),
            ]);
            const hRih = rih.phash(img, 8);
            expect(hSquint.toHex()).toBe(hRih.toHex());
        });
    }

    for (const name of JPEG_FILES) {
        const path = join(FIXTURES_JPEG, name);

        it(`returns a Hash for ${name}`, async () => {
            const h = await phash(path, 8);
            expect(h).toBeInstanceOf(rih.Hash);
        });

        it(`phash === phashBytes for ${name}`, async () => {
            const [hPath, hBytes] = await Promise.all([
                phash(path, 8),
                readFile(path).then(b => phashBytes(new Uint8Array(b), 8)),
            ]);
            expect(hPath.toHex()).toBe(hBytes.toHex());
        });
    }
});

// ---------------------------------------------------------------------------
// averageHash
// ---------------------------------------------------------------------------
describe("averageHash", () => {
    for (const name of PNG_FILES) {
        const path = join(FIXTURES_PNG, name);

        it(`returns a Hash for ${name}`, async () => {
            const h = await averageHash(path, 8);
            expect(h).toBeInstanceOf(rih.Hash);
        });

        it(`averageHash === averageHashBytes for ${name}`, async () => {
            const [hPath, hBytes] = await Promise.all([
                averageHash(path, 8),
                readFile(path).then(b => averageHashBytes(new Uint8Array(b), 8)),
            ]);
            expect(hPath.toHex()).toBe(hBytes.toHex());
        });

        it(`averageHash matches rih.averageHash(decodeFile) for ${name}`, async () => {
            const [hSquint, img] = await Promise.all([
                averageHash(path, 8),
                decodeFile(path),
            ]);
            const hRih = rih.averageHash(img, 8);
            expect(hSquint.toHex()).toBe(hRih.toHex());
        });
    }
});

// ---------------------------------------------------------------------------
// dhash
// ---------------------------------------------------------------------------
describe("dhash", () => {
    for (const name of ["imagehash.png", "checker-256.png"]) {
        const path = join(FIXTURES_PNG, name);

        it(`returns a Hash for ${name}`, async () => {
            const h = await dhash(path, 8);
            expect(h).toBeInstanceOf(rih.Hash);
        });

        it(`dhash === dhashBytes for ${name}`, async () => {
            const [hPath, hBytes] = await Promise.all([
                dhash(path, 8),
                readFile(path).then(b => dhashBytes(new Uint8Array(b), 8)),
            ]);
            expect(hPath.toHex()).toBe(hBytes.toHex());
        });
    }
});

// ---------------------------------------------------------------------------
// dhashVertical
// ---------------------------------------------------------------------------
describe("dhashVertical", () => {
    for (const name of ["imagehash.png", "checker-256.png"]) {
        const path = join(FIXTURES_PNG, name);

        it(`returns a Hash for ${name}`, async () => {
            const h = await dhashVertical(path, 8);
            expect(h).toBeInstanceOf(rih.Hash);
        });

        it(`dhashVertical === dhashVerticalBytes for ${name}`, async () => {
            const [hPath, hBytes] = await Promise.all([
                dhashVertical(path, 8),
                readFile(path).then(b => dhashVerticalBytes(new Uint8Array(b), 8)),
            ]);
            expect(hPath.toHex()).toBe(hBytes.toHex());
        });
    }
});

// ---------------------------------------------------------------------------
// phashSimple
// ---------------------------------------------------------------------------
describe("phashSimple", () => {
    for (const name of ["imagehash.png", "peppers.png"]) {
        const path = join(FIXTURES_PNG, name);

        it(`returns a Hash for ${name}`, async () => {
            const h = await phashSimple(path, 8);
            expect(h).toBeInstanceOf(rih.Hash);
        });

        it(`phashSimple === phashSimpleBytes for ${name}`, async () => {
            const [hPath, hBytes] = await Promise.all([
                phashSimple(path, 8),
                readFile(path).then(b => phashSimpleBytes(new Uint8Array(b), 8)),
            ]);
            expect(hPath.toHex()).toBe(hBytes.toHex());
        });
    }
});

// ---------------------------------------------------------------------------
// whashHaar
// ---------------------------------------------------------------------------
describe("whashHaar", () => {
    for (const name of ["imagehash.png", "peppers.png"]) {
        const path = join(FIXTURES_PNG, name);

        it(`returns a Hash for ${name}`, async () => {
            const h = await whashHaar(path, 8);
            expect(h).toBeInstanceOf(rih.Hash);
        });

        it(`whashHaar === whashHaarBytes for ${name}`, async () => {
            const [hPath, hBytes] = await Promise.all([
                whashHaar(path, 8),
                readFile(path).then(b => whashHaarBytes(new Uint8Array(b), 8)),
            ]);
            expect(hPath.toHex()).toBe(hBytes.toHex());
        });
    }
});

// ---------------------------------------------------------------------------
// whashDb4
// ---------------------------------------------------------------------------
describe("whashDb4", () => {
    for (const name of ["imagehash.png", "peppers.png"]) {
        const path = join(FIXTURES_PNG, name);

        it(`returns a Hash for ${name}`, async () => {
            const h = await whashDb4(path, 8);
            expect(h).toBeInstanceOf(rih.Hash);
        });

        it(`whashDb4 === whashDb4Bytes for ${name}`, async () => {
            const [hPath, hBytes] = await Promise.all([
                whashDb4(path, 8),
                readFile(path).then(b => whashDb4Bytes(new Uint8Array(b), 8)),
            ]);
            expect(hPath.toHex()).toBe(hBytes.toHex());
        });
    }
});

// ---------------------------------------------------------------------------
// colorhash
// ---------------------------------------------------------------------------
describe("colorhash", () => {
    for (const name of ["imagehash.png", "peppers.png"]) {
        const path = join(FIXTURES_PNG, name);

        it(`returns a Hash for ${name}`, async () => {
            const h = await colorhash(path, 3);
            expect(h).toBeInstanceOf(rih.Hash);
        });

        it(`colorhash === colorhashBytes for ${name}`, async () => {
            const [hPath, hBytes] = await Promise.all([
                colorhash(path, 3),
                readFile(path).then(b => colorhashBytes(new Uint8Array(b), 3)),
            ]);
            expect(hPath.toHex()).toBe(hBytes.toHex());
        });

        it(`colorhash matches rih.colorhash(decodeFile) for ${name}`, async () => {
            const [hSquint, img] = await Promise.all([
                colorhash(path, 3),
                decodeFile(path),
            ]);
            const hRih = rih.colorhash(img, 3);
            expect(hSquint.toHex()).toBe(hRih.toHex());
        });
    }
});

// ---------------------------------------------------------------------------
// cropResistantHash
// ---------------------------------------------------------------------------
describe("cropResistantHash", () => {
    for (const name of ["imagehash.png", "peppers.png"]) {
        const path = join(FIXTURES_PNG, name);

        it(`returns an ImageMultiHash for ${name}`, async () => {
            const mh = await cropResistantHash(path);
            expect(mh).toBeInstanceOf(ImageMultiHash);
            expect(mh.segmentHashes.length).toBeGreaterThan(0);
        });

        it(`cropResistantHash === cropResistantHashBytes for ${name}`, async () => {
            const [mhPath, mhBytes] = await Promise.all([
                cropResistantHash(path),
                readFile(path).then(b => cropResistantHashBytes(new Uint8Array(b))),
            ]);
            expect(mhPath.toString()).toBe(mhBytes.toString());
        });

        it(`cropResistantHash matches rih.cropResistantHash(decodeFile) for ${name}`, async () => {
            const [mhSquint, img] = await Promise.all([
                cropResistantHash(path),
                decodeFile(path),
            ]);
            const mhRih = rih.cropResistantHash(img);
            expect(mhSquint.toString()).toBe(mhRih.toString());
        });
    }
});

// ---------------------------------------------------------------------------
// Re-export sanity checks
// ---------------------------------------------------------------------------
describe("re-exports", () => {
    it("hexToHash round-trips a 16-char hex string", () => {
        const hex = "ffffffffffffffff";
        const h = hexToHash(hex);
        expect(h.toHex()).toBe(hex);
    });

    it("hexToFlathash round-trips a colorhash string", async () => {
        const path = join(FIXTURES_PNG, "imagehash.png");
        const h = await colorhash(path, 3);
        const hex = h.toHex();
        const h2 = hexToFlathash(hex, 3);
        expect(h2.toHex()).toBe(hex);
    });

    it("hexToMultiHash round-trips a cropResistantHash string", async () => {
        const path = join(FIXTURES_PNG, "imagehash.png");
        const mh = await cropResistantHash(path);
        const str = mh.toString();
        const mh2 = hexToMultiHash(str);
        expect(mh2.toString()).toBe(str);
    });

    it("ImageMultiHash is exported and constructible", () => {
        const bits = [[true, false], [false, true]];
        const h = new rih.Hash(bits);
        const mh = new ImageMultiHash([h]);
        expect(mh.segmentHashes.length).toBe(1);
    });
});
