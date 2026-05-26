package io.github.wmetcalf.rosettasquint.decode.internal;

import io.github.wmetcalf.rosettasquint.decode.DecodeException;
import io.github.wmetcalf.rosettasquint.decode.Format;

/**
 * Pure-Java GIF LZW decoder. Ported line-by-line from the JS port's
 * {@code lzwDecode} in {@code decode/js/rosetta-squint-decode/src/internal/gif-decoder.ts}.
 *
 * <p>LSB-first bit reader, GIF variant. Strictly rejects out-of-range codes — does not
 * fall back to the last-valid color the way {@code javax.imageio.GIFImageReader} does
 * (see SPEC §3.2).
 *
 * <p>Table size limit is hard-coded to 4095. Code size grows from {@code minCodeSize + 1}
 * up to 12 bits as the table fills.
 */
final class GIFLzwDecoder {
    private GIFLzwDecoder() {}

    /**
     * Decompresses {@code data} (the flat LZW byte stream collected from sub-blocks)
     * into {@code pixelCount} palette indices.
     *
     * @throws DecodeException with {@link DecodeException.Kind#CORRUPT_INPUT} on any
     *     out-of-range code (first-after-clear must be a literal; subsequent codes must
     *     refer to an existing table entry or the about-to-be-defined {@code nextCode}).
     */
    static byte[] decode(byte[] data, int minCodeSize, int pixelCount) throws DecodeException {
        final int clearCode = 1 << minCodeSize;
        final int endCode = clearCode + 1;

        BitReader reader = new BitReader(data);

        // Code table: each entry is a byte sequence.
        // Entries 0..<clearCode are single-byte literal sequences.
        // Sentinel slots at clearCode and endCode are zero-length.
        byte[][] codeTable = new byte[clearCode + 2][];
        int codeSize = minCodeSize + 1;
        int nextCode = endCode + 1;
        resetTable(codeTable, clearCode);

        byte[] output = new byte[pixelCount];
        int outPos = 0;

        // prevSequence: the sequence emitted by the last code.
        // null = at start of a fresh run (just after a clear, or absolute start).
        // In that state the next code is the "first after clear" — emit it, record it
        // as prevSequence, and DO NOT add a new code table entry.
        byte[] prevSequence = null;

        while (outPos < pixelCount) {
            int code = reader.readBits(codeSize);
            if (code < 0) break;  // EOF before pixelCount

            if (code == endCode) break;

            if (code == clearCode) {
                // Re-allocate to the original size; subsequent code definitions
                // append via grow().
                codeTable = new byte[clearCode + 2][];
                resetTable(codeTable, clearCode);
                codeSize = minCodeSize + 1;
                nextCode = endCode + 1;
                prevSequence = null;
                continue;
            }

            if (prevSequence == null) {
                // First real code after a clear (or absolute start).
                // Must be a literal palette code (0..<clearCode).
                if (code >= clearCode) {
                    throw new DecodeException(DecodeException.Kind.CORRUPT_INPUT, Format.GIF,
                        "LZW: first code after clear is out of range: " + code);
                }
                byte[] seq = codeTable[code];
                outPos = emit(output, outPos, seq, pixelCount);
                prevSequence = seq;
                continue;
            }

            final byte[] prev = prevSequence;

            // Look up current code in table (or handle the "code == nextCode" special case).
            byte[] entry;
            if (code < codeTable.length && codeTable[code] != null && codeTable[code].length > 0) {
                entry = codeTable[code];
            } else if (code == nextCode) {
                // The new code being defined right now: prev + prev[0].
                entry = new byte[prev.length + 1];
                System.arraycopy(prev, 0, entry, 0, prev.length);
                entry[prev.length] = prev[0];
            } else {
                throw new DecodeException(DecodeException.Kind.CORRUPT_INPUT, Format.GIF,
                    "LZW: code " + code + " out of range (table size " + nextCode + ")");
            }

            outPos = emit(output, outPos, entry, pixelCount);

            // Define a new code table entry: prev + first_byte(entry).
            if (nextCode <= 4095) {
                byte[] newEntry = new byte[prev.length + 1];
                System.arraycopy(prev, 0, newEntry, 0, prev.length);
                newEntry[prev.length] = entry[0];

                if (nextCode < codeTable.length) {
                    codeTable[nextCode] = newEntry;
                } else {
                    codeTable = grow(codeTable, nextCode + 1);
                    codeTable[nextCode] = newEntry;
                }
                nextCode++;

                // Expand code size when the table fills the current bit width.
                if (nextCode == (1 << codeSize) && codeSize < 12) {
                    codeSize++;
                }
            }

            prevSequence = entry;
        }

        // Return exactly pixelCount bytes (may be partially zero-filled if LZW data ended early).
        return output;
    }

    private static void resetTable(byte[][] table, int clearCode) {
        for (int i = 0; i < clearCode; i++) {
            table[i] = new byte[] { (byte) i };
        }
        table[clearCode] = new byte[0];
        table[clearCode + 1] = new byte[0];
    }

    private static byte[][] grow(byte[][] table, int minCapacity) {
        int newCapacity = table.length;
        // Match TS Array.push semantics: amortized growth so we don't reallocate on every code.
        while (newCapacity < minCapacity) {
            newCapacity = newCapacity == 0 ? 4 : newCapacity * 2;
        }
        byte[][] copy = new byte[newCapacity][];
        System.arraycopy(table, 0, copy, 0, table.length);
        return copy;
    }

    private static int emit(byte[] output, int outPos, byte[] seq, int pixelCount) {
        int space = pixelCount - outPos;
        int n = seq.length < space ? seq.length : space;
        System.arraycopy(seq, 0, output, outPos, n);
        return outPos + n;
    }

    /** LSB-first bit reader over a packed byte array. Returns -1 on EOF. */
    private static final class BitReader {
        private final byte[] data;
        private int byteIdx = 0;
        private int bitIdx = 0;

        BitReader(byte[] data) {
            this.data = data;
        }

        int readBits(int n) {
            int value = 0;
            int bitsRead = 0;
            while (bitsRead < n) {
                if (byteIdx >= data.length) return -1;
                int bit = ((data[byteIdx] & 0xFF) >> bitIdx) & 1;
                value |= bit << bitsRead;
                bitsRead++;
                bitIdx++;
                if (bitIdx == 8) {
                    byteIdx++;
                    bitIdx = 0;
                }
            }
            return value;
        }
    }
}
