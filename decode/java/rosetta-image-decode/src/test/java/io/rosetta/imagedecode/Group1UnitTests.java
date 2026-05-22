package io.rosetta.imagedecode;

import io.rosetta.imagedecode.internal.BMPDecoder;
import org.junit.jupiter.api.Test;

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
