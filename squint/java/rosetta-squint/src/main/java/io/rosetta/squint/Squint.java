package io.rosetta.squint;

import io.rosetta.imagedecode.Channels;
import io.rosetta.imagedecode.DecodedImage;
import io.rosetta.imagedecode.Decoder;
import io.rosetta.imagehash.AverageHash;
import io.rosetta.imagehash.ColorHash;
import io.rosetta.imagehash.CropResistantHash;
import io.rosetta.imagehash.DHash;
import io.rosetta.imagehash.DHashVertical;
import io.rosetta.imagehash.ImageHash;
import io.rosetta.imagehash.ImageMultiHash;
import io.rosetta.imagehash.PHash;
import io.rosetta.imagehash.PHashSimple;
import io.rosetta.imagehash.WHashDb4;
import io.rosetta.imagehash.WHashDb4Robust;
import io.rosetta.imagehash.WHashHaar;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Convenience façade that chains rosetta-image-decode + rosetta-image-hash
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

    // ------------------------------------------------------------------ //
    // Decode helpers                                                       //
    // ------------------------------------------------------------------ //

    /**
     * Convert a {@link DecodedImage} (raw RGB or RGBA pixel bytes from
     * rosetta-image-decode) to a {@link BufferedImage} suitable for the
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

    /** Decode an image file at {@code path} to a {@link BufferedImage}. */
    public static BufferedImage decodeFile(String path) throws Exception {
        byte[] bytes = Files.readAllBytes(Path.of(path));
        return decodeBytes(bytes);
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
