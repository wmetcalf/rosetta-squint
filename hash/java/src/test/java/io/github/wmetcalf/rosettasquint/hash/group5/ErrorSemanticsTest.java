package io.github.wmetcalf.rosettasquint.hash.group5;

import io.github.wmetcalf.rosettasquint.hash.AverageHash;
import io.github.wmetcalf.rosettasquint.hash.ColorHash;
import io.github.wmetcalf.rosettasquint.hash.DHash;
import io.github.wmetcalf.rosettasquint.hash.Hex;
import io.github.wmetcalf.rosettasquint.hash.PHash;
import io.github.wmetcalf.rosettasquint.hash.WHashHaar;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertThrows;

class ErrorSemanticsTest {
    private static final BufferedImage TINY = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
    private static final BufferedImage SMALL = new BufferedImage(32, 32, BufferedImage.TYPE_INT_RGB);

    @Test
    void averageHashRejectsHashSizeBelowTwo() {
        assertThrows(IllegalArgumentException.class, () -> AverageHash.compute(TINY, 1));
        assertThrows(IllegalArgumentException.class, () -> AverageHash.compute(TINY, 0));
    }

    @Test
    void dhashRejectsHashSizeBelowTwo() {
        assertThrows(IllegalArgumentException.class, () -> DHash.compute(TINY, 1));
    }

    @Test
    void phashRejectsHashSizeBelowTwo() {
        assertThrows(IllegalArgumentException.class, () -> PHash.compute(TINY, 1));
    }

    @Test
    void whashRejectsHashSizeBelowTwo() {
        assertThrows(IllegalArgumentException.class, () -> WHashHaar.compute(SMALL, 1));
    }

    @Test
    void whashRejectsNonPowerOfTwo() {
        assertThrows(IllegalArgumentException.class, () -> WHashHaar.compute(SMALL, 3));
        assertThrows(IllegalArgumentException.class, () -> WHashHaar.compute(SMALL, 5));
    }

    @Test
    void whashAcceptsHashSizeLargerThanImage() {
        // WHash scales the image up if needed, so hashSize > image size is allowed
        BufferedImage tiny = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        // Should not throw
        WHashHaar.compute(tiny, 16);
    }

    @Test
    void colorhashRejectsBinbitsBelowOne() {
        assertThrows(IllegalArgumentException.class, () -> ColorHash.compute(TINY, 0));
    }

    @Test
    void hexToHashRejectsNonSquare() {
        assertThrows(IllegalArgumentException.class, () -> Hex.hexToHash("12345"));
    }

    @Test
    void hexToHashRejectsInvalidChars() {
        assertThrows(IllegalArgumentException.class, () -> Hex.hexToHash("xyz!"));
    }

    @Test
    void hexToFlathashRejectsZeroHashSize() {
        assertThrows(IllegalArgumentException.class, () -> Hex.hexToFlathash("00", 0));
    }
}
