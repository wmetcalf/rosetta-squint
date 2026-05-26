package io.github.wmetcalf.rosettasquint.hash.group1;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.wmetcalf.rosettasquint.hash.internal.PilGrayscale;
import io.github.wmetcalf.rosettasquint.hash.testkit.SpecPath;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Iterator;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PilGrayscaleTest {
    @Test
    void allCasesMatchSpec() throws IOException {
        JsonNode data = new ObjectMapper().readTree(SpecPath.GRAYSCALE_CASES.toFile());
        JsonNode cases = data.get("cases");
        int n = 0;
        for (Iterator<JsonNode> it = cases.elements(); it.hasNext(); ) {
            JsonNode c = it.next();
            int r = c.get("rgb").get(0).asInt();
            int g = c.get("rgb").get(1).asInt();
            int b = c.get("rgb").get(2).asInt();
            int expected = c.get("L").asInt();
            int actual = PilGrayscale.toGray(r, g, b);
            assertEquals(expected, actual, () -> "RGB(" + r + "," + g + "," + b + ")");
            n++;
        }
        assertEquals(30, n, "expected ~30 cases in grayscale_cases.json");
    }
}
