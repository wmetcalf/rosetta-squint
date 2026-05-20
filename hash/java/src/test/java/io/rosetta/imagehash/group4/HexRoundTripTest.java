package io.rosetta.imagehash.group4;

import io.rosetta.imagehash.Hex;
import io.rosetta.imagehash.ImageHash;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HexRoundTripTest {
    @Test
    void hexToHashAndBack() {
        String hex = "ffd7918181c9ffff";
        ImageHash h = Hex.hexToHash(hex);
        assertEquals(8 * 8, h.bitCount());
        assertEquals(hex, h.toHex());
    }

    @Test
    void hexToFlathashAndBack() {
        String hex = "0123456789abcd";
        ImageHash h = Hex.hexToFlathash(hex, 4);
        assertEquals(14 * 4, h.bitCount());
        assertEquals(hex, h.toHex());
    }

    @Test
    void hexToHashNonSquareThrows() {
        assertThrows(IllegalArgumentException.class, () -> Hex.hexToHash("12345"));
    }

    @Test
    void hexToHashInvalidCharsThrows() {
        assertThrows(IllegalArgumentException.class, () -> Hex.hexToHash("xyz!"));
    }

    @Test
    void roundTripAllZeros() {
        String hex = "0000000000000000";
        ImageHash h = Hex.hexToHash(hex);
        assertEquals("0000000000000000", h.toHex());
    }
}
