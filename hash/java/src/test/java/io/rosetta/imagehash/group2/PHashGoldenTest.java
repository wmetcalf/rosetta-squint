package io.rosetta.imagehash.group2;

import io.rosetta.imagehash.ImageHash;
import io.rosetta.imagehash.PHash;
import io.rosetta.imagehash.testkit.Goldens;
import io.rosetta.imagehash.testkit.PreDecoded;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

class PHashGoldenTest {
    @TestFactory
    List<DynamicTest> phashMatchesGoldens() {
        List<DynamicTest> tests = new ArrayList<>();
        for (Goldens.Triple t : Goldens.algorithmCases("phash")) {
            tests.add(dynamicTest("phash " + t.fixture() + " size=" + t.size(), () -> {
                BufferedImage img = PreDecoded.loadAsBufferedImage(t.fixture());
                ImageHash result = PHash.compute(img, t.size());
                assertEquals(t.hex(), result.toHex(),
                        () -> "fixture=" + t.fixture() + " size=" + t.size());
            }));
        }
        return tests;
    }
}
