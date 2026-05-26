package io.github.wmetcalf.rosettasquint.decode.internal;

import io.github.wmetcalf.rosettasquint.decode.Channels;
import io.github.wmetcalf.rosettasquint.decode.DecodeException;
import io.github.wmetcalf.rosettasquint.decode.DecodedImage;
import io.github.wmetcalf.rosettasquint.decode.Format;

/**
 * In-tree pure-Java GIF89a/87a decoder. First frame only.
 *
 * <p>Ported from {@code decode/js/rosetta-squint-decode/src/internal/gif-decoder.ts},
 * which is itself a port of the Swift implementation. Replaces the previous
 * {@code javax.imageio} (GIFImageReader) path: that decoder is over-permissive
 * with malformed LZW streams (fills out-of-range codes with the last valid color)
 * and was the source of cross-port tolerance drift documented in SPEC §3.2.
 *
 * <p>This decoder strictly rejects out-of-range LZW codes with
 * {@link DecodeException.Kind#CORRUPT_INPUT}.
 */
public final class GIFDecoder {
    private GIFDecoder() {}

    public static DecodedImage decode(byte[] bytes) throws DecodeException {
        // Sniff the canvas dimensions from the Logical Screen Descriptor at
        // offsets 6..10 BEFORE running the full parse, so the MAX_PIXELS guard
        // fires before any pixel allocation. LSD stores width / height as
        // little-endian u16. Spec §3.1 requires this ordering.
        if (bytes != null && bytes.length >= 10) {
            int gifWidth = (bytes[6] & 0xFF) | ((bytes[7] & 0xFF) << 8);
            int gifHeight = (bytes[8] & 0xFF) | ((bytes[9] & 0xFF) << 8);
            Limits.checkDimensions(gifWidth, gifHeight, Format.GIF);
        }

        if (bytes == null) {
            throw new DecodeException(DecodeException.Kind.CORRUPT_INPUT, Format.GIF, "null input");
        }

        GIFParser parser = new GIFParser(bytes);

        parser.parseHeader();
        parser.parseLogicalScreenDescriptor();

        if (parser.hasGlobalColorTable) {
            parser.parseGlobalColorTable();
        }

        // Walk blocks until first Image Descriptor.
        GIFParser.TidxBox tidxBox = new GIFParser.TidxBox();

        while (true) {
            int intro = parser.readByte();
            if (intro < 0) {
                throw new DecodeException(DecodeException.Kind.CORRUPT_INPUT, Format.GIF,
                    "unexpected EOF before image descriptor");
            }
            if (intro == 0x21) {
                // Extension introducer
                parser.skipOrParseExtension(tidxBox);
            } else if (intro == 0x2C) {
                // Image Descriptor
                GIFParser.FrameInfo frame = parser.parseImageDescriptor();

                // D-M2 per-frame dim check: the image descriptor declares frame
                // dims independently of the LSD; without this guard a 16x16 LSD
                // could carry a 65535x65535 frame and drive ~4 GB of allocation.
                // Run MAX_PIXELS first so a decompression-bomb frame is flagged
                // as imageTooLarge rather than shadowed by the canvas-extent check.
                Limits.checkDimensions(frame.width, frame.height, Format.GIF);
                if (frame.left + frame.width > parser.lsdWidth
                    || frame.top + frame.height > parser.lsdHeight) {
                    throw new DecodeException(DecodeException.Kind.CORRUPT_INPUT, Format.GIF,
                        "frame " + frame.width + "x" + frame.height
                            + " at (" + frame.left + "," + frame.top + ") extends beyond canvas "
                            + parser.lsdWidth + "x" + parser.lsdHeight);
                }

                byte[] palette;
                if (frame.localColorTable != null) {
                    palette = frame.localColorTable;
                } else if (parser.globalColorTable != null) {
                    palette = parser.globalColorTable;
                } else {
                    throw new DecodeException(DecodeException.Kind.CORRUPT_INPUT, Format.GIF,
                        "no color table available");
                }

                byte[] indices = parser.decodeLZWImage(frame.width, frame.height);
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
                    parser.bgIndex
                );
            } else if (intro == 0x3B) {
                // Trailer
                throw new DecodeException(DecodeException.Kind.CORRUPT_INPUT, Format.GIF,
                    "GIF trailer reached before any image");
            } else {
                throw new DecodeException(DecodeException.Kind.CORRUPT_INPUT, Format.GIF,
                    String.format("unknown block introducer: 0x%02x", intro));
            }
        }
    }

    // ---------------------------------------------------------------------------
    // Frame emission — compose canvas, write frame pixels, handle transparency.
    // Mirrors the JS emitFrame() function exactly.
    // ---------------------------------------------------------------------------

    private static DecodedImage emitFrame(
            int canvasWidth,
            int canvasHeight,
            int frameLeft,
            int frameTop,
            int frameWidth,
            int frameHeight,
            byte[] indices,
            byte[] palette,
            Integer transparentIndex,
            int bgIndex) throws DecodeException {
        boolean hasAlpha = transparentIndex != null;
        int bpp = hasAlpha ? 4 : 3;
        int paletteEntries = palette.length / 3;

        long outSize = (long) canvasWidth * (long) canvasHeight * (long) bpp;
        if (outSize > Integer.MAX_VALUE) {
            throw new DecodeException(DecodeException.Kind.IMAGE_TOO_LARGE, Format.GIF,
                "pixel buffer size " + outSize + " exceeds Java int max");
        }
        byte[] data = new byte[(int) outSize];

        // Fill canvas background.
        byte bgR = 0, bgG = 0, bgB = 0;
        if (bgIndex < paletteEntries) {
            bgR = palette[bgIndex * 3];
            bgG = palette[bgIndex * 3 + 1];
            bgB = palette[bgIndex * 3 + 2];
        }
        int total = canvasWidth * canvasHeight;
        if (!hasAlpha) {
            for (int i = 0; i < total; i++) {
                data[i * 3]     = bgR;
                data[i * 3 + 1] = bgG;
                data[i * 3 + 2] = bgB;
            }
        } else {
            // For RGBA canvas, background pixels are fully opaque bg color.
            for (int i = 0; i < total; i++) {
                data[i * 4]     = bgR;
                data[i * 4 + 1] = bgG;
                data[i * 4 + 2] = bgB;
                data[i * 4 + 3] = (byte) 255;
            }
        }

        // Write frame pixels into canvas.
        int tidxValue = hasAlpha ? transparentIndex : -1;
        for (int fy = 0; fy < frameHeight; fy++) {
            int cy = frameTop + fy;
            if (cy < 0 || cy >= canvasHeight) continue;
            for (int fx = 0; fx < frameWidth; fx++) {
                int cx = frameLeft + fx;
                if (cx < 0 || cx >= canvasWidth) continue;
                int srcIdx = fy * frameWidth + fx;
                if (srcIdx >= indices.length) continue;
                int palIdx = indices[srcIdx] & 0xFF;
                byte r = 0, g = 0, b = 0;
                if (palIdx < paletteEntries) {
                    r = palette[palIdx * 3];
                    g = palette[palIdx * 3 + 1];
                    b = palette[palIdx * 3 + 2];
                }
                int dstBase = (cy * canvasWidth + cx) * bpp;
                data[dstBase]     = r;
                data[dstBase + 1] = g;
                data[dstBase + 2] = b;
                if (hasAlpha) {
                    // Transparent index: alpha=0 but RGB from palette (PIL preserves palette RGB).
                    data[dstBase + 3] = (palIdx == tidxValue) ? 0 : (byte) 255;
                }
            }
        }

        Channels channels = hasAlpha ? Channels.RGBA : Channels.RGB;
        return new DecodedImage(canvasWidth, canvasHeight, data, channels, Format.GIF);
    }

    // ---------------------------------------------------------------------------
    // Interlace de-interleaving — 4 passes:
    //   Pass 1: rows 0, 8, 16, ...  (step 8, start 0)
    //   Pass 2: rows 4, 12, 20, ... (step 8, start 4)
    //   Pass 3: rows 2, 6, 10, ...  (step 4, start 2)
    //   Pass 4: rows 1, 3, 5, ...   (step 2, start 1)
    // ---------------------------------------------------------------------------

    private static byte[] deinterlace(byte[] indices, int width, int height) {
        int[] interlacedRows = new int[height];
        int n = 0;
        int[][] passes = {{0, 8}, {4, 8}, {2, 4}, {1, 2}};
        for (int[] pass : passes) {
            int start = pass[0], step = pass[1];
            for (int row = start; row < height; row += step) {
                interlacedRows[n++] = row;
            }
        }

        byte[] result = new byte[width * height];
        for (int srcRow = 0; srcRow < n; srcRow++) {
            int dstRow = interlacedRows[srcRow];
            int srcBase = srcRow * width;
            int dstBase = dstRow * width;
            for (int col = 0; col < width; col++) {
                if (srcBase + col < indices.length) {
                    result[dstBase + col] = indices[srcBase + col];
                }
            }
        }
        return result;
    }
}
