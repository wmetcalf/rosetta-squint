package io.github.wmetcalf.rosettasquint;

import io.github.wmetcalf.rosettasquint.decode.Channels;
import io.github.wmetcalf.rosettasquint.decode.DecodedImage;
import io.github.wmetcalf.rosettasquint.decode.Decoder;
import io.github.wmetcalf.rosettasquint.hash.AverageHash;
import io.github.wmetcalf.rosettasquint.hash.ColorHash;
import io.github.wmetcalf.rosettasquint.hash.CropResistantHash;
import io.github.wmetcalf.rosettasquint.hash.DHash;
import io.github.wmetcalf.rosettasquint.hash.DHashVertical;
import io.github.wmetcalf.rosettasquint.hash.ImageHash;
import io.github.wmetcalf.rosettasquint.hash.ImageMultiHash;
import io.github.wmetcalf.rosettasquint.hash.PHash;
import io.github.wmetcalf.rosettasquint.hash.PHashSimple;
import io.github.wmetcalf.rosettasquint.hash.WHashDb4;
import io.github.wmetcalf.rosettasquint.hash.WHashDb4Robust;
import io.github.wmetcalf.rosettasquint.hash.WHashHaar;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Convenience façade that chains rosetta-squint-decode + rosetta-squint-hash
 * into single-call methods.
 *
 * <p>Usage:
 * <pre>
 *   ImageHash h = Squint.phash("photo.jpg", 8);
 *   ImageHash h = Squint.phashBytes(jpegBytes, 8);
 *   System.out.println(h); // e.g. "c3f8a1b27d0e4f96"
 * </pre>
 */
public final class Squint {
    private Squint() {}

    /**
     * Maximum allowed size for path-based decode inputs. Refuse anything
     * larger BEFORE reading bytes. Callers that genuinely need to process
     * images larger than this should decode via rosetta-squint-decode
     * directly after explicit validation.
     */
    public static final long MAX_FILE_SIZE = 256L * 1024L * 1024L; // 256 MiB

    // ------------------------------------------------------------------ //
    // Decode helpers                                                       //
    // ------------------------------------------------------------------ //

    /**
     * Convert a {@link DecodedImage} (raw RGB or RGBA pixel bytes from
     * rosetta-squint-decode) to a {@link BufferedImage} suitable for the
     * hash algorithms.
     */
    public static BufferedImage decodedToBufferedImage(DecodedImage d) {
        int type = d.channels() == Channels.RGBA
                ? BufferedImage.TYPE_INT_ARGB
                : BufferedImage.TYPE_INT_RGB;
        BufferedImage img = new BufferedImage(d.width(), d.height(), type);
        byte[] data = d.unsafeData();
        int bpp = d.channels() == Channels.RGBA ? 4 : 3;
        for (int y = 0; y < d.height(); y++) {
            for (int x = 0; x < d.width(); x++) {
                int i = (y * d.width() + x) * bpp;
                int r = data[i]     & 0xFF;
                int g = data[i + 1] & 0xFF;
                int b = data[i + 2] & 0xFF;
                int argb;
                if (bpp == 4) {
                    int a = data[i + 3] & 0xFF;
                    argb = (a << 24) | (r << 16) | (g << 8) | b;
                } else {
                    argb = 0xFF000000 | (r << 16) | (g << 8) | b;
                }
                img.setRGB(x, y, argb);
            }
        }
        return img;
    }

    /**
     * Decode an image file at {@code path} to a {@link BufferedImage}.
     *
     * <p>Refuses symlinks (via {@link Files#isSymbolicLink}), non-regular
     * files (FIFOs, {@code /dev/zero}, character devices, etc.) and files
     * larger than {@link #MAX_FILE_SIZE} BEFORE reading bytes — without
     * these guards {@code Files.readAllBytes(/dev/zero)} would loop until
     * OOM and a 300 MiB sparse file would allocate 300 MiB even though it
     * contains no image. Callers who genuinely want symlink resolution
     * must do it explicitly (for example via {@link Path#toRealPath}) before
     * calling this method.
     *
     * <p>The symlink check uses {@code isSymbolicLink} (which does not
     * follow), then the file is opened via {@link FileChannel#open} with
     * {@link java.nio.file.LinkOption#NOFOLLOW_LINKS} so that a symlink
     * swapped in between the lstat and the open still causes the open to
     * fail rather than silently resolving. Subsequent size and read
     * operations work against the open channel ({@code channel.size()},
     * {@code channel.read()}) rather than the path, closing the TOCTOU
     * window between size check and read. The read is bounded by
     * {@code MAX_FILE_SIZE + 1} so a concurrent writer that grows the
     * file after the size check is still rejected.
     */
    public static BufferedImage decodeFile(String path) throws Exception {
        Path p = Path.of(path);
        if (Files.isSymbolicLink(p)) {
            throw new IllegalArgumentException("symlink not allowed: " + path);
        }
        if (!Files.isRegularFile(p, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            throw new RuntimeException("not a regular file: " + path);
        }
        try (FileChannel ch = FileChannel.open(
                p, StandardOpenOption.READ, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            long size = ch.size();
            if (size > MAX_FILE_SIZE) {
                throw new RuntimeException(
                    "input file too large: " + size + " bytes (max "
                        + MAX_FILE_SIZE + " bytes / 256 MiB). For images above "
                        + "this threshold, decode via rosetta-squint-decode "
                        + "directly after explicit validation.");
            }
            // Allocate to MAX_FILE_SIZE+1 so a concurrent writer that grows
            // the file post-stat is detected (size > MAX_FILE_SIZE → reject).
            // Cast is safe because size <= MAX_FILE_SIZE which fits in int.
            int alloc = (int) Math.min(size + 1, MAX_FILE_SIZE + 1);
            ByteBuffer buf = ByteBuffer.allocate(alloc);
            while (buf.hasRemaining()) {
                int n = ch.read(buf);
                if (n < 0) {
                    break;
                }
            }
            if (buf.position() > MAX_FILE_SIZE) {
                throw new RuntimeException(
                    "input file too large: " + buf.position() + " bytes (max "
                        + MAX_FILE_SIZE + " bytes / 256 MiB). For images above "
                        + "this threshold, decode via rosetta-squint-decode "
                        + "directly after explicit validation.");
            }
            byte[] bytes = new byte[buf.position()];
            buf.flip();
            buf.get(bytes);
            return decodeBytes(bytes);
        }
    }

    /** Decode raw image bytes to a {@link BufferedImage}. */
    public static BufferedImage decodeBytes(byte[] bytes) throws Exception {
        DecodedImage d = Decoder.decode(bytes);
        return decodedToBufferedImage(d);
    }

    // ------------------------------------------------------------------ //
    // phash                                                                //
    // ------------------------------------------------------------------ //

    public static ImageHash phash(String path, int hashSize) throws Exception {
        return PHash.compute(decodeFile(path), hashSize);
    }

    public static ImageHash phashBytes(byte[] bytes, int hashSize) throws Exception {
        return PHash.compute(decodeBytes(bytes), hashSize);
    }

    // ------------------------------------------------------------------ //
    // phash_simple                                                         //
    // ------------------------------------------------------------------ //

    public static ImageHash phashSimple(String path, int hashSize) throws Exception {
        return PHashSimple.compute(decodeFile(path), hashSize);
    }

    public static ImageHash phashSimpleBytes(byte[] bytes, int hashSize) throws Exception {
        return PHashSimple.compute(decodeBytes(bytes), hashSize);
    }

    // ------------------------------------------------------------------ //
    // average_hash                                                         //
    // ------------------------------------------------------------------ //

    public static ImageHash averageHash(String path, int hashSize) throws Exception {
        return AverageHash.compute(decodeFile(path), hashSize);
    }

    public static ImageHash averageHashBytes(byte[] bytes, int hashSize) throws Exception {
        return AverageHash.compute(decodeBytes(bytes), hashSize);
    }

    // ------------------------------------------------------------------ //
    // dhash                                                                //
    // ------------------------------------------------------------------ //

    public static ImageHash dhash(String path, int hashSize) throws Exception {
        return DHash.compute(decodeFile(path), hashSize);
    }

    public static ImageHash dhashBytes(byte[] bytes, int hashSize) throws Exception {
        return DHash.compute(decodeBytes(bytes), hashSize);
    }

    // ------------------------------------------------------------------ //
    // dhash_vertical                                                       //
    // ------------------------------------------------------------------ //

    public static ImageHash dhashVertical(String path, int hashSize) throws Exception {
        return DHashVertical.compute(decodeFile(path), hashSize);
    }

    public static ImageHash dhashVerticalBytes(byte[] bytes, int hashSize) throws Exception {
        return DHashVertical.compute(decodeBytes(bytes), hashSize);
    }

    // ------------------------------------------------------------------ //
    // whash (Haar)                                                         //
    // ------------------------------------------------------------------ //

    public static ImageHash whashHaar(String path, int hashSize) throws Exception {
        return WHashHaar.compute(decodeFile(path), hashSize);
    }

    public static ImageHash whashHaarBytes(byte[] bytes, int hashSize) throws Exception {
        return WHashHaar.compute(decodeBytes(bytes), hashSize);
    }

    // ------------------------------------------------------------------ //
    // whash (db4)                                                          //
    // ------------------------------------------------------------------ //

    public static ImageHash whashDb4(String path, int hashSize) throws Exception {
        return WHashDb4.compute(decodeFile(path), hashSize);
    }

    public static ImageHash whashDb4Bytes(byte[] bytes, int hashSize) throws Exception {
        return WHashDb4.compute(decodeBytes(bytes), hashSize);
    }

    // ------------------------------------------------------------------ //
    // whash (db4 robust)                                                   //
    // ------------------------------------------------------------------ //

    public static ImageHash whashDb4Robust(String path, int hashSize) throws Exception {
        return WHashDb4Robust.compute(decodeFile(path), hashSize);
    }

    public static ImageHash whashDb4RobustBytes(byte[] bytes, int hashSize) throws Exception {
        return WHashDb4Robust.compute(decodeBytes(bytes), hashSize);
    }

    // ------------------------------------------------------------------ //
    // colorhash                                                            //
    // ------------------------------------------------------------------ //

    /** colorhash with default binbits=3. */
    public static ImageHash colorhash(String path) throws Exception {
        return ColorHash.compute(decodeFile(path));
    }

    public static ImageHash colorhash(String path, int binbits) throws Exception {
        return ColorHash.compute(decodeFile(path), binbits);
    }

    /** colorhash with default binbits=3. */
    public static ImageHash colorhashBytes(byte[] bytes) throws Exception {
        return ColorHash.compute(decodeBytes(bytes));
    }

    public static ImageHash colorhashBytes(byte[] bytes, int binbits) throws Exception {
        return ColorHash.compute(decodeBytes(bytes), binbits);
    }

    // ------------------------------------------------------------------ //
    // crop_resistant_hash                                                  //
    // ------------------------------------------------------------------ //

    public static ImageMultiHash cropResistantHash(String path) throws Exception {
        return CropResistantHash.compute(decodeFile(path));
    }

    public static ImageMultiHash cropResistantHash(String path, Integer limitSegments) throws Exception {
        return CropResistantHash.compute(decodeFile(path), limitSegments);
    }

    public static ImageMultiHash cropResistantHashBytes(byte[] bytes) throws Exception {
        return CropResistantHash.compute(decodeBytes(bytes));
    }

    public static ImageMultiHash cropResistantHashBytes(byte[] bytes, Integer limitSegments) throws Exception {
        return CropResistantHash.compute(decodeBytes(bytes), limitSegments);
    }
}
