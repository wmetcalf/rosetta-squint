package io.rosetta.imagedecode;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class Group3DetectionTests {
    @Test
    public void detectsAllValidBmpFixtures() throws IOException {
        var fixtures = TestKit.listValidFixtures("bmp");
        assertFalse(fixtures.isEmpty(), "should have BMP fixtures");
        for (String relPath : fixtures) {
            byte[] bytes = TestKit.readFixture(relPath);
            Optional<Format> fmt = Decoder.detectFormat(bytes);
            assertEquals(Optional.of(Format.BMP), fmt, "should detect BMP for " + relPath);
        }
    }

    @Test
    public void rejectsBadSignature() throws IOException {
        byte[] bytes = TestKit.readFixture("bmp/invalid/bad-signature.bmp");
        Optional<Format> fmt = Decoder.detectFormat(bytes);
        assertTrue(fmt.isEmpty(), "should return empty for bad signature");
    }

    @Test
    public void rejectsEmptyAndShortInput() {
        assertTrue(Decoder.detectFormat(new byte[0]).isEmpty());
        assertTrue(Decoder.detectFormat(new byte[]{0x42}).isEmpty());
        assertTrue(Decoder.detectFormat(null).isEmpty());
    }

    @Test
    public void supportedFormatsContainsBMP() {
        assertTrue(Decoder.supportedFormats().contains(Format.BMP));
    }

    @Test
    public void detectsAllValidPngFixtures() throws IOException {
        var fixtures = TestKit.listValidFixtures("png");
        assertFalse(fixtures.isEmpty(), "should have PNG fixtures");
        for (String rel : fixtures) {
            byte[] bytes = TestKit.readFixture(rel);
            Optional<Format> fmt = Decoder.detectFormat(bytes);
            assertEquals(Optional.of(Format.PNG), fmt, "should detect PNG for " + rel);
        }
    }

    @Test
    public void supportedFormatsContainsPng() {
        assertTrue(Decoder.supportedFormats().contains(Format.PNG));
    }

    @Test
    public void detectsAllValidGifFixtures() throws IOException {
        var fixtures = TestKit.listValidFixtures("gif");
        assertFalse(fixtures.isEmpty(), "should have GIF fixtures");
        for (String rel : fixtures) {
            byte[] bytes = TestKit.readFixture(rel);
            Optional<Format> fmt = Decoder.detectFormat(bytes);
            assertEquals(Optional.of(Format.GIF), fmt, "should detect GIF for " + rel);
        }
    }

    @Test
    public void supportedFormatsContainsGif() {
        assertTrue(Decoder.supportedFormats().contains(Format.GIF));
    }

    @Test
    public void detectsAllValidJpegFixtures() throws IOException {
        var fixtures = TestKit.listValidFixtures("jpeg");
        assertFalse(fixtures.isEmpty(), "should have JPEG fixtures");
        for (String rel : fixtures) {
            byte[] bytes = TestKit.readFixture(rel);
            Optional<Format> fmt = Decoder.detectFormat(bytes);
            assertEquals(Optional.of(Format.JPEG), fmt, "should detect JPEG for " + rel);
        }
    }

    @Test
    public void supportedFormatsContainsJpeg() {
        assertTrue(Decoder.supportedFormats().contains(Format.JPEG));
    }

    @Test
    public void detectsAllValidWebpFixtures() throws IOException {
        var fixtures = TestKit.listValidFixtures("webp");
        assertFalse(fixtures.isEmpty(), "should have WebP fixtures");
        for (String rel : fixtures) {
            byte[] bytes = TestKit.readFixture(rel);
            Optional<Format> fmt = Decoder.detectFormat(bytes);
            assertEquals(Optional.of(Format.WEBP), fmt, "should detect WEBP for " + rel);
        }
    }

    @Test
    public void supportedFormatsContainsWebp() {
        assertTrue(Decoder.supportedFormats().contains(Format.WEBP));
    }

    @Test
    public void detectsAllValidTiffFixtures() throws IOException {
        var fixtures = TestKit.listValidFixtures("tiff");
        assertFalse(fixtures.isEmpty(), "should have TIFF fixtures");
        for (String rel : fixtures) {
            byte[] bytes = TestKit.readFixture(rel);
            Optional<Format> fmt = Decoder.detectFormat(bytes);
            assertEquals(Optional.of(Format.TIFF), fmt, "should detect TIFF for " + rel);
        }
    }

    @Test
    public void supportedFormatsContainsTiff() {
        assertTrue(Decoder.supportedFormats().contains(Format.TIFF));
    }

    @Test
    public void detectsAllValidHeicFixtures() throws IOException {
        var fixtures = TestKit.listValidFixtures("heic");
        assertFalse(fixtures.isEmpty(), "should have HEIC fixtures");
        for (String rel : fixtures) {
            byte[] bytes = TestKit.readFixture(rel);
            Optional<Format> fmt = Decoder.detectFormat(bytes);
            assertEquals(Optional.of(Format.HEIC), fmt, "should detect HEIC for " + rel);
        }
    }

    @Test
    public void supportedFormatsContainsHeic() {
        assertTrue(Decoder.supportedFormats().contains(Format.HEIC));
    }

    @Test
    public void rejectsAvifAsUnsupported() throws IOException {
        byte[] bytes = TestKit.readFixture("heic/invalid/avif.heic");
        Optional<Format> fmt = Decoder.detectFormat(bytes);
        assertTrue(fmt.isEmpty(), "avif brand should not be detected as HEIC");
    }
}
