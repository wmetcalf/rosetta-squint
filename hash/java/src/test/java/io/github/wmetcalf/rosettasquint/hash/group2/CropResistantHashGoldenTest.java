package io.github.wmetcalf.rosettasquint.hash.group2;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.wmetcalf.rosettasquint.hash.CropResistantHash;
import io.github.wmetcalf.rosettasquint.hash.ImageMultiHash;
import io.github.wmetcalf.rosettasquint.hash.testkit.Goldens;
import io.github.wmetcalf.rosettasquint.hash.testkit.PreDecoded;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

class CropResistantHashGoldenTest {

    @TestFactory
    List<DynamicTest> cropResistantHashMatchesGoldens() {
        List<DynamicTest> tests = new ArrayList<>();
        JsonNode algos = Goldens.root().get("algorithms");
        JsonNode crh = algos.get("crop_resistant_hash");
        JsonNode fixtures = crh.get("fixtures");

        Iterator<Map.Entry<String, JsonNode>> fixIter = fixtures.fields();
        while (fixIter.hasNext()) {
            Map.Entry<String, JsonNode> fixEntry = fixIter.next();
            String fixture = fixEntry.getKey();
            // The size key is "default" (not an integer) for crop_resistant_hash
            JsonNode sizeMap = fixEntry.getValue();
            String expectedHex = sizeMap.get("default").asText();

            tests.add(dynamicTest("crop_resistant_hash " + fixture, () -> {
                BufferedImage img = PreDecoded.loadAsBufferedImage(fixture);
                ImageMultiHash result = CropResistantHash.compute(img);
                assertEquals(expectedHex, result.toString(),
                        () -> "fixture=" + fixture);
            }));
        }
        return tests;
    }
}
