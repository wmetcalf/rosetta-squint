package io.github.wmetcalf.rosettasquint.hash.group3;

import io.github.wmetcalf.rosettasquint.hash.AverageHash;
import io.github.wmetcalf.rosettasquint.hash.ColorHash;
import io.github.wmetcalf.rosettasquint.hash.DHash;
import io.github.wmetcalf.rosettasquint.hash.ImageHash;
import io.github.wmetcalf.rosettasquint.hash.PHash;
import io.github.wmetcalf.rosettasquint.hash.WHashHaar;
import io.github.wmetcalf.rosettasquint.hash.testkit.Goldens;
import io.github.wmetcalf.rosettasquint.hash.testkit.SpecPath;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

class PngEndToEndTest {
    /** Fixtures listed in DECODER_NOTES.md to skip (one per line, "<name>.png — ..."). Empty initially. */
    private static final Set<String> EXEMPT = loadExemptions();

    @TestFactory
    Stream<DynamicTest> allAlgorithmsEndToEnd() {
        List<DynamicTest> tests = new ArrayList<>();
        for (Goldens.Triple t : Goldens.algorithmCases("average_hash")) {
            tests.add(makeTest("average_hash", t, h -> AverageHash.compute(h, t.size())));
        }
        for (Goldens.Triple t : Goldens.algorithmCases("dhash")) {
            tests.add(makeTest("dhash", t, h -> DHash.compute(h, t.size())));
        }
        for (Goldens.Triple t : Goldens.algorithmCases("phash")) {
            tests.add(makeTest("phash", t, h -> PHash.compute(h, t.size())));
        }
        for (Goldens.Triple t : Goldens.algorithmCases("whash_haar")) {
            tests.add(makeTest("whash_haar", t, h -> WHashHaar.compute(h, t.size())));
        }
        for (Goldens.Triple t : Goldens.algorithmCases("colorhash")) {
            tests.add(makeTest("colorhash", t, h -> ColorHash.compute(h, t.size())));
        }
        return tests.stream();
    }

    private static DynamicTest makeTest(String algoName, Goldens.Triple t, java.util.function.Function<BufferedImage, ImageHash> algo) {
        return dynamicTest(algoName + " " + t.fixture() + " size=" + t.size(), () -> {
            if (EXEMPT.contains(t.fixture())) {
                org.junit.jupiter.api.Assumptions.abort("Group-3 exempt per DECODER_NOTES.md: " + t.fixture());
            }
            BufferedImage img = ImageIO.read(SpecPath.FIXTURES.resolve(t.fixture()).toFile());
            ImageHash result = algo.apply(img);
            assertEquals(t.hex(), result.toHex(),
                    () -> algoName + " end-to-end PNG: fixture=" + t.fixture() + " size=" + t.size());
        });
    }

    private static Set<String> loadExemptions() {
        Set<String> out = new HashSet<>();
        Path notes = SpecPath.SPEC.resolve("..").resolve("java").resolve("DECODER_NOTES.md").normalize();
        if (!Files.exists(notes)) return out;
        try {
            for (String line : Files.readAllLines(notes)) {
                int dash = line.indexOf("—");
                if (dash > 0) {
                    String fixture = line.substring(0, dash).trim();
                    if (fixture.endsWith(".png")) out.add(fixture);
                }
            }
        } catch (Exception e) {
            // best-effort
        }
        return out;
    }
}
