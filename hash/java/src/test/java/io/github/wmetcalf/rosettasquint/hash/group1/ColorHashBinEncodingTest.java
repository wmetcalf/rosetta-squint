package io.github.wmetcalf.rosettasquint.hash.group1;

import io.github.wmetcalf.rosettasquint.hash.ColorHash;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class ColorHashBinEncodingTest {
    /**
     * Tests the quirky colorhash bin encoding from SPEC.md §8:
     *   bit[i] = (v // 2^(B-i-1)) % 2^(B-i) > 0
     */
    @Test
    void binEncodingB4() {
        assertArrayEquals(new boolean[]{false, false, false, false}, ColorHash.binEncode(0, 4));
        assertArrayEquals(new boolean[]{false, false, false, true},  ColorHash.binEncode(1, 4));
        assertArrayEquals(new boolean[]{false, false, true,  false}, ColorHash.binEncode(2, 4));
        assertArrayEquals(new boolean[]{false, true,  true,  false}, ColorHash.binEncode(4, 4));   // NOT 0100
        assertArrayEquals(new boolean[]{false, true,  true,  true},  ColorHash.binEncode(7, 4));
        assertArrayEquals(new boolean[]{true,  true,  false, false}, ColorHash.binEncode(8, 4));   // NOT 1000
        assertArrayEquals(new boolean[]{true,  true,  true,  true},  ColorHash.binEncode(15, 4));
    }

    @Test
    void binEncodingB3() {
        assertArrayEquals(new boolean[]{false, false, false}, ColorHash.binEncode(0, 3));
        assertArrayEquals(new boolean[]{true,  true,  true},  ColorHash.binEncode(7, 3));
    }
}
