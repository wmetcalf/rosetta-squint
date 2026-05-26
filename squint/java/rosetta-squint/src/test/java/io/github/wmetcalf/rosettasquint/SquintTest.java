package io.github.wmetcalf.rosettasquint;

import io.github.wmetcalf.rosettasquint.hash.AverageHash;
import io.github.wmetcalf.rosettasquint.hash.ColorHash;
import io.github.wmetcalf.rosettasquint.hash.DHash;
import io.github.wmetcalf.rosettasquint.hash.DHashVertical;
import io.github.wmetcalf.rosettasquint.hash.ImageHash;
import io.github.wmetcalf.rosettasquint.hash.ImageMultiHash;
import io.github.wmetcalf.rosettasquint.hash.PHash;
import io.github.wmetcalf.rosettasquint.hash.PHashSimple;
import io.github.wmetcalf.rosettasquint.hash.WHashDb4;
import io.github.wmetcalf.rosettasquint.hash.WHashDb4Robust;
import io.github.wmetcalf.rosettasquint.hash.WHashHaar;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Integration tests for the Squint convenience API.
 *
 * <p>For each algorithm and fixture, verifies:
 * <ol>
 *   <li>The result is non-null.</li>
 *   <li>Squint.xHash(path) == Squint.xHashBytes(readBytes(path))  — path vs bytes parity.</li>
 *   <li>The result equals calling the underlying algorithm directly on Squint.decodeFile(path)
 *       — chain consistency.</li>
 * </ol>
 *
 * <p>Fixtures resolved relative to {@code decode/spec/fixtures/} in the repo root.
 */
class SquintTest {

    /** Absolute path to decode/spec/fixtures (resolved from the squint/java module dir). */
    private static final Path FIXTURES = Paths.get("..", "..", "..", "decode", "spec", "fixtures")
            .toAbsolutePath().normalize();

    private static Path png(String name) {
        return FIXTURES.resolve("png/valid/" + name);
    }

    private static Path jpg(String name) {
        return FIXTURES.resolve("jpeg/valid/" + name);
    }

    private static Path bmp(String name) {
        return FIXTURES.resolve("bmp/valid/" + name);
    }

    // ------------------------------------------------------------------ //
    // phash                                                                //
    // ------------------------------------------------------------------ //

    @Test
    void phash_imagehash_png() throws Exception {
        String path = png("imagehash.png").toString();
        ImageHash h = Squint.phash(path, 8);
        assertNotNull(h);
        assertEquals(h, Squint.phashBytes(Files.readAllBytes(Path.of(path)), 8));
        assertEquals(h, PHash.compute(Squint.decodeFile(path), 8));
    }

    @Test
    void phash_gradient_png() throws Exception {
        String path = png("gradient-h-256.png").toString();
        ImageHash h = Squint.phash(path, 8);
        assertNotNull(h);
        assertEquals(h, Squint.phashBytes(Files.readAllBytes(Path.of(path)), 8));
        assertEquals(h, PHash.compute(Squint.decodeFile(path), 8));
    }

    @Test
    void phash_jpeg() throws Exception {
        String path = jpg("32x32-quality-95.jpg").toString();
        ImageHash h = Squint.phash(path, 8);
        assertNotNull(h);
        assertEquals(h, Squint.phashBytes(Files.readAllBytes(Path.of(path)), 8));
        assertEquals(h, PHash.compute(Squint.decodeFile(path), 8));
    }

    // ------------------------------------------------------------------ //
    // phash_simple                                                         //
    // ------------------------------------------------------------------ //

    @Test
    void phashSimple_imagehash_png() throws Exception {
        String path = png("imagehash.png").toString();
        ImageHash h = Squint.phashSimple(path, 8);
        assertNotNull(h);
        assertEquals(h, Squint.phashSimpleBytes(Files.readAllBytes(Path.of(path)), 8));
        assertEquals(h, PHashSimple.compute(Squint.decodeFile(path), 8));
    }

    @Test
    void phashSimple_gradient_png() throws Exception {
        String path = png("gradient-h-256.png").toString();
        ImageHash h = Squint.phashSimple(path, 8);
        assertNotNull(h);
        assertEquals(h, Squint.phashSimpleBytes(Files.readAllBytes(Path.of(path)), 8));
        assertEquals(h, PHashSimple.compute(Squint.decodeFile(path), 8));
    }

    @Test
    void phashSimple_bmp() throws Exception {
        String path = bmp("rgb24-large.bmp").toString();
        ImageHash h = Squint.phashSimple(path, 8);
        assertNotNull(h);
        assertEquals(h, Squint.phashSimpleBytes(Files.readAllBytes(Path.of(path)), 8));
        assertEquals(h, PHashSimple.compute(Squint.decodeFile(path), 8));
    }

    // ------------------------------------------------------------------ //
    // average_hash                                                         //
    // ------------------------------------------------------------------ //

    @Test
    void averageHash_imagehash_png() throws Exception {
        String path = png("imagehash.png").toString();
        ImageHash h = Squint.averageHash(path, 8);
        assertNotNull(h);
        assertEquals(h, Squint.averageHashBytes(Files.readAllBytes(Path.of(path)), 8));
        assertEquals(h, AverageHash.compute(Squint.decodeFile(path), 8));
    }

    @Test
    void averageHash_checker_png() throws Exception {
        String path = png("checker-256.png").toString();
        ImageHash h = Squint.averageHash(path, 8);
        assertNotNull(h);
        assertEquals(h, Squint.averageHashBytes(Files.readAllBytes(Path.of(path)), 8));
        assertEquals(h, AverageHash.compute(Squint.decodeFile(path), 8));
    }

    @Test
    void averageHash_jpeg() throws Exception {
        String path = jpg("64x64-quality-50.jpg").toString();
        ImageHash h = Squint.averageHash(path, 8);
        assertNotNull(h);
        assertEquals(h, Squint.averageHashBytes(Files.readAllBytes(Path.of(path)), 8));
        assertEquals(h, AverageHash.compute(Squint.decodeFile(path), 8));
    }

    // ------------------------------------------------------------------ //
    // dhash                                                                //
    // ------------------------------------------------------------------ //

    @Test
    void dhash_imagehash_png() throws Exception {
        String path = png("imagehash.png").toString();
        ImageHash h = Squint.dhash(path, 8);
        assertNotNull(h);
        assertEquals(h, Squint.dhashBytes(Files.readAllBytes(Path.of(path)), 8));
        assertEquals(h, DHash.compute(Squint.decodeFile(path), 8));
    }

    @Test
    void dhash_salt_pepper_png() throws Exception {
        String path = png("salt-pepper-256.png").toString();
        ImageHash h = Squint.dhash(path, 8);
        assertNotNull(h);
        assertEquals(h, Squint.dhashBytes(Files.readAllBytes(Path.of(path)), 8));
        assertEquals(h, DHash.compute(Squint.decodeFile(path), 8));
    }

    @Test
    void dhash_jpeg() throws Exception {
        String path = jpg("16x16-quality-95.jpg").toString();
        ImageHash h = Squint.dhash(path, 8);
        assertNotNull(h);
        assertEquals(h, Squint.dhashBytes(Files.readAllBytes(Path.of(path)), 8));
        assertEquals(h, DHash.compute(Squint.decodeFile(path), 8));
    }

    // ------------------------------------------------------------------ //
    // dhash_vertical                                                       //
    // ------------------------------------------------------------------ //

    @Test
    void dhashVertical_imagehash_png() throws Exception {
        String path = png("imagehash.png").toString();
        ImageHash h = Squint.dhashVertical(path, 8);
        assertNotNull(h);
        assertEquals(h, Squint.dhashVerticalBytes(Files.readAllBytes(Path.of(path)), 8));
        assertEquals(h, DHashVertical.compute(Squint.decodeFile(path), 8));
    }

    @Test
    void dhashVertical_gradient_png() throws Exception {
        String path = png("gradient-v-256.png").toString();
        ImageHash h = Squint.dhashVertical(path, 8);
        assertNotNull(h);
        assertEquals(h, Squint.dhashVerticalBytes(Files.readAllBytes(Path.of(path)), 8));
        assertEquals(h, DHashVertical.compute(Squint.decodeFile(path), 8));
    }

    @Test
    void dhashVertical_bmp() throws Exception {
        String path = bmp("rgb24-large.bmp").toString();
        ImageHash h = Squint.dhashVertical(path, 8);
        assertNotNull(h);
        assertEquals(h, Squint.dhashVerticalBytes(Files.readAllBytes(Path.of(path)), 8));
        assertEquals(h, DHashVertical.compute(Squint.decodeFile(path), 8));
    }

    // ------------------------------------------------------------------ //
    // whash (Haar)                                                         //
    // ------------------------------------------------------------------ //

    @Test
    void whashHaar_imagehash_png() throws Exception {
        String path = png("imagehash.png").toString();
        ImageHash h = Squint.whashHaar(path, 8);
        assertNotNull(h);
        assertEquals(h, Squint.whashHaarBytes(Files.readAllBytes(Path.of(path)), 8));
        assertEquals(h, WHashHaar.compute(Squint.decodeFile(path), 8));
    }

    @Test
    void whashHaar_gradient_png() throws Exception {
        String path = png("gradient-diag-256.png").toString();
        ImageHash h = Squint.whashHaar(path, 8);
        assertNotNull(h);
        assertEquals(h, Squint.whashHaarBytes(Files.readAllBytes(Path.of(path)), 8));
        assertEquals(h, WHashHaar.compute(Squint.decodeFile(path), 8));
    }

    @Test
    void whashHaar_jpeg() throws Exception {
        String path = jpg("32x32-quality-95.jpg").toString();
        ImageHash h = Squint.whashHaar(path, 8);
        assertNotNull(h);
        assertEquals(h, Squint.whashHaarBytes(Files.readAllBytes(Path.of(path)), 8));
        assertEquals(h, WHashHaar.compute(Squint.decodeFile(path), 8));
    }

    // ------------------------------------------------------------------ //
    // whash (db4)                                                          //
    // ------------------------------------------------------------------ //

    @Test
    void whashDb4_imagehash_png() throws Exception {
        String path = png("imagehash.png").toString();
        ImageHash h = Squint.whashDb4(path, 8);
        assertNotNull(h);
        assertEquals(h, Squint.whashDb4Bytes(Files.readAllBytes(Path.of(path)), 8));
        assertEquals(h, WHashDb4.compute(Squint.decodeFile(path), 8));
    }

    @Test
    void whashDb4_gradient_png() throws Exception {
        String path = png("gradient-h-256.png").toString();
        ImageHash h = Squint.whashDb4(path, 8);
        assertNotNull(h);
        assertEquals(h, Squint.whashDb4Bytes(Files.readAllBytes(Path.of(path)), 8));
        assertEquals(h, WHashDb4.compute(Squint.decodeFile(path), 8));
    }

    @Test
    void whashDb4_jpeg() throws Exception {
        String path = jpg("64x64-quality-50.jpg").toString();
        ImageHash h = Squint.whashDb4(path, 8);
        assertNotNull(h);
        assertEquals(h, Squint.whashDb4Bytes(Files.readAllBytes(Path.of(path)), 8));
        assertEquals(h, WHashDb4.compute(Squint.decodeFile(path), 8));
    }

    // ------------------------------------------------------------------ //
    // whash (db4 robust)                                                   //
    // ------------------------------------------------------------------ //

    @Test
    void whashDb4Robust_imagehash_png() throws Exception {
        String path = png("imagehash.png").toString();
        ImageHash h = Squint.whashDb4Robust(path, 8);
        assertNotNull(h);
        assertEquals(h, Squint.whashDb4RobustBytes(Files.readAllBytes(Path.of(path)), 8));
        assertEquals(h, WHashDb4Robust.compute(Squint.decodeFile(path), 8));
    }

    @Test
    void whashDb4Robust_gradient_png() throws Exception {
        String path = png("gradient-h-256.png").toString();
        ImageHash h = Squint.whashDb4Robust(path, 8);
        assertNotNull(h);
        assertEquals(h, Squint.whashDb4RobustBytes(Files.readAllBytes(Path.of(path)), 8));
        assertEquals(h, WHashDb4Robust.compute(Squint.decodeFile(path), 8));
    }

    @Test
    void whashDb4Robust_jpeg() throws Exception {
        String path = jpg("64x64-quality-50.jpg").toString();
        ImageHash h = Squint.whashDb4Robust(path, 8);
        assertNotNull(h);
        assertEquals(h, Squint.whashDb4RobustBytes(Files.readAllBytes(Path.of(path)), 8));
        assertEquals(h, WHashDb4Robust.compute(Squint.decodeFile(path), 8));
    }

    // ------------------------------------------------------------------ //
    // colorhash                                                            //
    // ------------------------------------------------------------------ //

    @Test
    void colorhash_imagehash_png() throws Exception {
        String path = png("imagehash.png").toString();
        ImageHash h = Squint.colorhash(path);
        assertNotNull(h);
        assertEquals(h, Squint.colorhashBytes(Files.readAllBytes(Path.of(path))));
        assertEquals(h, ColorHash.compute(Squint.decodeFile(path)));
    }

    @Test
    void colorhash_checker_png() throws Exception {
        String path = png("checker-256.png").toString();
        ImageHash h = Squint.colorhash(path, 3);
        assertNotNull(h);
        assertEquals(h, Squint.colorhashBytes(Files.readAllBytes(Path.of(path)), 3));
        assertEquals(h, ColorHash.compute(Squint.decodeFile(path), 3));
    }

    @Test
    void colorhash_jpeg() throws Exception {
        String path = jpg("32x32-quality-95.jpg").toString();
        ImageHash h = Squint.colorhash(path, 3);
        assertNotNull(h);
        assertEquals(h, Squint.colorhashBytes(Files.readAllBytes(Path.of(path)), 3));
        assertEquals(h, ColorHash.compute(Squint.decodeFile(path), 3));
    }

    // ------------------------------------------------------------------ //
    // crop_resistant_hash                                                  //
    // ------------------------------------------------------------------ //

    @Test
    void cropResistantHash_imagehash_png() throws Exception {
        String path = png("imagehash.png").toString();
        ImageMultiHash h = Squint.cropResistantHash(path);
        assertNotNull(h);
        assertEquals(h.toString(),
                Squint.cropResistantHashBytes(Files.readAllBytes(Path.of(path))).toString());
    }

    @Test
    void cropResistantHash_salt_pepper_png() throws Exception {
        String path = png("salt-pepper-256.png").toString();
        ImageMultiHash h = Squint.cropResistantHash(path);
        assertNotNull(h);
        assertEquals(h.toString(),
                Squint.cropResistantHashBytes(Files.readAllBytes(Path.of(path))).toString());
    }

    @Test
    void cropResistantHash_jpeg() throws Exception {
        String path = jpg("64x64-quality-50.jpg").toString();
        ImageMultiHash h = Squint.cropResistantHash(path);
        assertNotNull(h);
        assertEquals(h.toString(),
                Squint.cropResistantHashBytes(Files.readAllBytes(Path.of(path))).toString());
    }
}
