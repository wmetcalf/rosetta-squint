package io.github.wmetcalf.rosettasquint.hash.group2;

import io.github.wmetcalf.rosettasquint.hash.ImageHash;
import io.github.wmetcalf.rosettasquint.hash.PHashSimple;
import io.github.wmetcalf.rosettasquint.hash.testkit.Goldens;
import io.github.wmetcalf.rosettasquint.hash.testkit.PreDecoded;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

class PHashSimpleGoldenTest {
    @TestFactory
    List<DynamicTest> phashSimpleMatchesGoldens() {
        List<DynamicTest> tests = new ArrayList<>();
        for (Goldens.Triple t : Goldens.algorithmCases("phash_simple")) {
            tests.add(dynamicTest("phash_simple " + t.fixture() + " size=" + t.size(), () -> {
                BufferedImage img = PreDecoded.loadAsBufferedImage(t.fixture());
                ImageHash result = PHashSimple.compute(img, t.size());
                assertEquals(t.hex(), result.toHex(),
                        () -> "fixture=" + t.fixture() + " size=" + t.size());
            }));
        }
        return tests;
    }
}
