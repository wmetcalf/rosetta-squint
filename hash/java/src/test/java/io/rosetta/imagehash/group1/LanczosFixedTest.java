package io.rosetta.imagehash.group1;

import io.rosetta.imagehash.internal.LanczosFixed;
import io.rosetta.imagehash.testkit.LanczosCase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

class LanczosFixedTest {
    @Test
    void downsample64To32Gradient() {
        runCase("downsample_64_to_32_gradient");
    }

    @Test
    void upsample16To32Gradient() {
        runCase("upsample_16_to_32_gradient");
    }

    @Test
    void identity32To32Random() {
        runCase("identity_32_to_32_random");
    }

    @Test
    void asymmetric64x48To32x24() {
        runCase("asymmetric_64x48_to_32x24");
    }

    private static void runCase(String name) {
        LanczosCase c = LanczosCase.load(name);
        int[][] out = LanczosFixed.resize(c.src, c.dstW, c.dstH);
        assertEquals(c.dstH, out.length, "row count");
        assertEquals(c.dstW, out[0].length, "col count");
        for (int y = 0; y < c.dstH; y++) {
            for (int x = 0; x < c.dstW; x++) {
                if (out[y][x] != c.dst[y][x]) {
                    fail("Mismatch at (" + y + "," + x + ") for " + name +
                            ": expected " + c.dst[y][x] + ", got " + out[y][x]);
                }
            }
        }
    }
}
