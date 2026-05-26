package io.github.wmetcalf.rosettasquint.hash.group1;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.wmetcalf.rosettasquint.hash.internal.PilMedianFilter;
import io.github.wmetcalf.rosettasquint.hash.testkit.SpecPath;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

class PilMedianFilterTest {

    @TestFactory
    List<DynamicTest> allCasesMatchSpec() throws IOException {
        JsonNode data = new ObjectMapper().readTree(SpecPath.MEDIAN_FILTER_CASES.toFile());
        JsonNode cases = data.get("cases");
        List<DynamicTest> tests = new ArrayList<>();
        for (Iterator<JsonNode> it = cases.elements(); it.hasNext(); ) {
            JsonNode c = it.next();
            String name = c.get("name").asText();
            tests.add(dynamicTest("median_filter " + name, () -> {
                JsonNode inputNode = c.get("input");
                JsonNode outputNode = c.get("output");
                int H = inputNode.size();
                int W = inputNode.get(0).size();
                int[][] input = new int[H][W];
                int[][] expected = new int[H][W];
                for (int y = 0; y < H; y++) {
                    for (int x = 0; x < W; x++) {
                        input[y][x] = inputNode.get(y).get(x).asInt();
                        expected[y][x] = outputNode.get(y).get(x).asInt();
                    }
                }
                int[][] actual = PilMedianFilter.apply(input);
                for (int y = 0; y < H; y++) {
                    for (int x = 0; x < W; x++) {
                        int ey = y, ex = x;
                        assertEquals(expected[ey][ex], actual[ey][ex],
                                () -> name + " diff at (" + ey + "," + ex + ")");
                    }
                }
            }));
        }
        return tests;
    }
}
