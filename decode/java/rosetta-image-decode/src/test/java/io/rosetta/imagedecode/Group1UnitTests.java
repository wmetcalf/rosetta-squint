package io.rosetta.imagedecode;

import io.rosetta.imagedecode.internal.BMPDecoder;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.*;

public class Group1UnitTests {
    /** Test that biPlanes != 1 is rejected with proper error. */
    @Test
    public void rejectsBiPlanesNot1() throws Exception {
        byte[] bytes = TestKit.readFixture("bmp/invalid/planes-not-1.bmp");
        DecodeException ex = assertThrows(DecodeException.class, () -> Decoder.decode(bytes));
        assertEquals(DecodeException.Kind.CORRUPT_INPUT, ex.kind());
        assertTrue(ex.detail().contains("biPlanes"), "detail should mention biPlanes, got: " + ex.detail());
    }

    /** Test that biWidth = 0 is rejected. */
    @Test
    public void rejectsZeroWidth() throws Exception {
        byte[] bytes = TestKit.readFixture("bmp/invalid/zero-width.bmp");
        DecodeException ex = assertThrows(DecodeException.class, () -> Decoder.decode(bytes));
        assertEquals(DecodeException.Kind.CORRUPT_INPUT, ex.kind());
        assertTrue(ex.detail().contains("biWidth"));
    }

    /** Test that invalid biBitCount is rejected. */
    @Test
    public void rejectsBitcount3() throws Exception {
        byte[] bytes = TestKit.readFixture("bmp/invalid/bitcount-3.bmp");
        DecodeException ex = assertThrows(DecodeException.class, () -> Decoder.decode(bytes));
        assertEquals(DecodeException.Kind.CORRUPT_INPUT, ex.kind());
        assertTrue(ex.detail().contains("biBitCount"));
    }

    /** Test that BI_BITFIELDS with zero mask is rejected. */
    @Test
    public void rejectsZeroMask() throws Exception {
        byte[] bytes = TestKit.readFixture("bmp/invalid/bitfields-zero-mask.bmp");
        DecodeException ex = assertThrows(DecodeException.class, () -> Decoder.decode(bytes));
        assertEquals(DecodeException.Kind.CORRUPT_INPUT, ex.kind());
        assertTrue(ex.detail().contains("mask is zero"));
    }

    /**
     * D-M1: biClrUsed must be clamped to bit-depth max so an attacker-controlled
     * value (e.g. 0x10000000 = 256M entries) cannot cause GB-scale palette allocation.
     * Build an 8-bit BMP in memory with biClrUsed = 0x10000000.
     * With the clamp, decode succeeds reading only 256*4 bytes of palette.
     * Without the clamp, entryCount*4 overflows int (becomes negative),
     * the truncation check bytes.length < colorTableEnd silently passes, and
     * `new int[entryCount][3]` would request ~12 GB.
     */
    @Test
    public void clampsOversizedClrUsedBomb() throws Exception {
        byte[] bytes = buildPal8WithClrUsed(0x10000000);
        DecodedImage img = Decoder.decode(bytes);
        assertEquals(2, img.width());
        assertEquals(2, img.height());
        // 2x2 RGB = 12 bytes — confirms no excessive allocation occurred.
        assertEquals(12, img.data().length);
    }

    /** Clamp test: biClrUsed = 257 (just over 8-bit max) should clamp to 256. */
    @Test
    public void clampsClrUsed257to256() throws Exception {
        byte[] bytes = buildPal8WithClrUsed(257);
        DecodedImage img = Decoder.decode(bytes);
        assertEquals(2, img.width());
        assertEquals(2, img.height());
    }

    /** Unit test for the clampEntryCount helper directly. */
    @Test
    public void clampEntryCountBoundaries() {
        // bit-depth maxima: 1->2, 4->16, 8->256
        assertEquals(2, BMPDecoder.clampEntryCount(0, 1));
        assertEquals(2, BMPDecoder.clampEntryCount(100, 1));
        assertEquals(1, BMPDecoder.clampEntryCount(1, 1));
        assertEquals(16, BMPDecoder.clampEntryCount(0, 4));
        assertEquals(16, BMPDecoder.clampEntryCount(0x40000000, 4));
        assertEquals(8, BMPDecoder.clampEntryCount(8, 4));
        assertEquals(256, BMPDecoder.clampEntryCount(0, 8));
        assertEquals(256, BMPDecoder.clampEntryCount(0x10000000, 8));
        assertEquals(256, BMPDecoder.clampEntryCount(257, 8));
        assertEquals(100, BMPDecoder.clampEntryCount(100, 8));
        // Negative (signed-32 view of huge unsigned) treated as the "<=0 -> default" branch.
        assertEquals(256, BMPDecoder.clampEntryCount(0x80000000, 8));
    }

    /**
     * Build a minimal 2x2 8-bit paletted BMP with biClrUsed set to the supplied value.
     * The actual palette in the file is exactly 256 entries (1024 bytes), so a clamped
     * decoder reads exactly those 1024 bytes; an un-clamped decoder would attempt to
     * read clrUsed*4 bytes and overflow or OOM.
     */
    private static byte[] buildPal8WithClrUsed(int biClrUsed) {
        int width = 2;
        int height = 2;
        int paletteBytes = 256 * 4; // actual on-disk palette: 1024 bytes
        int rowStride = ((width + 3) / 4) * 4; // = 4
        int pixelDataSize = rowStride * height; // = 8
        int pixelDataOffset = 14 + 40 + paletteBytes; // = 1078
        int fileSize = pixelDataOffset + pixelDataSize;

        ByteBuffer bb = ByteBuffer.allocate(fileSize).order(ByteOrder.LITTLE_ENDIAN);
        // BMP file header (14 bytes)
        bb.put((byte) 'B'); bb.put((byte) 'M');
        bb.putInt(fileSize);
        bb.putShort((short) 0); bb.putShort((short) 0);
        bb.putInt(pixelDataOffset);
        // DIB BITMAPINFOHEADER (40 bytes)
        bb.putInt(40);
        bb.putInt(width);
        bb.putInt(height);
        bb.putShort((short) 1); // planes
        bb.putShort((short) 8); // bit count
        bb.putInt(0); // compression BI_RGB
        bb.putInt(pixelDataSize); // biSizeImage
        bb.putInt(2835); // x ppm
        bb.putInt(2835); // y ppm
        bb.putInt(biClrUsed); // ATTACKER-CONTROLLED
        bb.putInt(0); // biClrImportant
        // Palette: 256 entries of (B, G, R, reserved)
        for (int i = 0; i < 256; i++) {
            bb.put((byte) i); bb.put((byte) i); bb.put((byte) i); bb.put((byte) 0);
        }
        // Pixel data: row 0 = [0, 1, pad, pad], row 1 = [2, 3, pad, pad] (palette indices)
        bb.put((byte) 0); bb.put((byte) 1); bb.put((byte) 0); bb.put((byte) 0);
        bb.put((byte) 2); bb.put((byte) 3); bb.put((byte) 0); bb.put((byte) 0);
        return bb.array();
    }

    /** BI_BITFIELDS mask math: for mask 0x07E0 (G channel in 5-6-5), shift=5, range=0x3F=63. */
    @Test
    public void bitfieldsScalingFormula() {
        long mask = 0x07E0L;  // 5-6-5 green: bits 5-10
        int shift = Long.numberOfTrailingZeros(mask);
        long range = mask >>> shift;
        assertEquals(5, shift);
        assertEquals(63L, range);
        // Test channel = ((pixel & mask) >> shift) * 255 / range
        long pixel = 0x07E0L;  // max green
        long channel = (((pixel & mask) >>> shift) * 255L) / range;
        assertEquals(255L, channel);
        pixel = 0x0000L;  // zero green
        channel = (((pixel & mask) >>> shift) * 255L) / range;
        assertEquals(0L, channel);
    }
}
