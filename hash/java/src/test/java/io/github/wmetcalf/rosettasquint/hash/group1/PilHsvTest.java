package io.github.wmetcalf.rosettasquint.hash.group1;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.wmetcalf.rosettasquint.hash.internal.PilHsv;
import io.github.wmetcalf.rosettasquint.hash.testkit.SpecPath;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Iterator;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PilHsvTest {
    @Test
    void allCasesMatchSpec() throws IOException {
        JsonNode data = new ObjectMapper().readTree(SpecPath.HSV_CASES.toFile());
        JsonNode cases = data.get("cases");
        int n = 0;
        for (Iterator<JsonNode> it = cases.elements(); it.hasNext(); ) {
            JsonNode c = it.next();
            int r = c.get("rgb").get(0).asInt();
            int g = c.get("rgb").get(1).asInt();
            int b = c.get("rgb").get(2).asInt();
            int[] expected = {
                c.get("hsv").get(0).asInt(),
                c.get("hsv").get(1).asInt(),
                c.get("hsv").get(2).asInt()
            };
            int[] actual = PilHsv.toHsv(r, g, b);
            assertArrayEquals(expected, actual,
                    () -> "RGB(" + r + "," + g + "," + b + ") expected " +
                          "(" + expected[0] + "," + expected[1] + "," + expected[2] + ")");
            n++;
        }
        assertEquals(31, n, "expected ~30 cases in hsv_cases.json");
    }

    @Test
    void negativeHPreWrapCase() {
        // RGB(200,100,150): r==maxc, h_pre = -128, wrap → 1402, h = 233
        assertArrayEquals(new int[]{233, 127, 200}, PilHsv.toHsv(200, 100, 150));
    }

    @Test
    void halfBoundaryFloorNotRound() {
        // RGB(100,150,200): b==maxc, h = 148 (not 149 from naive round)
        assertArrayEquals(new int[]{148, 127, 200}, PilHsv.toHsv(100, 150, 200));
    }

    @Test
    void saturation170Boundary() {
        // RGB(255,85,85): S exactly 170
        assertEquals(170, PilHsv.toHsv(255, 85, 85)[1]);
    }
}
