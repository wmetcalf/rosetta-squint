package io.rosetta.imagehash.group2;

import io.rosetta.imagehash.ImageHash;
import io.rosetta.imagehash.WHashDb4Robust;
import io.rosetta.imagehash.testkit.Goldens;
import io.rosetta.imagehash.testkit.PreDecoded;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

class WHashDb4RobustGoldenTest {
    @TestFactory
    List<DynamicTest> whashDb4RobustMatchesGoldens() {
        List<DynamicTest> tests = new ArrayList<>();
        for (Goldens.Triple t : Goldens.algorithmCases("whash_db4_robust")) {
            tests.add(dynamicTest("whash_db4_robust " + t.fixture() + " size=" + t.size(), () -> {
                BufferedImage img = PreDecoded.loadAsBufferedImage(t.fixture());
                ImageHash result = WHashDb4Robust.compute(img, t.size());
                assertEquals(t.hex(), result.toHex(),
                        () -> "fixture=" + t.fixture() + " size=" + t.size());
            }));
        }
        return tests;
    }
}
