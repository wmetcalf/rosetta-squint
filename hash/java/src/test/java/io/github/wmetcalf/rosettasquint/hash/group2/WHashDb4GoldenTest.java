package io.github.wmetcalf.rosettasquint.hash.group2;

import io.github.wmetcalf.rosettasquint.hash.ImageHash;
import io.github.wmetcalf.rosettasquint.hash.WHashDb4;
import io.github.wmetcalf.rosettasquint.hash.testkit.Goldens;
import io.github.wmetcalf.rosettasquint.hash.testkit.PreDecoded;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

class WHashDb4GoldenTest {
    /**
     * Fixtures where db4 + median thresholding is at the bit boundary due to
     * ULP-level numerical noise (LL coefficients ~1e-17 with median exactly 0).
     * PyWavelets' C+SIMD/FMA accumulation resolves these on a different side of
     * zero than portable double arithmetic. See spec/SPEC.md whash_db4 section,
     * which explicitly permits these. Matches the exemptions in the Go, JS, and
     * Swift ports.
     */
    private static final Set<String> ULP_EXEMPT = Set.of(
            "checker-256.png:8",
            "checker-256.png:16",
            "line-art-icon-256.png:16"
    );

    @TestFactory
    List<DynamicTest> whashDb4MatchesGoldens() {
        List<DynamicTest> tests = new ArrayList<>();
        for (Goldens.Triple t : Goldens.algorithmCases("whash_db4")) {
            String key = t.fixture() + ":" + t.size();
            if (ULP_EXEMPT.contains(key)) continue;
            tests.add(dynamicTest("whash_db4 " + t.fixture() + " size=" + t.size(), () -> {
                BufferedImage img = PreDecoded.loadAsBufferedImage(t.fixture());
                ImageHash result = WHashDb4.compute(img, t.size());
                assertEquals(t.hex(), result.toHex(),
                        () -> "fixture=" + t.fixture() + " size=" + t.size());
            }));
        }
        return tests;
    }
}
