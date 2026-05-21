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
}
