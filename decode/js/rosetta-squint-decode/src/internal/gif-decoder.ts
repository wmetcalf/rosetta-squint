// In-tree pure-TS GIF89a/87a decoder. First frame only. Replaces the
// unmaintained `omggif` package (last commit 2019). Ported from the Swift
// port's GIFDecoder, which is byte-exact-validated against PIL/Pillow.

import { DecodeError } from "../errors.js";
import { checkDimensions } from "./limits.js";

export interface GifFrame {
  width: number;
  height: number;
  /** RGB or RGBA bytes, row-major. */
  pixels: Uint8Array;
  /** 3 for RGB-only, 4 for RGBA (transparency present in first frame). */
  channels: 3 | 4;
}

// ---------------------------------------------------------------------------
// Public entry point
// ---------------------------------------------------------------------------

export function decodeGifFirstFrame(bytes: Uint8Array): GifFrame {
  const parser = new GIFParser(bytes);

  // Parse header (6 bytes) and logical screen descriptor (7 bytes)
  parser.parseHeader();
  parser.parseLogicalScreenDescriptor();

  // Read global color table if present
  if (parser.hasGlobalColorTable) {
    parser.parseGlobalColorTable();
  }

  // Walk blocks until first Image Descriptor
  // Use a ref-box so skipOrParseExtension can mutate it
  const tidxBox: { value: number | null } = { value: null };

  while (true) {
    const intro = parser.readByte();
    if (intro === null) {
      throw new DecodeError("corruptInput", "gif", "unexpected EOF before image descriptor");
    }
    if (intro === 0x21) {
      // Extension introducer
      parser.skipOrParseExtension(tidxBox);
    } else if (intro === 0x2c) {
      // Image Descriptor
      const frame = parser.parseImageDescriptor();

      // D-M2: per-frame dimension validation. The image descriptor declares
      // frame dims independently of the LSD (canvas) dims; without this check
      // a 16x16 LSD can still carry a 65535x65535 frame and drive ~4 GB of
      // allocation in lzwDecode below. MAX_PIXELS check runs first so a
      // decompression-bomb frame is flagged as imageTooLarge rather than
      // shadowed by the canvas-extent check.
      // 1) Frame pixel count itself must respect MAX_PIXELS.
      checkDimensions(frame.width, frame.height, "gif");
      // 2) Frame must lie within the canvas — otherwise input is corrupt.
      if (
        frame.left + frame.width > parser.lsdWidth ||
        frame.top + frame.height > parser.lsdHeight
      ) {
        throw new DecodeError(
          "corruptInput",
          "gif",
          `frame ${frame.width}x${frame.height} at (${frame.left},${frame.top}) ` +
            `extends beyond canvas ${parser.lsdWidth}x${parser.lsdHeight}`,
        );
      }

      let palette: Uint8Array;
      if (frame.localColorTable !== null) {
        palette = frame.localColorTable;
      } else if (parser.globalColorTable !== null) {
        palette = parser.globalColorTable;
      } else {
        throw new DecodeError("corruptInput", "gif", "no color table available");
      }

      let indices = parser.decodeLZWImage(frame.width, frame.height);
      if (frame.interlaced) {
        indices = deinterlace(indices, frame.width, frame.height);
      }

      return emitFrame(
        parser.lsdWidth,
        parser.lsdHeight,
        frame.left,
        frame.top,
        frame.width,
        frame.height,
        indices,
        palette,
        tidxBox.value,
        parser.bgIndex,
      );
    } else if (intro === 0x3b) {
      // Trailer
      throw new DecodeError("corruptInput", "gif", "GIF trailer reached before any image");
    } else {
      throw new DecodeError(
        "corruptInput",
        "gif",
        `unknown block introducer: 0x${intro.toString(16)}`,
      );
    }
  }
}

// ---------------------------------------------------------------------------
// Frame emission — compose canvas, write frame pixels, handle transparency
// ---------------------------------------------------------------------------

function emitFrame(
  canvasWidth: number,
  canvasHeight: number,
  frameLeft: number,
  frameTop: number,
  frameWidth: number,
  frameHeight: number,
  indices: Uint8Array,
  palette: Uint8Array, // flat RGB triples: [r0,g0,b0, r1,g1,b1, ...]
  transparentIndex: number | null,
  bgIndex: number,
): GifFrame {
  const hasAlpha = transparentIndex !== null;
  const bpp = hasAlpha ? 4 : 3;
  const paletteEntries = palette.length / 3;

  const data = new Uint8Array(canvasWidth * canvasHeight * bpp);

  // Fill canvas background
  let bgR = 0, bgG = 0, bgB = 0;
  if (bgIndex < paletteEntries) {
    bgR = palette[bgIndex * 3]!;
    bgG = palette[bgIndex * 3 + 1]!;
    bgB = palette[bgIndex * 3 + 2]!;
  }
  const total = canvasWidth * canvasHeight;
  if (!hasAlpha) {
    for (let i = 0; i < total; i++) {
      data[i * 3]     = bgR;
      data[i * 3 + 1] = bgG;
      data[i * 3 + 2] = bgB;
    }
  } else {
    // For RGBA canvas, background pixels are fully opaque bg color
    for (let i = 0; i < total; i++) {
      data[i * 4]     = bgR;
      data[i * 4 + 1] = bgG;
      data[i * 4 + 2] = bgB;
      data[i * 4 + 3] = 255;
    }
  }

  // Write frame pixels into canvas
  for (let fy = 0; fy < frameHeight; fy++) {
    const cy = frameTop + fy;
    if (cy < 0 || cy >= canvasHeight) continue;
    for (let fx = 0; fx < frameWidth; fx++) {
      const cx = frameLeft + fx;
      if (cx < 0 || cx >= canvasWidth) continue;
      const srcIdx = fy * frameWidth + fx;
      if (srcIdx >= indices.length) continue;
      const palIdx = indices[srcIdx]!;
      let r = 0, g = 0, b = 0;
      if (palIdx < paletteEntries) {
        r = palette[palIdx * 3]!;
        g = palette[palIdx * 3 + 1]!;
        b = palette[palIdx * 3 + 2]!;
      }
      const dstBase = (cy * canvasWidth + cx) * bpp;
      data[dstBase]     = r;
      data[dstBase + 1] = g;
      data[dstBase + 2] = b;
      if (hasAlpha) {
        // Transparent index: alpha=0 but RGB from palette (PIL preserves palette RGB)
        data[dstBase + 3] = palIdx === transparentIndex ? 0 : 255;
      }
    }
  }

  return {
    width: canvasWidth,
    height: canvasHeight,
    pixels: data,
    channels: hasAlpha ? 4 : 3,
  };
}

// ---------------------------------------------------------------------------
// Interlace de-interleaving
//
// GIF interlacing delivers rows in 4 passes:
//   Pass 1: rows 0, 8, 16, ...  (step 8, start 0)
//   Pass 2: rows 4, 12, 20, ... (step 8, start 4)
//   Pass 3: rows 2, 6, 10, ...  (step 4, start 2)
//   Pass 4: rows 1, 3, 5, ...   (step 2, start 1)
//
// `indices` contains pixels in that scan order. We reorder them into
// normal top-to-bottom, left-to-right order.
// ---------------------------------------------------------------------------

function deinterlace(indices: Uint8Array, width: number, height: number): Uint8Array {
  // Build list of actual row indices in interlaced order
  const interlacedRows: number[] = [];
  const passes: Array<[number, number]> = [[0, 8], [4, 8], [2, 4], [1, 2]];
  for (const [start, step] of passes) {
    for (let row = start; row < height; row += step) {
      interlacedRows.push(row);
    }
  }

  // interlacedRows[i] is the destination row for the i-th row in the raw LZW output
  const result = new Uint8Array(width * height);
  for (let srcRow = 0; srcRow < interlacedRows.length; srcRow++) {
    const dstRow = interlacedRows[srcRow]!;
    const srcBase = srcRow * width;
    const dstBase = dstRow * width;
    for (let col = 0; col < width; col++) {
      if (srcBase + col < indices.length) {
        result[dstBase + col] = indices[srcBase + col]!;
      }
    }
  }
  return result;
}

// ---------------------------------------------------------------------------
// GIFParser
// ---------------------------------------------------------------------------

interface FrameInfo {
  left: number;
  top: number;
  width: number;
  height: number;
  /** Flat RGB triples, or null if no local color table. */
  localColorTable: Uint8Array | null;
  interlaced: boolean;
}

class GIFParser {
  private readonly bytes: Uint8Array;
  private pos: number = 0;

  // LSD fields
  lsdWidth: number = 0;
  lsdHeight: number = 0;
  hasGlobalColorTable: boolean = false;
  private gctSize: number = 0;
  bgIndex: number = 0;

  /** Decoded GCT as flat RGB triples. */
  globalColorTable: Uint8Array | null = null;

  constructor(bytes: Uint8Array) {
    this.bytes = bytes;
  }

  // ---------------------------------------------------------------------------
  // Basic reading
  // ---------------------------------------------------------------------------

  readByte(): number | null {
    if (this.pos >= this.bytes.length) return null;
    return this.bytes[this.pos++]!;
  }

  private readBytes(n: number): Uint8Array | null {
    if (this.pos + n > this.bytes.length) return null;
    const slice = this.bytes.subarray(this.pos, this.pos + n);
    this.pos += n;
    return slice;
  }

  private readU16LE(): number | null {
    if (this.pos + 2 > this.bytes.length) return null;
    const lo = this.bytes[this.pos]!;
    const hi = this.bytes[this.pos + 1]!;
    this.pos += 2;
    return lo | (hi << 8);
  }

  // ---------------------------------------------------------------------------
  // Header
  // ---------------------------------------------------------------------------

  parseHeader(): void {
    const sig = this.readBytes(6);
    if (sig === null) {
      throw new DecodeError("corruptInput", "gif", "truncated header");
    }
    // sig[0..2] must be "GIF"
    if (sig[0] !== 0x47 || sig[1] !== 0x49 || sig[2] !== 0x46) {
      throw new DecodeError("unsupportedFormat", null, "not a GIF file");
    }
    // sig[3..5] must be "87a" or "89a"
    if (
      sig[3] !== 0x38 ||
      (sig[4] !== 0x37 && sig[4] !== 0x39) ||
      sig[5] !== 0x61
    ) {
      throw new DecodeError("corruptInput", "gif", "invalid GIF version");
    }
  }

  parseLogicalScreenDescriptor(): void {
    const w = this.readU16LE();
    const h = this.readU16LE();
    if (w === null || h === null) {
      throw new DecodeError("corruptInput", "gif", "truncated logical screen descriptor");
    }
    const packed = this.readByte();
    const bg     = this.readByte();
    const _ar    = this.readByte(); // aspect ratio, ignored
    if (packed === null || bg === null || _ar === null) {
      throw new DecodeError("corruptInput", "gif", "truncated logical screen descriptor");
    }
    checkDimensions(w, h, "gif");
    this.lsdWidth  = w;
    this.lsdHeight = h;
    this.hasGlobalColorTable = ((packed >> 7) & 1) === 1;
    this.gctSize   = packed & 0x07;
    this.bgIndex   = bg;
  }

  parseGlobalColorTable(): void {
    const count = 1 << (this.gctSize + 1);
    this.globalColorTable = this.parseColorTable(count);
  }

  private parseColorTable(count: number): Uint8Array {
    const data = this.readBytes(count * 3);
    if (data === null) {
      throw new DecodeError("corruptInput", "gif", "truncated color table");
    }
    // Return a copy so the subarray is independent of the main buffer
    return new Uint8Array(data);
  }

  // ---------------------------------------------------------------------------
  // Extensions
  // ---------------------------------------------------------------------------

  skipOrParseExtension(tidxBox: { value: number | null }): void {
    const label = this.readByte();
    if (label === null) {
      throw new DecodeError("corruptInput", "gif", "truncated extension label");
    }
    if (label === 0xf9) {
      // Graphic Control Extension
      this.parseGCE(tidxBox);
    } else {
      // All other extensions: skip sub-blocks
      this.skipSubBlocks();
    }
  }

  private parseGCE(tidxBox: { value: number | null }): void {
    // Block size byte (should be 4 for a standard GCE)
    const blockSize = this.readByte();
    if (blockSize === null) {
      throw new DecodeError("corruptInput", "gif", "truncated GCE block size");
    }
    if (blockSize < 4) {
      throw new DecodeError("corruptInput", "gif", "GCE block size < 4");
    }
    const packed   = this.readByte();
    const _delayLo = this.readByte(); // delay lo (ignored)
    const _delayHi = this.readByte(); // delay hi (ignored)
    const tidx     = this.readByte();
    if (packed === null || _delayLo === null || _delayHi === null || tidx === null) {
      throw new DecodeError("corruptInput", "gif", "truncated GCE data");
    }
    // Skip any extra bytes if blockSize > 4
    if (blockSize > 4) {
      const extra = this.readBytes(blockSize - 4);
      if (extra === null) {
        throw new DecodeError("corruptInput", "gif", "truncated GCE extra bytes");
      }
    }
    // Read block terminator
    const term = this.readByte();
    if (term === null) {
      throw new DecodeError("corruptInput", "gif", "truncated GCE terminator");
    }
    if (term !== 0x00) {
      // Not a proper terminator; back up one and skip sub-blocks
      this.pos -= 1;
      this.skipSubBlocks();
    }

    const transparentFlag = packed & 0x01;
    if (transparentFlag !== 0) {
      tidxBox.value = tidx;
    } else {
      tidxBox.value = null;
    }
  }

  private skipSubBlocks(): void {
    while (true) {
      const sz = this.readByte();
      if (sz === null) {
        throw new DecodeError("corruptInput", "gif", "truncated sub-block size");
      }
      if (sz === 0) return;
      const data = this.readBytes(sz);
      if (data === null) {
        throw new DecodeError("corruptInput", "gif", "truncated sub-block data");
      }
    }
  }

  // ---------------------------------------------------------------------------
  // Image Descriptor
  // ---------------------------------------------------------------------------

  parseImageDescriptor(): FrameInfo {
    // 0x2C already consumed; read remaining 9 bytes
    const left   = this.readU16LE();
    const top    = this.readU16LE();
    const width  = this.readU16LE();
    const height = this.readU16LE();
    const packed = this.readByte();
    if (left === null || top === null || width === null || height === null || packed === null) {
      throw new DecodeError("corruptInput", "gif", "truncated image descriptor");
    }
    const hasLCT    = ((packed >> 7) & 1) === 1;
    const interlace = ((packed >> 6) & 1) === 1;
    const lctSize   = packed & 0x07;

    let lct: Uint8Array | null = null;
    if (hasLCT) {
      const count = 1 << (lctSize + 1);
      lct = this.parseColorTable(count);
    }

    return {
      left,
      top,
      width,
      height,
      localColorTable: lct,
      interlaced: interlace,
    };
  }

  // ---------------------------------------------------------------------------
  // LZW Image Data
  // ---------------------------------------------------------------------------

  decodeLZWImage(frameWidth: number, frameHeight: number): Uint8Array {
    const minCodeSizeByte = this.readByte();
    if (minCodeSizeByte === null) {
      throw new DecodeError("corruptInput", "gif", "missing LZW minimum code size");
    }
    const minCodeSize = minCodeSizeByte;
    if (minCodeSize < 2 || minCodeSize > 12) {
      throw new DecodeError(
        "corruptInput",
        "gif",
        `invalid LZW min code size: ${minCodeSize}`,
      );
    }

    // Collect all sub-block data into one flat buffer
    const chunks: Uint8Array[] = [];
    let totalLen = 0;
    while (true) {
      const sz = this.readByte();
      if (sz === null) {
        throw new DecodeError("corruptInput", "gif", "truncated LZW sub-block size");
      }
      if (sz === 0) break;
      const chunk = this.readBytes(sz);
      if (chunk === null) {
        throw new DecodeError("corruptInput", "gif", "truncated LZW sub-block data");
      }
      chunks.push(chunk);
      totalLen += sz;
    }

    // Flatten into one Uint8Array
    const lzwData = new Uint8Array(totalLen);
    let off = 0;
    for (const c of chunks) {
      lzwData.set(c, off);
      off += c.length;
    }

    return lzwDecode(lzwData, minCodeSize, frameWidth * frameHeight);
  }
}

// ---------------------------------------------------------------------------
// LZW Decompressor (LSB-first bit stream, GIF variant)
// ---------------------------------------------------------------------------

function lzwDecode(data: Uint8Array, minCodeSize: number, pixelCount: number): Uint8Array {
  const clearCode = 1 << minCodeSize;
  const endCode   = clearCode + 1;

  // Bit reader state (LSB-first)
  let byteIdx = 0;
  let bitIdx  = 0;

  function readBits(n: number): number | null {
    let value    = 0;
    let bitsRead = 0;
    while (bitsRead < n) {
      if (byteIdx >= data.length) return null;
      const bit = (data[byteIdx]! >> bitIdx) & 1;
      value |= bit << bitsRead;
      bitsRead++;
      bitIdx++;
      if (bitIdx === 8) {
        byteIdx++;
        bitIdx = 0;
      }
    }
    return value;
  }

  // Code table: each entry is a Uint8Array byte sequence.
  // Entries 0..<clearCode are single-byte literal sequences.
  // Sentinel slots at clearCode and endCode are left empty (length 0).
  let codeTable: Uint8Array[] = [];
  let codeSize  = minCodeSize + 1;
  let nextCode  = endCode + 1;

  function resetTable(): void {
    codeTable = new Array<Uint8Array>(clearCode + 2);
    for (let i = 0; i < clearCode; i++) {
      codeTable[i] = new Uint8Array([i]);
    }
    codeTable[clearCode] = new Uint8Array(0);
    codeTable[endCode]   = new Uint8Array(0);
    codeSize  = minCodeSize + 1;
    nextCode  = endCode + 1;
  }

  resetTable();

  const output = new Uint8Array(pixelCount);
  let outPos = 0;

  function emit(seq: Uint8Array): void {
    const space = pixelCount - outPos;
    const n = seq.length < space ? seq.length : space;
    output.set(seq.subarray(0, n), outPos);
    outPos += n;
  }

  // prevSequence: the sequence emitted by the last code.
  // null means we are at the start of a fresh run (just after a clear, or at the
  // very beginning). In this state the next code is the "first after clear":
  // we emit it, record it as prevSequence, and DON'T add a new code table entry.
  let prevSequence: Uint8Array | null = null;

  while (outPos < pixelCount) {
    const code = readBits(codeSize);
    if (code === null) break;

    if (code === endCode) break;

    if (code === clearCode) {
      resetTable();
      prevSequence = null;
      continue;
    }

    if (prevSequence === null) {
      // First real code after a clear (or absolute start).
      // Must be a literal palette code (0..<clearCode).
      if (code >= clearCode) {
        throw new DecodeError(
          "corruptInput",
          "gif",
          `LZW: first code after clear is out of range: ${code}`,
        );
      }
      const seq = codeTable[code]!;
      emit(seq);
      prevSequence = seq;
      continue;
    }

    const prev = prevSequence;

    // Look up current code in table (or handle the "code == nextCode" special case).
    let entry: Uint8Array;
    if (code < codeTable.length && codeTable[code] !== undefined && codeTable[code]!.length > 0) {
      entry = codeTable[code]!;
    } else if (code === nextCode) {
      // The new code being defined right now: its sequence is prev + prev[0].
      entry = new Uint8Array(prev.length + 1);
      entry.set(prev);
      entry[prev.length] = prev[0]!;
    } else {
      throw new DecodeError(
        "corruptInput",
        "gif",
        `LZW: code ${code} out of range (table size ${nextCode})`,
      );
    }

    emit(entry);

    // Define a new code table entry: prev_sequence + first_byte(entry).
    if (nextCode <= 4095) {
      const newEntry = new Uint8Array(prev.length + 1);
      newEntry.set(prev);
      newEntry[prev.length] = entry[0]!;

      if (nextCode < codeTable.length) {
        codeTable[nextCode] = newEntry;
      } else {
        codeTable.push(newEntry);
      }
      nextCode++;

      // Expand code size when the table fills the current bit width.
      if (nextCode === (1 << codeSize) && codeSize < 12) {
        codeSize++;
      }
    }

    prevSequence = entry;
  }

  // Return exactly pixelCount bytes (may be short if LZW data ended early)
  return output.subarray(0, pixelCount);
}
