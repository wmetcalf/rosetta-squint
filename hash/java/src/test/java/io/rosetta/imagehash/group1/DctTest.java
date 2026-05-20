package io.rosetta.imagehash.group1;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.rosetta.imagehash.internal.Dct;
import io.rosetta.imagehash.testkit.SpecPath;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DctTest {
    private static final double TOL = 1e-9;

    @Test
    void oneDimMatchesScipy() throws IOException {
        JsonNode data = new ObjectMapper().readTree(SpecPath.DCT_CASES.toFile());
        int n = data.get("n").asInt();
        JsonNode cases = data.get("cases");
        for (Iterator<Map.Entry<String, JsonNode>> it = cases.fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> e = it.next();
            String name = e.getKey();
            JsonNode caseNode = e.getValue();
            double[] input = new double[n];
            for (int i = 0; i < n; i++) input[i] = caseNode.get("input").get(i).asDouble();
            double[] expected = new double[n];
            for (int i = 0; i < n; i++) expected[i] = caseNode.get("output").get(i).asDouble();
            double[] actual = Dct.dct1d(input);
            assertEquals(n, actual.length, name + ": length");
            for (int k = 0; k < n; k++) {
                int kk = k;
                assertTrue(Math.abs(actual[kk] - expected[kk]) < TOL,
                        () -> name + ": k=" + kk + " expected " + expected[kk] + ", got " + actual[kk] + ", diff " + (actual[kk] - expected[kk]));
            }
        }
    }

    @Test
    void arangeFirstOutputIs992() {
        double[] x = new double[32];
        for (int i = 0; i < 32; i++) x[i] = i;
        double[] y = Dct.dct1d(x);
        // y[0] = 2 * sum(x[n] * cos(0)) = 2 * sum(0..31) = 2 * 496 = 992
        assertEquals(992.0, y[0], 1e-9);
    }
}
