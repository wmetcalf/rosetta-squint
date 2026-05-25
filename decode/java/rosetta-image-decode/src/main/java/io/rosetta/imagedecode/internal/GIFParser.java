package io.rosetta.imagedecode.internal;

import io.rosetta.imagedecode.DecodeException;
import io.rosetta.imagedecode.Format;

import java.io.ByteArrayOutputStream;

/**
 * Pure-Java GIF87a/89a parser. Ported from
 * {@code decode/js/rosetta-image-decode/src/internal/gif-decoder.ts}.
 *
 * <p>Walks the GIF block stream up to the first Image Descriptor and returns the
 * frame info. Handles Graphic Control Extensions (to extract the transparent
 * color index) and skips over all other extensions.
 */
final class GIFParser {
    private final byte[] bytes;
    private int pos = 0;

    // Logical Screen Descriptor fields
    int lsdWidth = 0;
    int lsdHeight = 0;
    boolean hasGlobalColorTable = false;
    private int gctSize = 0;
    int bgIndex = 0;

    /** Decoded GCT as flat RGB triples (length = entries * 3). null if not present. */
    byte[] globalColorTable = null;

    GIFParser(byte[] bytes) {
        this.bytes = bytes;
    }

    // ---------------------------------------------------------------------------
    // Basic reading. readByte returns -1 on EOF; readBytes/readU16LE return null.
    // ---------------------------------------------------------------------------

    int readByte() {
        if (pos >= bytes.length) return -1;
        return bytes[pos++] & 0xFF;
    }

    /** Returns a copy of n bytes, or null on EOF. */
    private byte[] readBytes(int n) {
        if (pos + n > bytes.length) return null;
        byte[] slice = new byte[n];
        System.arraycopy(bytes, pos, slice, 0, n);
        pos += n;
        return slice;
    }

    private int readU16LE() {
        if (pos + 2 > bytes.length) return -1;
        int lo = bytes[pos] & 0xFF;
        int hi = bytes[pos + 1] & 0xFF;
        pos += 2;
        return lo | (hi << 8);
    }

    // ---------------------------------------------------------------------------
    // Header
    // ---------------------------------------------------------------------------

    void parseHeader() throws DecodeException {
        byte[] sig = readBytes(6);
        if (sig == null) {
            throw new DecodeException(DecodeException.Kind.CORRUPT_INPUT, Format.GIF, "truncated header");
        }
        // sig[0..2] must be "GIF"
        if ((sig[0] & 0xFF) != 0x47 || (sig[1] & 0xFF) != 0x49 || (sig[2] & 0xFF) != 0x46) {
            throw new DecodeException(DecodeException.Kind.UNSUPPORTED_FORMAT, null, "not a GIF file");
        }
        // sig[3..5] must be "87a" or "89a"
        if ((sig[3] & 0xFF) != 0x38
            || ((sig[4] & 0xFF) != 0x37 && (sig[4] & 0xFF) != 0x39)
            || (sig[5] & 0xFF) != 0x61) {
            throw new DecodeException(DecodeException.Kind.CORRUPT_INPUT, Format.GIF, "invalid GIF version");
        }
    }

    void parseLogicalScreenDescriptor() throws DecodeException {
        int w = readU16LE();
        int h = readU16LE();
        if (w < 0 || h < 0) {
            throw new DecodeException(DecodeException.Kind.CORRUPT_INPUT, Format.GIF,
                "truncated logical screen descriptor");
        }
        int packed = readByte();
        int bg = readByte();
        int ar = readByte();   // aspect ratio, ignored
        if (packed < 0 || bg < 0 || ar < 0) {
            throw new DecodeException(DecodeException.Kind.CORRUPT_INPUT, Format.GIF,
                "truncated logical screen descriptor");
        }
        Limits.checkDimensions(w, h, Format.GIF);
        this.lsdWidth = w;
        this.lsdHeight = h;
        this.hasGlobalColorTable = ((packed >> 7) & 1) == 1;
        this.gctSize = packed & 0x07;
        this.bgIndex = bg;
    }

    void parseGlobalColorTable() throws DecodeException {
        int count = 1 << (this.gctSize + 1);
        this.globalColorTable = parseColorTable(count);
    }

    private byte[] parseColorTable(int count) throws DecodeException {
        byte[] data = readBytes(count * 3);
        if (data == null) {
            throw new DecodeException(DecodeException.Kind.CORRUPT_INPUT, Format.GIF, "truncated color table");
        }
        return data;
    }

    // ---------------------------------------------------------------------------
    // Extensions
    // ---------------------------------------------------------------------------

    /** Box for the transparent index (mutated by parseGCE). */
    static final class TidxBox {
        Integer value = null;
    }

    void skipOrParseExtension(TidxBox tidxBox) throws DecodeException {
        int label = readByte();
        if (label < 0) {
            throw new DecodeException(DecodeException.Kind.CORRUPT_INPUT, Format.GIF,
                "truncated extension label");
        }
        if (label == 0xF9) {
            parseGCE(tidxBox);
        } else {
            skipSubBlocks();
        }
    }

    private void parseGCE(TidxBox tidxBox) throws DecodeException {
        int blockSize = readByte();
        if (blockSize < 0) {
            throw new DecodeException(DecodeException.Kind.CORRUPT_INPUT, Format.GIF,
                "truncated GCE block size");
        }
        if (blockSize < 4) {
            throw new DecodeException(DecodeException.Kind.CORRUPT_INPUT, Format.GIF,
                "GCE block size < 4");
        }
        int packed = readByte();
        int delayLo = readByte();
        int delayHi = readByte();
        int tidx = readByte();
        if (packed < 0 || delayLo < 0 || delayHi < 0 || tidx < 0) {
            throw new DecodeException(DecodeException.Kind.CORRUPT_INPUT, Format.GIF,
                "truncated GCE data");
        }
        if (blockSize > 4) {
            byte[] extra = readBytes(blockSize - 4);
            if (extra == null) {
                throw new DecodeException(DecodeException.Kind.CORRUPT_INPUT, Format.GIF,
                    "truncated GCE extra bytes");
            }
        }
        int term = readByte();
        if (term < 0) {
            throw new DecodeException(DecodeException.Kind.CORRUPT_INPUT, Format.GIF,
                "truncated GCE terminator");
        }
        if (term != 0x00) {
            // Not a proper terminator: back up one and skip sub-blocks.
            this.pos -= 1;
            skipSubBlocks();
        }

        int transparentFlag = packed & 0x01;
        if (transparentFlag != 0) {
            tidxBox.value = tidx;
        } else {
            tidxBox.value = null;
        }
    }

    private void skipSubBlocks() throws DecodeException {
        while (true) {
            int sz = readByte();
            if (sz < 0) {
                throw new DecodeException(DecodeException.Kind.CORRUPT_INPUT, Format.GIF,
                    "truncated sub-block size");
            }
            if (sz == 0) return;
            byte[] data = readBytes(sz);
            if (data == null) {
                throw new DecodeException(DecodeException.Kind.CORRUPT_INPUT, Format.GIF,
                    "truncated sub-block data");
            }
        }
    }

    // ---------------------------------------------------------------------------
    // Image Descriptor
    // ---------------------------------------------------------------------------

    static final class FrameInfo {
        final int left;
        final int top;
        final int width;
        final int height;
        /** Flat RGB triples (length = entries * 3). null if no local color table. */
        final byte[] localColorTable;
        final boolean interlaced;

        FrameInfo(int left, int top, int width, int height, byte[] lct, boolean interlaced) {
            this.left = left;
            this.top = top;
            this.width = width;
            this.height = height;
            this.localColorTable = lct;
            this.interlaced = interlaced;
        }
    }

    FrameInfo parseImageDescriptor() throws DecodeException {
        // 0x2C already consumed; read remaining 9 bytes
        int left = readU16LE();
        int top = readU16LE();
        int width = readU16LE();
        int height = readU16LE();
        int packed = readByte();
        if (left < 0 || top < 0 || width < 0 || height < 0 || packed < 0) {
            throw new DecodeException(DecodeException.Kind.CORRUPT_INPUT, Format.GIF,
                "truncated image descriptor");
        }
        boolean hasLCT = ((packed >> 7) & 1) == 1;
        boolean interlace = ((packed >> 6) & 1) == 1;
        int lctSize = packed & 0x07;

        byte[] lct = null;
        if (hasLCT) {
            int count = 1 << (lctSize + 1);
            lct = parseColorTable(count);
        }

        return new FrameInfo(left, top, width, height, lct, interlace);
    }

    // ---------------------------------------------------------------------------
    // LZW Image Data
    // ---------------------------------------------------------------------------

    byte[] decodeLZWImage(int frameWidth, int frameHeight) throws DecodeException {
        int minCodeSize = readByte();
        if (minCodeSize < 0) {
            throw new DecodeException(DecodeException.Kind.CORRUPT_INPUT, Format.GIF,
                "missing LZW minimum code size");
        }
        if (minCodeSize < 2 || minCodeSize > 12) {
            throw new DecodeException(DecodeException.Kind.CORRUPT_INPUT, Format.GIF,
                "invalid LZW min code size: " + minCodeSize);
        }

        // Collect all sub-block data into one flat buffer.
        ByteArrayOutputStream chunks = new ByteArrayOutputStream();
        while (true) {
            int sz = readByte();
            if (sz < 0) {
                throw new DecodeException(DecodeException.Kind.CORRUPT_INPUT, Format.GIF,
                    "truncated LZW sub-block size");
            }
            if (sz == 0) break;
            byte[] chunk = readBytes(sz);
            if (chunk == null) {
                throw new DecodeException(DecodeException.Kind.CORRUPT_INPUT, Format.GIF,
                    "truncated LZW sub-block data");
            }
            chunks.write(chunk, 0, chunk.length);
        }

        // Guard against int overflow when computing pixelCount; we expect
        // dimensions to already be bounded by checkDimensions, but be defensive.
        long pixelsLong = (long) frameWidth * (long) frameHeight;
        if (pixelsLong < 0 || pixelsLong > Integer.MAX_VALUE) {
            throw new DecodeException(DecodeException.Kind.CORRUPT_INPUT, Format.GIF,
                "frame pixel count overflows int: " + pixelsLong);
        }
        return GIFLzwDecoder.decode(chunks.toByteArray(), minCodeSize, (int) pixelsLong);
    }
}
