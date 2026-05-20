package io.rosetta.imagehash.group5;

import io.rosetta.imagehash.ImageHash;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DistanceTest {
    @Test
    void hammingDistanceIsZeroForEqualHashes() {
        boolean[][] bits = {{true, false}, {true, true}};
        ImageHash a = new ImageHash(bits);
        ImageHash b = new ImageHash(bits);
        assertEquals(0, a.subtract(b));
    }

    @Test
    void hammingDistanceCountsDifferingBits() {
        ImageHash a = new ImageHash(new boolean[][]{{true, false}, {true, true}});
        ImageHash b = new ImageHash(new boolean[][]{{false, false}, {true, false}});
        assertEquals(2, a.subtract(b));
    }

    @Test
    void bitCountIsHeightTimesWidth() {
        boolean[][] bits = new boolean[8][8];
        ImageHash h = new ImageHash(bits);
        assertEquals(64, h.bitCount());
    }

    @Test
    void equalsIsValueBased() {
        ImageHash a = new ImageHash(new boolean[][]{{true, false}});
        ImageHash b = new ImageHash(new boolean[][]{{true, false}});
        ImageHash c = new ImageHash(new boolean[][]{{false, false}});
        assertEquals(a, b);
        assertNotEquals(a, c);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void toHexProducesExpectedFormat() {
        boolean[][] bits = new boolean[8][8];
        for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) bits[y][x] = true;
        ImageHash h = new ImageHash(bits);
        assertEquals("ffffffffffffffff", h.toHex());
    }

    @Test
    void subtractRequiresMatchingShape() {
        ImageHash a = new ImageHash(new boolean[][]{{true, false}});
        ImageHash b = new ImageHash(new boolean[][]{{true, false}, {true, false}});
        assertThrows(IllegalArgumentException.class, () -> a.subtract(b));
    }
}
