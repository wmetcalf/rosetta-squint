package io.github.wmetcalf.rosettasquint.hash.group1;

import io.github.wmetcalf.rosettasquint.hash.internal.BitPack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BitPackTest {
    @Test
    void pack4x4Pattern() {
        boolean[][] bits = {
                {true, false, true, false},
                {false, true, false, true},
                {true, true, true, true},
                {false, false, false, false},
        };
        assertEquals("a5f0", BitPack.pack(bits));
    }

    @Test
    void packAllOnes8x8() {
        boolean[][] bits = new boolean[8][8];
        for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) bits[y][x] = true;
        assertEquals("ffffffffffffffff", BitPack.pack(bits));
    }

    @Test
    void packAllZeros8x8() {
        boolean[][] bits = new boolean[8][8];
        assertEquals("0000000000000000", BitPack.pack(bits));
    }

    @Test
    void unpackSquareIsInverse() {
        boolean[][] bits = {
                {true, false, true, false},
                {false, true, false, true},
                {true, true, true, true},
                {false, false, false, false},
        };
        boolean[][] roundTrip = BitPack.unpackSquare("a5f0");
        assertEquals(4, roundTrip.length);
        for (int y = 0; y < 4; y++) {
            assertArrayEquals(bits[y], roundTrip[y]);
        }
    }

    @Test
    void unpackFlat() {
        boolean[][] bits = BitPack.unpackFlat("00000000000", 3);
        assertEquals(14, bits.length);
        assertEquals(3, bits[0].length);
        for (int i = 0; i < 14; i++)
            for (int j = 0; j < 3; j++)
                assertEquals(false, bits[i][j]);
    }

    @Test
    void unpackSquareNonSquareLengthThrows() {
        assertThrows(IllegalArgumentException.class, () -> BitPack.unpackSquare("12345"));
    }
}
