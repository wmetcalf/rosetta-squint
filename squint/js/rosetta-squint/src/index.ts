import { readFile } from "node:fs/promises";
import { decode, type DecodedImage } from "rosetta-image-decode";
import * as rih from "rosetta-image-hash";

// Re-export key types and utilities from the underlying packages for ergonomics.
export type { Hash } from "rosetta-image-hash";
export type { Format } from "rosetta-image-decode";
export { ImageMultiHash, hexToHash, hexToFlathash, hexToMultiHash } from "rosetta-image-hash";

/** Adapt a rosetta-image-decode DecodedImage into the rosetta-image-hash RgbImage shape. */
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

/** Read a file from disk and decode. */
export async function decodeFile(path: string): Promise<rih.RgbImage> {
    const bytes = new Uint8Array(await readFile(path));
    return decodeBytes(bytes);
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
