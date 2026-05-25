package io.rosetta.imagedecode.internal;

import io.rosetta.imagedecode.Channels;
import io.rosetta.imagedecode.DecodeException;
import io.rosetta.imagedecode.DecodedImage;
import io.rosetta.imagedecode.Format;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class BMPDecoder {
    static final int BI_RGB = 0;
    static final int BI_RLE8 = 1;
    static final int BI_RLE4 = 2;
    static final int BI_BITFIELDS = 3;
    static final int BI_JPEG = 4;
    static final int BI_PNG = 5;
    static final int BI_ALPHABITFIELDS = 6;

    private BMPDecoder() {}

    public static DecodedImage decode(byte[] bytes) throws DecodeException {
        BMPHeader hdr = parseHeader(bytes);
        return switch (hdr.compression) {
            case BI_RGB -> switch (hdr.bitCount) {
                case 24 -> decodeRgb24(bytes, hdr);
                case 32 -> decodeRgb32(bytes, hdr);
                case 8  -> decodePal8(bytes, hdr);
                case 4  -> decodePal4(bytes, hdr);
                case 1  -> decodePal1(bytes, hdr);
                case 16 -> throw new DecodeException(DecodeException.Kind.CORRUPT_INPUT, Format.BMP,
                    "BI_RGB 16-bit not supported");
                default -> throw new DecodeException(DecodeException.Kind.CORRUPT_INPUT, Format.BMP,
                    "biBitCount " + hdr.bitCount + " for BI_RGB");
            };
            case BI_BITFIELDS, BI_ALPHABITFIELDS -> switch (hdr.bitCount) {
                case 16 -> decodeBitfields(bytes, hdr, 16);
                case 32 -> decodeBitfields(bytes, hdr, 32);
                default -> throw new DecodeException(DecodeException.Kind.CORRUPT_INPUT, Format.BMP,
                    "BI_BITFIELDS with biBitCount " + hdr.bitCount);
            };
            case BI_RLE8 -> decodeRle(bytes, hdr, 8);
            case BI_RLE4 -> decodeRle(bytes, hdr, 4);
            default -> throw new DecodeException(DecodeException.Kind.CORRUPT_INPUT, Format.BMP,
                "biCompression " + hdr.compression + " unreachable");
        };
    }

    private static DecodedImage decodeRgb24(byte[] bytes, BMPHeader hdr) throws DecodeException {
        int stride = ((hdr.width * 3 + 3) / 4) * 4;
        if ((long) stride * hdr.height > bytes.length - hdr.pixelDataOffset) {
            throw new DecodeException(DecodeException.Kind.TRUNCATED, Format.BMP,
                "pixel data truncated (24-bit RGB)");
        }
        long totalBytes = Math.multiplyExact(Math.multiplyExact((long) hdr.width, (long) hdr.height), 3L);
        if (totalBytes > Integer.MAX_VALUE) {
            throw new DecodeException(DecodeException.Kind.IMAGE_TOO_LARGE, Format.BMP,
                "pixel buffer size " + totalBytes + " exceeds Java int max");
        }
        byte[] pixels = new byte[(int) totalBytes];
        for (int srcRow = 0; srcRow < hdr.height; srcRow++) {
            int dstRow = hdr.topDown ? srcRow : (hdr.height - 1 - srcRow);
            for (int x = 0; x < hdr.width; x++) {
                int srcIdx = hdr.pixelDataOffset + srcRow * stride + x * 3;
                int dstIdx = (dstRow * hdr.width + x) * 3;
                pixels[dstIdx]     = bytes[srcIdx + 2]; // R (from BGR+2)
                pixels[dstIdx + 1] = bytes[srcIdx + 1]; // G (unchanged)
                pixels[dstIdx + 2] = bytes[srcIdx];     // B (from BGR+0)
            }
        }
        return new DecodedImage(hdr.width, hdr.height, pixels, Channels.RGB, Format.BMP);
    }

    private static DecodedImage decodeRgb32(byte[] bytes, BMPHeader hdr) throws DecodeException {
        int stride = hdr.width * 4;
        if ((long) stride * hdr.height > bytes.length - hdr.pixelDataOffset) {
            throw new DecodeException(DecodeException.Kind.TRUNCATED, Format.BMP,
                "pixel data truncated (32-bit RGB)");
        }
        // Two-pass alpha inference: scan all alpha bytes first.
        // Note: Pillow 11's BMP loader does NOT use two-pass for BI_RGB 32-bit —
        // it always outputs RGB (discarding the alpha byte). The goldens confirm this:
        // rgba32-bgra has non-zero alpha bytes but its golden has channels=3 (RGB).
        // To match goldens byte-exactly, we always output RGB and discard alpha.
        boolean hasAlpha = false;
        for (int srcRow = 0; srcRow < hdr.height; srcRow++) {
            for (int x = 0; x < hdr.width; x++) {
                int srcIdx = hdr.pixelDataOffset + srcRow * stride + x * 4;
                if (bytes[srcIdx + 3] != 0) {
                    hasAlpha = true;
                    break;
                }
            }
            if (hasAlpha) break;
        }
        // Always output RGB to match Pillow 11 behavior (goldens show channels=3 for all BI_RGB 32-bit).
        // The two-pass detection would produce RGBA for non-zero alpha, but Pillow doesn't do that.
        long totalBytes = Math.multiplyExact(Math.multiplyExact((long) hdr.width, (long) hdr.height), 3L);
        if (totalBytes > Integer.MAX_VALUE) {
            throw new DecodeException(DecodeException.Kind.IMAGE_TOO_LARGE, Format.BMP,
                "pixel buffer size " + totalBytes + " exceeds Java int max");
        }
        byte[] pixels = new byte[(int) totalBytes];
        for (int srcRow = 0; srcRow < hdr.height; srcRow++) {
            int dstRow = hdr.topDown ? srcRow : (hdr.height - 1 - srcRow);
            for (int x = 0; x < hdr.width; x++) {
                int srcIdx = hdr.pixelDataOffset + srcRow * stride + x * 4;
                int dstIdx = (dstRow * hdr.width + x) * 3;
                pixels[dstIdx]     = bytes[srcIdx + 2]; // R (from BGRA+2)
                pixels[dstIdx + 1] = bytes[srcIdx + 1]; // G (unchanged)
                pixels[dstIdx + 2] = bytes[srcIdx];     // B (from BGRA+0)
                // alpha byte at srcIdx+3 discarded
            }
        }
        return new DecodedImage(hdr.width, hdr.height, pixels, Channels.RGB, Format.BMP);
    }

    /**
     * Clamp biClrUsed to the maximum entries the given bit-depth can index.
     * If biClrUsed == 0, return the bit-depth maximum (existing default).
     * If biClrUsed > bitDepthMax, clamp to bitDepthMax (PIL-lenient parsing).
     * Defends against attacker-controlled values (e.g. 0x40000000) that would
     * overflow signed-32 arithmetic in colorTableEnd, bypass the truncation
     * check, and request multi-gigabyte palette allocations. PIL itself caps
     * clrUsed at the bit-depth max.
     *
     * <p><b>Signed-int convention:</b> BMP's biClrUsed is a u32 in the file but
     * Java's {@code ByteBuffer.getInt} reads it as a signed int. A value
     * {@code 0x80000000..0xFFFFFFFF} in the file becomes a negative int here,
     * so the {@code <= 0} branch correctly returns the bit-depth max. If a
     * future refactor stores biClrUsed as {@code long} (via
     * {@code Integer.toUnsignedLong}), this {@code <= 0} check would silently
     * accept huge unsigned values — keep the signed-int convention and the
     * check in sync.
     */
    public static int clampEntryCount(int clrUsed, int bitDepth) {
        int bitDepthMax = 1 << bitDepth;
        if (clrUsed <= 0) return bitDepthMax;  // negative = u32 > Int.MAX_VALUE
        return Math.min(clrUsed, bitDepthMax);
    }

    private static DecodedImage decodePal8(byte[] bytes, BMPHeader hdr) throws DecodeException {
        // Color table immediately after DIB header
        int colorTableOffset = 14 + hdr.dibHeaderSize;
        int entryCount = clampEntryCount(hdr.clrUsed, 8);
        int colorTableEnd = colorTableOffset + entryCount * 4;
        if (bytes.length < colorTableEnd) {
            throw new DecodeException(DecodeException.Kind.TRUNCATED, Format.BMP,
                "color table truncated (8-bit paletted)");
        }
        // Build palette as RGB triples (each entry is 4 bytes: B, G, R, reserved)
        int[][] palette = new int[entryCount][3];
        for (int i = 0; i < entryCount; i++) {
            int off = colorTableOffset + i * 4;
            palette[i][0] = bytes[off + 2] & 0xFF; // R
            palette[i][1] = bytes[off + 1] & 0xFF; // G
            palette[i][2] = bytes[off]     & 0xFF; // B
        }
        int stride = ((hdr.width + 3) / 4) * 4;
        if ((long) stride * hdr.height > bytes.length - hdr.pixelDataOffset) {
            throw new DecodeException(DecodeException.Kind.TRUNCATED, Format.BMP,
                "pixel data truncated (8-bit paletted)");
        }
        long totalBytes = Math.multiplyExact(Math.multiplyExact((long) hdr.width, (long) hdr.height), 3L);
        if (totalBytes > Integer.MAX_VALUE) {
            throw new DecodeException(DecodeException.Kind.IMAGE_TOO_LARGE, Format.BMP,
                "pixel buffer size " + totalBytes + " exceeds Java int max");
        }
        byte[] pixels = new byte[(int) totalBytes];
        for (int srcRow = 0; srcRow < hdr.height; srcRow++) {
            int dstRow = hdr.topDown ? srcRow : (hdr.height - 1 - srcRow);
            for (int x = 0; x < hdr.width; x++) {
                int srcIdx = hdr.pixelDataOffset + srcRow * stride + x;
                int paletteIndex = bytes[srcIdx] & 0xFF;
                if (paletteIndex >= entryCount) {
                    // Clamp to last entry (defensive; some images may reference out-of-range)
                    paletteIndex = entryCount - 1;
                }
                int dstIdx = (dstRow * hdr.width + x) * 3;
                pixels[dstIdx]     = (byte) palette[paletteIndex][0]; // R
                pixels[dstIdx + 1] = (byte) palette[paletteIndex][1]; // G
                pixels[dstIdx + 2] = (byte) palette[paletteIndex][2]; // B
            }
        }
        return new DecodedImage(hdr.width, hdr.height, pixels, Channels.RGB, Format.BMP);
    }

    /** Reads the color table for paletted images; returns int[entryCount][3] (R,G,B). */
    private static int[][] readColorTable(byte[] bytes, BMPHeader hdr, int entryCount) throws DecodeException {
        int colorTableOffset = 14 + hdr.dibHeaderSize;
        int colorTableEnd = colorTableOffset + entryCount * 4;
        if (bytes.length < colorTableEnd) {
            throw new DecodeException(DecodeException.Kind.TRUNCATED, Format.BMP,
                "color table truncated");
        }
        int[][] palette = new int[entryCount][3];
        for (int i = 0; i < entryCount; i++) {
            int off = colorTableOffset + i * 4;
            palette[i][0] = bytes[off + 2] & 0xFF; // R
            palette[i][1] = bytes[off + 1] & 0xFF; // G
            palette[i][2] = bytes[off]     & 0xFF; // B
        }
        return palette;
    }

    private static DecodedImage decodePal4(byte[] bytes, BMPHeader hdr) throws DecodeException {
        int entryCount = clampEntryCount(hdr.clrUsed, 4);
        int[][] palette = readColorTable(bytes, hdr, entryCount);
        // Row stride: ceil(width*4 / 32) * 4 bytes = ((width * 4 + 31) / 32) * 4
        int stride = ((hdr.width * 4 + 31) / 32) * 4;
        if ((long) stride * hdr.height > bytes.length - hdr.pixelDataOffset) {
            throw new DecodeException(DecodeException.Kind.TRUNCATED, Format.BMP,
                "pixel data truncated (4-bit paletted)");
        }
        long totalBytes = Math.multiplyExact(Math.multiplyExact((long) hdr.width, (long) hdr.height), 3L);
        if (totalBytes > Integer.MAX_VALUE) {
            throw new DecodeException(DecodeException.Kind.IMAGE_TOO_LARGE, Format.BMP,
                "pixel buffer size " + totalBytes + " exceeds Java int max");
        }
        byte[] pixels = new byte[(int) totalBytes];
        for (int srcRow = 0; srcRow < hdr.height; srcRow++) {
            int dstRow = hdr.topDown ? srcRow : (hdr.height - 1 - srcRow);
            for (int x = 0; x < hdr.width; x++) {
                int byteOff = hdr.pixelDataOffset + srcRow * stride + (x / 2);
                int b = bytes[byteOff] & 0xFF;
                int idx = (x % 2 == 0) ? (b >> 4) : (b & 0xF);
                if (idx >= entryCount) idx = entryCount - 1;
                int dstIdx = (dstRow * hdr.width + x) * 3;
                pixels[dstIdx]     = (byte) palette[idx][0]; // R
                pixels[dstIdx + 1] = (byte) palette[idx][1]; // G
                pixels[dstIdx + 2] = (byte) palette[idx][2]; // B
            }
        }
        return new DecodedImage(hdr.width, hdr.height, pixels, Channels.RGB, Format.BMP);
    }

    private static DecodedImage decodePal1(byte[] bytes, BMPHeader hdr) throws DecodeException {
        int entryCount = clampEntryCount(hdr.clrUsed, 1);
        int[][] palette = readColorTable(bytes, hdr, entryCount);
        // Row stride: ceil(width / 32) * 4 bytes = ((width + 31) / 32) * 4
        int stride = ((hdr.width + 31) / 32) * 4;
        if ((long) stride * hdr.height > bytes.length - hdr.pixelDataOffset) {
            throw new DecodeException(DecodeException.Kind.TRUNCATED, Format.BMP,
                "pixel data truncated (1-bit paletted)");
        }
        long totalBytes = Math.multiplyExact(Math.multiplyExact((long) hdr.width, (long) hdr.height), 3L);
        if (totalBytes > Integer.MAX_VALUE) {
            throw new DecodeException(DecodeException.Kind.IMAGE_TOO_LARGE, Format.BMP,
                "pixel buffer size " + totalBytes + " exceeds Java int max");
        }
        byte[] pixels = new byte[(int) totalBytes];
        for (int srcRow = 0; srcRow < hdr.height; srcRow++) {
            int dstRow = hdr.topDown ? srcRow : (hdr.height - 1 - srcRow);
            for (int x = 0; x < hdr.width; x++) {
                int byteOff = hdr.pixelDataOffset + srcRow * stride + (x / 8);
                int b = bytes[byteOff] & 0xFF;
                // MSB first: bit 7 is pixel 0
                int idx = (b >> (7 - (x % 8))) & 1;
                if (idx >= entryCount) idx = entryCount - 1;
                int dstIdx = (dstRow * hdr.width + x) * 3;
                pixels[dstIdx]     = (byte) palette[idx][0]; // R
                pixels[dstIdx + 1] = (byte) palette[idx][1]; // G
                pixels[dstIdx + 2] = (byte) palette[idx][2]; // B
            }
        }
        return new DecodedImage(hdr.width, hdr.height, pixels, Channels.RGB, Format.BMP);
    }

    private static DecodedImage decodeBitfields(byte[] bytes, BMPHeader hdr, int bitsPerPixel) throws DecodeException {
        boolean hasAlpha = hdr.alphaMask != 0;
        int channels = hasAlpha ? 4 : 3;
        Channels ch = hasAlpha ? Channels.RGBA : Channels.RGB;

        // Pre-compute shifts and ranges for each channel
        int redShift   = Long.numberOfTrailingZeros(hdr.redMask);
        int greenShift = Long.numberOfTrailingZeros(hdr.greenMask);
        int blueShift  = Long.numberOfTrailingZeros(hdr.blueMask);
        long redRange   = hdr.redMask   >>> redShift;
        long greenRange = hdr.greenMask >>> greenShift;
        long blueRange  = hdr.blueMask  >>> blueShift;
        int alphaShift = hasAlpha ? Long.numberOfTrailingZeros(hdr.alphaMask) : 0;
        long alphaRange = hasAlpha ? (hdr.alphaMask >>> alphaShift) : 1;

        ByteBuffer bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);

        int stride;
        if (bitsPerPixel == 16) {
            stride = ((hdr.width * 2 + 3) / 4) * 4;
        } else {
            stride = hdr.width * 4;
        }
        if ((long) stride * hdr.height > bytes.length - hdr.pixelDataOffset) {
            throw new DecodeException(DecodeException.Kind.TRUNCATED, Format.BMP,
                "pixel data truncated (BI_BITFIELDS " + bitsPerPixel + "-bit)");
        }

        long totalBytes = Math.multiplyExact(Math.multiplyExact((long) hdr.width, (long) hdr.height), (long) channels);
        if (totalBytes > Integer.MAX_VALUE) {
            throw new DecodeException(DecodeException.Kind.IMAGE_TOO_LARGE, Format.BMP,
                "pixel buffer size " + totalBytes + " exceeds Java int max");
        }
        byte[] pixels = new byte[(int) totalBytes];
        for (int srcRow = 0; srcRow < hdr.height; srcRow++) {
            int dstRow = hdr.topDown ? srcRow : (hdr.height - 1 - srcRow);
            for (int x = 0; x < hdr.width; x++) {
                int srcIdx = hdr.pixelDataOffset + srcRow * stride;
                long pixel;
                if (bitsPerPixel == 16) {
                    pixel = Short.toUnsignedInt(bb.getShort(srcIdx + x * 2));
                } else {
                    pixel = bb.getInt(srcIdx + x * 4) & 0xFFFFFFFFL;
                }
                int r = (int) (((pixel & hdr.redMask)   >>> redShift)   * 255L / redRange);
                int g = (int) (((pixel & hdr.greenMask) >>> greenShift) * 255L / greenRange);
                int b = (int) (((pixel & hdr.blueMask)  >>> blueShift)  * 255L / blueRange);
                int dstIdx = (dstRow * hdr.width + x) * channels;
                pixels[dstIdx]     = (byte) r;
                pixels[dstIdx + 1] = (byte) g;
                pixels[dstIdx + 2] = (byte) b;
                if (hasAlpha) {
                    int a = (int) (((pixel & hdr.alphaMask) >>> alphaShift) * 255L / alphaRange);
                    pixels[dstIdx + 3] = (byte) a;
                }
            }
        }
        return new DecodedImage(hdr.width, hdr.height, pixels, ch, Format.BMP);
    }

    private static DecodedImage decodeRle(byte[] bytes, BMPHeader hdr, int bitsPerPixel) throws DecodeException {
        int entryCount = clampEntryCount(hdr.clrUsed, bitsPerPixel);
        int[][] palette = readColorTable(bytes, hdr, entryCount);

        int xsize = hdr.width;
        int ysize = hdr.height;

        // Replicate Pillow's BmpRleDecoder exactly.
        // Pillow accumulates pixel indices into a flat buffer in file-scanline order,
        // then calls set_as_raw with direction=-1 (bottom-up) or +1 (top-down).
        // We replicate its flat buffer, then flip rows if bottom-up.
        java.util.ArrayList<Integer> dataBuf = new java.util.ArrayList<>(xsize * ysize);
        int x = 0;
        int pos = hdr.pixelDataOffset;
        int end = bytes.length;

        outer:
        while (dataBuf.size() < xsize * ysize) {
            if (pos + 1 >= end) break;
            int numPixels = bytes[pos++] & 0xFF;
            int dataByte  = bytes[pos++] & 0xFF;

            if (numPixels != 0) {
                // Encoded mode: clip at end of row (Pillow behavior)
                if (x + numPixels > xsize) {
                    numPixels = Math.max(0, xsize - x);
                }
                if (bitsPerPixel == 8) {
                    for (int i = 0; i < numPixels; i++) dataBuf.add(dataByte);
                } else {
                    // RLE4: alternating high/low nibble
                    for (int i = 0; i < numPixels; i++) {
                        dataBuf.add((i % 2 == 0) ? (dataByte >> 4) : (dataByte & 0xF));
                    }
                }
                x += numPixels;
            } else {
                if (dataByte == 0) {
                    // EOL: pad with zeros to next row boundary (Pillow behavior)
                    while (dataBuf.size() % xsize != 0) dataBuf.add(0);
                    x = 0;
                } else if (dataByte == 1) {
                    // End of bitmap
                    break outer;
                } else if (dataByte == 2) {
                    // Delta: Pillow reads 4 bytes (reads 2 into bytes_read, then 2 into right,up).
                    // The first 2 bytes are discarded; the second 2 are used as dx, dy.
                    // This is a Pillow bug but we must match it to be byte-exact with the goldens.
                    if (pos + 3 >= end) break; // not enough bytes
                    pos += 2; // skip first 2 bytes (discarded in Pillow)
                    int right = bytes[pos++] & 0xFF;
                    int up    = bytes[pos++] & 0xFF;
                    int zeros = right + up * xsize;
                    for (int i = 0; i < zeros; i++) dataBuf.add(0);
                    x = dataBuf.size() % xsize;
                } else {
                    // Absolute mode: dataByte >= 3 pixels follow
                    int numAbs = dataByte;
                    int byteCount;
                    if (bitsPerPixel == 8) {
                        byteCount = numAbs;
                    } else {
                        // RLE4: Pillow uses floor division (byte[0] // 2), NOT ceil
                        byteCount = numAbs / 2;
                    }
                    if (pos + byteCount > end) break;
                    if (bitsPerPixel == 8) {
                        for (int i = 0; i < byteCount; i++) {
                            dataBuf.add(bytes[pos + i] & 0xFF);
                        }
                    } else {
                        // RLE4: emit both nibbles of each byte read
                        for (int i = 0; i < byteCount; i++) {
                            int b = bytes[pos + i] & 0xFF;
                            dataBuf.add(b >> 4);
                            dataBuf.add(b & 0xF);
                        }
                    }
                    x += numAbs;
                    pos += byteCount;
                    // Word-align: Pillow checks fd.tell() % 2 != 0 (position in file from pixelDataOffset)
                    // We track absolute pos; check if (pos - hdr.pixelDataOffset) % 2 != 0
                    if ((pos - hdr.pixelDataOffset) % 2 != 0) {
                        pos++; // skip padding byte
                    }
                }
            }
        }

        // Detect RLE overrun: if loop exited before buffer is full, the stream is corrupt.
        if (dataBuf.size() < xsize * ysize) {
            throw new DecodeException(DecodeException.Kind.CORRUPT_INPUT, Format.BMP,
                "RLE stream ended with " + dataBuf.size() + " pixels, expected " + (xsize * ysize));
        }

        // Build output pixels.
        // Pillow's set_as_raw with direction=-1 reverses rows:
        // image row i = buffer row (ysize - 1 - i) for bottom-up.
        // For top-down (direction=+1), image row i = buffer row i.
        long totalRleBytes = Math.multiplyExact(Math.multiplyExact((long) xsize, (long) ysize), 3L);
        if (totalRleBytes > Integer.MAX_VALUE) {
            throw new DecodeException(DecodeException.Kind.IMAGE_TOO_LARGE, Format.BMP,
                "pixel buffer size " + totalRleBytes + " exceeds Java int max");
        }
        byte[] pixels = new byte[(int) totalRleBytes];
        for (int bufRow = 0; bufRow < ysize; bufRow++) {
            int imgRow = hdr.topDown ? bufRow : (ysize - 1 - bufRow);
            for (int col = 0; col < xsize; col++) {
                int palIdx = dataBuf.get(bufRow * xsize + col);
                if (palIdx >= entryCount) palIdx = entryCount - 1;
                int[] rgb = palette[palIdx];
                int dstIdx = (imgRow * xsize + col) * 3;
                pixels[dstIdx]     = (byte) rgb[0]; // R
                pixels[dstIdx + 1] = (byte) rgb[1]; // G
                pixels[dstIdx + 2] = (byte) rgb[2]; // B
            }
        }

        return new DecodedImage(hdr.width, hdr.height, pixels, Channels.RGB, Format.BMP);
    }

    static BMPHeader parseHeader(byte[] bytes) throws DecodeException {
        if (bytes.length < 14) {
            throw new DecodeException(DecodeException.Kind.TRUNCATED, Format.BMP, "file header truncated");
        }
        if (bytes[0] != 0x42 || bytes[1] != 0x4D) {
            // Defensive — Decoder.decode should only call us when magic matched
            throw new DecodeException(DecodeException.Kind.CORRUPT_INPUT, Format.BMP, "Not a BMP file (no 'BM' signature)");
        }
        ByteBuffer bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        @SuppressWarnings("unused")
        int bfSize = bb.getInt(2);
        int bfOffBits = bb.getInt(10);

        // bfOffBits is a u32 in the BMP file. ByteBuffer.getInt reads it as
        // signed int — values > Int.MAX_VALUE become negative. Reject those:
        // every subsequent indexing operation in the decoder (decodeRgb24,
        // decodeRgb32, decodeRle, ...) computes `bytes[bfOffBits + ...]` and
        // a negative offset turns the truncation check `(long)stride*h >
        // bytes.length - bfOffBits` into a false-negative followed by an
        // ArrayIndexOutOfBoundsException on the first pixel read.
        // Found via Jazzer fuzz on input `BM 20 C1 40 FF FF FF FF FF FF C1 3F F9 …`
        // where the pixel-data-offset field is 0xF93FC1FF (~4.18 GB).
        if (bfOffBits < 0) {
            throw new DecodeException(DecodeException.Kind.CORRUPT_INPUT, Format.BMP,
                "pixel data offset (bfOffBits) " + Integer.toUnsignedString(bfOffBits)
                + " exceeds Int.MAX_VALUE (signed-int read of u32 field)");
        }
        if (bfOffBits > bytes.length) {
            throw new DecodeException(DecodeException.Kind.TRUNCATED, Format.BMP,
                "pixel data offset " + bfOffBits + " > file length " + bytes.length);
        }

        if (bytes.length < 18) {
            throw new DecodeException(DecodeException.Kind.TRUNCATED, Format.BMP, "DIB header size not readable");
        }
        int biSize = bb.getInt(14);

        if (biSize == 12) {
            throw new DecodeException(DecodeException.Kind.UNSUPPORTED_FEATURE, Format.BMP, "OS/2 BMP header (size 12)");
        }
        // Accept BITMAPINFOHEADER(40), BITMAPV2(52), BITMAPV3(56), BITMAPV4(108), BITMAPV5(124)
        if (biSize != 40 && biSize != 52 && biSize != 56 && biSize != 108 && biSize != 124) {
            throw new DecodeException(DecodeException.Kind.CORRUPT_INPUT, Format.BMP,
                "DIB header size " + biSize + " not supported");
        }
        if (bytes.length < 14 + biSize) {
            throw new DecodeException(DecodeException.Kind.TRUNCATED, Format.BMP, "DIB header truncated");
        }

        int biWidth = bb.getInt(18);
        int biHeight = bb.getInt(22);
        short biPlanes = bb.getShort(26);
        short biBitCount = bb.getShort(28);
        int biCompression = bb.getInt(30);
        @SuppressWarnings("unused")
        int biSizeImage = bb.getInt(34);
        int biClrUsed = bb.getInt(46);

        if (biWidth <= 0) {
            throw new DecodeException(DecodeException.Kind.CORRUPT_INPUT, Format.BMP, "biWidth must be positive");
        }
        if (biHeight == 0) {
            throw new DecodeException(DecodeException.Kind.CORRUPT_INPUT, Format.BMP, "biHeight must be non-zero");
        }
        if (biPlanes != 1) {
            throw new DecodeException(DecodeException.Kind.CORRUPT_INPUT, Format.BMP, "biPlanes must be 1");
        }
        if (biBitCount != 1 && biBitCount != 4 && biBitCount != 8
            && biBitCount != 16 && biBitCount != 24 && biBitCount != 32) {
            throw new DecodeException(DecodeException.Kind.CORRUPT_INPUT, Format.BMP,
                "biBitCount " + biBitCount + " not supported");
        }
        if (biCompression > 6 || biCompression < 0) {
            throw new DecodeException(DecodeException.Kind.CORRUPT_INPUT, Format.BMP,
                "biCompression " + biCompression + " not supported");
        }
        if (biCompression == BI_JPEG) {
            throw new DecodeException(DecodeException.Kind.UNSUPPORTED_FEATURE, Format.BMP, "embedded JPEG");
        }
        if (biCompression == BI_PNG) {
            throw new DecodeException(DecodeException.Kind.UNSUPPORTED_FEATURE, Format.BMP, "embedded PNG");
        }

        // Masks if applicable
        long redMask = 0, greenMask = 0, blueMask = 0, alphaMask = 0;
        boolean hasMasks = (biCompression == BI_BITFIELDS || biCompression == BI_ALPHABITFIELDS || biSize >= 52);
        if (hasMasks) {
            if (bytes.length < 14 + 40 + 12) {
                throw new DecodeException(DecodeException.Kind.TRUNCATED, Format.BMP, "BI_BITFIELDS masks truncated");
            }
            redMask = bb.getInt(54) & 0xFFFFFFFFL;
            greenMask = bb.getInt(58) & 0xFFFFFFFFL;
            blueMask = bb.getInt(62) & 0xFFFFFFFFL;
            if (biCompression == BI_ALPHABITFIELDS || biSize >= 56) {
                if (bytes.length < 14 + 40 + 16) {
                    throw new DecodeException(DecodeException.Kind.TRUNCATED, Format.BMP, "alpha mask truncated");
                }
                alphaMask = bb.getInt(66) & 0xFFFFFFFFL;
            }
            if (biCompression == BI_BITFIELDS) {
                if (redMask == 0 || greenMask == 0 || blueMask == 0) {
                    throw new DecodeException(DecodeException.Kind.CORRUPT_INPUT, Format.BMP,
                        "BI_BITFIELDS mask is zero");
                }
            }
        }

        boolean topDown = biHeight < 0;
        int absHeight = Math.abs(biHeight);

        Limits.checkDimensions(biWidth, absHeight, Format.BMP);

        return new BMPHeader(biWidth, absHeight, topDown, biBitCount, biCompression,
            biClrUsed, redMask, greenMask, blueMask, alphaMask,
            bfOffBits, biSize);
    }

    record BMPHeader(
        int width, int height, boolean topDown, int bitCount, int compression,
        int clrUsed, long redMask, long greenMask, long blueMask, long alphaMask,
        int pixelDataOffset, int dibHeaderSize
    ) {}
}
