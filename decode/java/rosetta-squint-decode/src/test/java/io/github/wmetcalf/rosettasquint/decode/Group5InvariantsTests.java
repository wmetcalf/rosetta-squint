package io.github.wmetcalf.rosettasquint.decode;

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
    public void supportedFormatsContainsBmpPngGifJpegWebpTiffHeic() {
        var supported = Decoder.supportedFormats();
        assertEquals(7, supported.size());
        assertTrue(supported.contains(Format.BMP));
        assertTrue(supported.contains(Format.PNG));
        assertTrue(supported.contains(Format.GIF));
        assertTrue(supported.contains(Format.JPEG));
        assertTrue(supported.contains(Format.WEBP));
        assertTrue(supported.contains(Format.TIFF));
        assertTrue(supported.contains(Format.HEIC));
    }

    @Test
    public void allDecodedPngImagesHaveValidShape() throws IOException {
        List<String> fixtures = TestKit.listValidFixtures("png");
        for (String rel : fixtures) {
            byte[] bytes = TestKit.readFixture(rel);
            try {
                DecodedImage img = Decoder.decode(bytes);
                assertTrue(img.width() > 0, rel);
                assertTrue(img.height() > 0, rel);
                assertEquals(Format.PNG, img.format(), rel);
                int expectedBytes = img.width() * img.height() * img.channels().bytesPerPixel();
                assertEquals(expectedBytes, img.data().length, rel);
                assertTrue(img.channels() == Channels.RGB || img.channels() == Channels.RGBA, rel);
            } catch (DecodeException e) {
                fail(rel + ": unexpected decode failure: " + e.kind() + ": " + e.detail());
            }
        }
    }

    @Test
    public void allDecodedGifImagesHaveValidShape() throws IOException {
        List<String> fixtures = TestKit.listValidFixtures("gif");
        for (String rel : fixtures) {
            byte[] bytes = TestKit.readFixture(rel);
            try {
                DecodedImage img = Decoder.decode(bytes);
                assertTrue(img.width() > 0, rel + ": width should be positive");
                assertTrue(img.height() > 0, rel + ": height should be positive");
                assertEquals(Format.GIF, img.format(), rel + ": format should be GIF");
                int expectedBytes = img.width() * img.height() * img.channels().bytesPerPixel();
                assertEquals(expectedBytes, img.data().length, rel + ": data length matches shape");
                assertTrue(img.channels() == Channels.RGB || img.channels() == Channels.RGBA, rel + ": channels in {RGB, RGBA}");
            } catch (DecodeException e) {
                fail(rel + ": unexpected decode failure: " + e.kind() + ": " + e.detail());
            }
        }
    }

    @Test
    public void allDecodedJpegImagesHaveValidShape() throws IOException {
        List<String> fixtures = TestKit.listValidFixtures("jpeg");
        for (String rel : fixtures) {
            byte[] bytes = TestKit.readFixture(rel);
            try {
                DecodedImage img = Decoder.decode(bytes);
                assertTrue(img.width() > 0, rel + ": width should be positive");
                assertTrue(img.height() > 0, rel + ": height should be positive");
                assertEquals(Format.JPEG, img.format(), rel + ": format should be JPEG");
                int expectedBytes = img.width() * img.height() * img.channels().bytesPerPixel();
                assertEquals(expectedBytes, img.data().length, rel + ": data length matches shape");
                assertTrue(img.channels() == Channels.RGB || img.channels() == Channels.RGBA, rel + ": channels in {RGB, RGBA}");
            } catch (DecodeException e) {
                fail(rel + ": unexpected decode failure: " + e.kind() + ": " + e.detail());
            }
        }
    }

    @Test
    public void allDecodedWebpImagesHaveValidShape() throws IOException {
        List<String> fixtures = TestKit.listValidFixtures("webp");
        for (String rel : fixtures) {
            byte[] bytes = TestKit.readFixture(rel);
            try {
                DecodedImage img = Decoder.decode(bytes);
                assertTrue(img.width() > 0, rel + ": width should be positive");
                assertTrue(img.height() > 0, rel + ": height should be positive");
                assertEquals(Format.WEBP, img.format(), rel + ": format should be WEBP");
                int expectedBytes = img.width() * img.height() * img.channels().bytesPerPixel();
                assertEquals(expectedBytes, img.data().length, rel + ": data length matches shape");
                assertTrue(img.channels() == Channels.RGB || img.channels() == Channels.RGBA,
                        rel + ": channels in {RGB, RGBA}");
            } catch (DecodeException e) {
                fail(rel + ": unexpected decode failure: " + e.kind() + ": " + e.detail());
            }
        }
    }

    @Test
    public void allDecodedTiffImagesHaveValidShape() throws IOException {
        List<String> fixtures = TestKit.listValidFixtures("tiff");
        for (String rel : fixtures) {
            byte[] bytes = TestKit.readFixture(rel);
            try {
                DecodedImage img = Decoder.decode(bytes);
                assertTrue(img.width() > 0, rel + ": width should be positive");
                assertTrue(img.height() > 0, rel + ": height should be positive");
                assertEquals(Format.TIFF, img.format(), rel + ": format should be TIFF");
                int expectedBytes = img.width() * img.height() * img.channels().bytesPerPixel();
                assertEquals(expectedBytes, img.data().length, rel + ": data length matches shape");
                assertTrue(img.channels() == Channels.RGB || img.channels() == Channels.RGBA,
                        rel + ": channels in {RGB, RGBA}");
            } catch (DecodeException e) {
                fail(rel + ": unexpected decode failure: " + e.kind() + ": " + e.detail());
            }
        }
    }

    @Test
    public void allDecodedHeicImagesHaveValidShape() throws IOException {
        List<String> fixtures = TestKit.listValidFixtures("heic");
        for (String rel : fixtures) {
            byte[] bytes = TestKit.readFixture(rel);
            try {
                DecodedImage img = Decoder.decode(bytes);
                assertTrue(img.width() > 0, rel + ": width should be positive");
                assertTrue(img.height() > 0, rel + ": height should be positive");
                assertEquals(Format.HEIC, img.format(), rel + ": format should be HEIC");
                int expectedBytes = img.width() * img.height() * img.channels().bytesPerPixel();
                assertEquals(expectedBytes, img.data().length, rel + ": data length matches shape");
                assertTrue(img.channels() == Channels.RGB || img.channels() == Channels.RGBA,
                        rel + ": channels in {RGB, RGBA}");
            } catch (DecodeException e) {
                fail(rel + ": unexpected decode failure: " + e.kind() + ": " + e.detail());
            }
        }
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
