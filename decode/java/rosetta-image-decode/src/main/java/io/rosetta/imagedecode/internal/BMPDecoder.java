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
                case 1, 4 -> throw new DecodeException(DecodeException.Kind.UNSUPPORTED_FEATURE, Format.BMP,
                    "1/4-bit paletted (Tier 2; implemented in next task)");
                case 16 -> throw new DecodeException(DecodeException.Kind.UNSUPPORTED_FEATURE, Format.BMP,
                    "BI_RGB 16-bit not supported (use BI_BITFIELDS)");
                default -> throw new DecodeException(DecodeException.Kind.CORRUPT_INPUT, Format.BMP,
                    "biBitCount " + hdr.bitCount + " for BI_RGB not supported");
            };
            case BI_RLE8, BI_RLE4 -> throw new DecodeException(DecodeException.Kind.UNSUPPORTED_FEATURE, Format.BMP,
                "RLE compression (Tier 3; implemented in next task)");
            case BI_BITFIELDS, BI_ALPHABITFIELDS -> throw new DecodeException(DecodeException.Kind.UNSUPPORTED_FEATURE, Format.BMP,
                "BI_BITFIELDS (Tier 2; implemented in next task)");
            default -> throw new DecodeException(DecodeException.Kind.CORRUPT_INPUT, Format.BMP,
                "biCompression " + hdr.compression + " unreachable");
        };
    }

    private static DecodedImage decodeRgb24(byte[] bytes, BMPHeader hdr) throws DecodeException {
        int stride = ((hdr.width * 3 + 3) / 4) * 4;
        if (bytes.length - hdr.pixelDataOffset < stride * hdr.height) {
            throw new DecodeException(DecodeException.Kind.TRUNCATED, Format.BMP,
                "pixel data truncated (24-bit RGB)");
        }
        byte[] pixels = new byte[hdr.width * hdr.height * 3];
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
        if (bytes.length - hdr.pixelDataOffset < stride * hdr.height) {
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
        byte[] pixels = new byte[hdr.width * hdr.height * 3];
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

    private static DecodedImage decodePal8(byte[] bytes, BMPHeader hdr) throws DecodeException {
        // Color table immediately after DIB header
        int colorTableOffset = 14 + hdr.dibHeaderSize;
        int entryCount = hdr.clrUsed > 0 ? hdr.clrUsed : 256;
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
        if (bytes.length - hdr.pixelDataOffset < stride * hdr.height) {
            throw new DecodeException(DecodeException.Kind.TRUNCATED, Format.BMP,
                "pixel data truncated (8-bit paletted)");
        }
        byte[] pixels = new byte[hdr.width * hdr.height * 3];
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

        if (bytes.length < 18) {
            throw new DecodeException(DecodeException.Kind.TRUNCATED, Format.BMP, "DIB header size not readable");
        }
        int biSize = bb.getInt(14);

        if (biSize == 12) {
            throw new DecodeException(DecodeException.Kind.UNSUPPORTED_FEATURE, Format.BMP, "OS/2 BMP header (size 12)");
        }
        if (biSize != 40 && biSize != 108 && biSize != 124) {
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
