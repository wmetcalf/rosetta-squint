package io.rosetta.imagedecode.internal;

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
        // Pixel decoding lands in Tasks 9 + 10.
        throw new DecodeException(DecodeException.Kind.UNSUPPORTED_FEATURE, Format.BMP,
            "pixel decode not yet implemented (header parsed: " + hdr.width + "x" + hdr.height + " bpp=" + hdr.bitCount + " comp=" + hdr.compression + ")");
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
