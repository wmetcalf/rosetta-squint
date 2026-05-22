package io.rosetta.imagehash.group1;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.rosetta.imagehash.internal.FindSegments;
import io.rosetta.imagehash.testkit.SpecPath;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

class FindSegmentsTest {

    @TestFactory
    List<DynamicTest> allCasesMatchSpec() throws IOException {
        JsonNode data = new ObjectMapper().readTree(SpecPath.SEGMENTATION_CASES.toFile());
        JsonNode cases = data.get("cases");
        List<DynamicTest> tests = new ArrayList<>();
        for (Iterator<JsonNode> it = cases.elements(); it.hasNext(); ) {
            JsonNode c = it.next();
            String name = c.get("name").asText();
            tests.add(dynamicTest("find_segments " + name, () -> {
                JsonNode inputNode = c.get("input");
                int H = inputNode.size();
                int W = inputNode.get(0).size();
                float[][] pixels = new float[H][W];
                for (int y = 0; y < H; y++) {
                    for (int x = 0; x < W; x++) {
                        pixels[y][x] = (float) inputNode.get(y).get(x).asDouble();
                    }
                }

                float threshold = (float) c.get("segment_threshold").asDouble();
                int minSize = c.get("min_segment_size").asInt();
                int expectedNumSegments = c.get("num_segments").asInt();

                List<FindSegments.Segment> actualSegments =
                        FindSegments.findAllSegments(pixels, threshold, minSize);

                assertEquals(expectedNumSegments, actualSegments.size(),
                        () -> name + ": wrong number of segments");

                // Compare each segment's pixel set (sorted) against expected
                JsonNode segmentsNode = c.get("segments");
                for (int i = 0; i < actualSegments.size(); i++) {
                    FindSegments.Segment seg = actualSegments.get(i);
                    JsonNode expSeg = segmentsNode.get(i);

                    // Sort actual pixels by (y, x)
                    List<int[]> actualPixels = new ArrayList<>(seg.pixels());
                    actualPixels.sort((a, b) -> a[0] != b[0] ? a[0] - b[0] : a[1] - b[1]);

                    final int segI = i;
                    assertEquals(expSeg.size(), actualPixels.size(),
                            () -> name + " seg " + segI + ": size mismatch");

                    for (int j = 0; j < expSeg.size(); j++) {
                        int expY = expSeg.get(j).get(0).asInt();
                        int expX = expSeg.get(j).get(1).asInt();
                        int[] got = actualPixels.get(j);
                        final int pixJ = j;
                        assertEquals(expY, got[0],
                                () -> name + " seg " + segI + " pixel " + pixJ + ": y mismatch");
                        assertEquals(expX, got[1],
                                () -> name + " seg " + segI + " pixel " + pixJ + ": x mismatch");
                    }
                }
            }));
        }
        return tests;
    }
}
