package io.rosetta.imagedecode;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class Group5InvariantsTests {
    @Test
    public void allDecodedImagesHaveValidShape() throws IOException {
        List<String> fixtures = TestKit.listValidFixtures("bmp");
        for (String rel : fixtures) {
            byte[] bytes = TestKit.readFixture(rel);
            try {
                DecodedImage img = Decoder.decode(bytes);
                assertTrue(img.width() > 0, rel + ": width should be positive");
                assertTrue(img.height() > 0, rel + ": height should be positive");
                assertEquals(Format.BMP, img.format(), rel + ": format should be BMP");
                int expectedBytes = img.width() * img.height() * img.channels().bytesPerPixel();
                assertEquals(expectedBytes, img.data().length, rel + ": data length matches shape");
                assertTrue(img.channels() == Channels.RGB || img.channels() == Channels.RGBA, rel + ": channels in {RGB, RGBA}");
            } catch (DecodeException e) {
                fail(rel + ": unexpected decode failure: " + e.kind() + ": " + e.detail());
            }
        }
    }

    @Test
    public void supportedFormatsContainsOnlyBMP() {
        var supported = Decoder.supportedFormats();
        assertEquals(1, supported.size());
        assertTrue(supported.contains(Format.BMP));
    }

    @Test
    public void decodedImageIsValueEqual() throws IOException {
        byte[] bytes = TestKit.readFixture("bmp/valid/rgb24-1x1.bmp");
        DecodedImage a = Decoder.decode(bytes);
        DecodedImage b = Decoder.decode(bytes);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }
}
