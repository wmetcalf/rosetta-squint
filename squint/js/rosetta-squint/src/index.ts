import { open, lstat } from "node:fs/promises";
import { decode, type DecodedImage } from "rosetta-squint-decode";
import * as rih from "rosetta-squint-hash";

// Re-export key types and utilities from the underlying packages for ergonomics.
export type { Hash } from "rosetta-squint-hash";
export type { Format } from "rosetta-squint-decode";
export { ImageMultiHash, hexToHash, hexToFlathash, hexToMultiHash } from "rosetta-squint-hash";

/**
 * Maximum allowed size for path-based decode inputs. Refuse anything larger
 * BEFORE reading bytes. Callers that genuinely need to process images larger
 * than this should decode via rosetta-squint-decode directly after explicit
 * validation.
 */
export const MAX_FILE_SIZE = 256 * 1024 * 1024; // 256 MiB

/** Adapt a rosetta-squint-decode DecodedImage into the rosetta-squint-hash RgbImage shape. */
function decodedToRgbImage(d: DecodedImage): rih.RgbImage {
    return {
        width: d.width,
        height: d.height,
        data: d.data,
        channels: d.channels,
    };
}

/** Decode raw bytes (any supported format) into the rgb image shape used by the hash lib. */
export async function decodeBytes(bytes: Uint8Array): Promise<rih.RgbImage> {
    const decoded = await decode(bytes);
    return decodedToRgbImage(decoded);
}

/** Read a file from disk and decode.
 *
 * Refuses symlinks (via `lstat`), non-regular files (FIFOs, /dev/zero,
 * character devices, etc.) and files larger than MAX_FILE_SIZE BEFORE
 * reading bytes — without these guards `readFile("/dev/zero")` would loop
 * until OOM and a 300 MiB sparse file would allocate 300 MiB even though
 * it contains no image. Callers who genuinely want symlink resolution
 * must do it explicitly (e.g. `fs.promises.realpath`) before calling
 * this function.
 *
 * Node's `fs.open` doesn't directly expose `O_NOFOLLOW`, so we lstat the
 * path first and reject symlinks. The window between the lstat and the
 * subsequent open is narrow — the attacker would have to swap the target
 * between those two syscalls, much harder than swapping the symlink
 * destination across an unrelated stat→read window.
 *
 * The file is then opened ONCE via `fs.open`, and `fhandle.stat()` plus
 * `fhandle.read()` operate on the same fd. `fhandle.stat()` ultimately
 * calls fstat(2) on the open descriptor, not stat(2) on the path, which
 * closes the TOCTOU window between the size check and the read. The read
 * is bounded by `MAX_FILE_SIZE + 1` so a concurrent writer that grows the
 * file after the size check is still rejected.
 */
export async function decodeFile(path: string): Promise<rih.RgbImage> {
    // Lstat-then-open is unfortunately not atomic on Node — there's no
    // public API for O_NOFOLLOW. The race is much narrower than the
    // stat→read race we already close below.
    const linkStat = await lstat(path);
    if (linkStat.isSymbolicLink()) {
        throw new TypeError(`symlink not allowed: ${path}`);
    }
    const fh = await open(path, "r");
    try {
        const st = await fh.stat();
        if (!st.isFile()) {
            throw new Error(`not a regular file: ${path}`);
        }
        if (st.size > MAX_FILE_SIZE) {
            throw new Error(
                `input file too large: ${st.size} bytes (max ${MAX_FILE_SIZE} `
                + `bytes / 256 MiB). For images above this threshold, decode `
                + `via rosetta-squint-decode directly after explicit validation.`,
            );
        }
        // Read up to MAX_FILE_SIZE+1 bytes so a concurrent writer that
        // grows the file post-stat is rejected rather than silently
        // exceeding the cap.
        const cap = Math.min(st.size, MAX_FILE_SIZE) + 1;
        const buf = new Uint8Array(cap);
        let total = 0;
        while (total < cap) {
            const { bytesRead } = await fh.read(
                buf, total, cap - total, total,
            );
            if (bytesRead === 0) break;
            total += bytesRead;
        }
        if (total > MAX_FILE_SIZE) {
            throw new Error(
                `input file too large: ${total} bytes (max ${MAX_FILE_SIZE} `
                + `bytes / 256 MiB). For images above this threshold, decode `
                + `via rosetta-squint-decode directly after explicit validation.`,
            );
        }
        const bytes = buf.subarray(0, total);
        return decodeBytes(bytes);
    } finally {
        await fh.close();
    }
}

// ---------------------------------------------------------------------------
// Convenience hash functions — each accepts either a file path or raw bytes.
// ---------------------------------------------------------------------------

export async function averageHash(path: string, hashSize: number): Promise<rih.Hash> {
    return rih.averageHash(await decodeFile(path), hashSize);
}
export async function averageHashBytes(bytes: Uint8Array, hashSize: number): Promise<rih.Hash> {
    return rih.averageHash(await decodeBytes(bytes), hashSize);
}

export async function phash(path: string, hashSize: number): Promise<rih.Hash> {
    return rih.phash(await decodeFile(path), hashSize);
}
export async function phashBytes(bytes: Uint8Array, hashSize: number): Promise<rih.Hash> {
    return rih.phash(await decodeBytes(bytes), hashSize);
}

export async function phashSimple(path: string, hashSize: number): Promise<rih.Hash> {
    return rih.phashSimple(await decodeFile(path), hashSize);
}
export async function phashSimpleBytes(bytes: Uint8Array, hashSize: number): Promise<rih.Hash> {
    return rih.phashSimple(await decodeBytes(bytes), hashSize);
}

export async function dhash(path: string, hashSize: number): Promise<rih.Hash> {
    return rih.dhash(await decodeFile(path), hashSize);
}
export async function dhashBytes(bytes: Uint8Array, hashSize: number): Promise<rih.Hash> {
    return rih.dhash(await decodeBytes(bytes), hashSize);
}

export async function dhashVertical(path: string, hashSize: number): Promise<rih.Hash> {
    return rih.dhashVertical(await decodeFile(path), hashSize);
}
export async function dhashVerticalBytes(bytes: Uint8Array, hashSize: number): Promise<rih.Hash> {
    return rih.dhashVertical(await decodeBytes(bytes), hashSize);
}

export async function whashHaar(path: string, hashSize: number): Promise<rih.Hash> {
    return rih.whashHaar(await decodeFile(path), hashSize);
}
export async function whashHaarBytes(bytes: Uint8Array, hashSize: number): Promise<rih.Hash> {
    return rih.whashHaar(await decodeBytes(bytes), hashSize);
}

export async function whashDb4(path: string, hashSize: number): Promise<rih.Hash> {
    return rih.whashDb4(await decodeFile(path), hashSize);
}
export async function whashDb4Bytes(bytes: Uint8Array, hashSize: number): Promise<rih.Hash> {
    return rih.whashDb4(await decodeBytes(bytes), hashSize);
}

export async function whashDb4Robust(path: string, hashSize: number): Promise<rih.Hash> {
    return rih.whashDb4Robust(await decodeFile(path), hashSize);
}
export async function whashDb4RobustBytes(bytes: Uint8Array, hashSize: number): Promise<rih.Hash> {
    return rih.whashDb4Robust(await decodeBytes(bytes), hashSize);
}

export async function colorhash(path: string, binbits: number): Promise<rih.Hash> {
    return rih.colorhash(await decodeFile(path), binbits);
}
export async function colorhashBytes(bytes: Uint8Array, binbits: number): Promise<rih.Hash> {
    return rih.colorhash(await decodeBytes(bytes), binbits);
}

export async function cropResistantHash(
    path: string,
    limitSegments?: number,
): Promise<rih.ImageMultiHash> {
    return rih.cropResistantHash(await decodeFile(path), limitSegments);
}
export async function cropResistantHashBytes(
    bytes: Uint8Array,
    limitSegments?: number,
): Promise<rih.ImageMultiHash> {
    return rih.cropResistantHash(await decodeBytes(bytes), limitSegments);
}
