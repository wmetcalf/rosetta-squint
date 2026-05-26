package io.github.wmetcalf.rosettasquint.hash.testkit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public final class Goldens {
    private static volatile JsonNode root;

    public static JsonNode root() {
        JsonNode local = root;
        if (local == null) {
            synchronized (Goldens.class) {
                local = root;
                if (local == null) {
                    try {
                        local = new ObjectMapper().readTree(SpecPath.GOLDENS_JSON.toFile());
                        root = local;
                    } catch (IOException e) {
                        throw new RuntimeException("Cannot read " + SpecPath.GOLDENS_JSON, e);
                    }
                }
            }
        }
        return local;
    }

    /** Returns triples (fixtureName, sizeOrBinbits, expectedHex). Skips null hex entries (small fixtures for whash). */
    public static List<Triple> algorithmCases(String algorithm) {
        JsonNode algos = root().get("algorithms");
        if (algos == null || !algos.has(algorithm))
            throw new IllegalArgumentException("Algorithm not in goldens: " + algorithm);
        JsonNode fixtures = algos.get(algorithm).get("fixtures");
        List<Triple> out = new ArrayList<>();
        Iterator<Map.Entry<String, JsonNode>> fixIter = fixtures.fields();
        while (fixIter.hasNext()) {
            Map.Entry<String, JsonNode> fixEntry = fixIter.next();
            String fixture = fixEntry.getKey();
            Iterator<Map.Entry<String, JsonNode>> sizeIter = fixEntry.getValue().fields();
            while (sizeIter.hasNext()) {
                Map.Entry<String, JsonNode> sizeEntry = sizeIter.next();
                int size = Integer.parseInt(sizeEntry.getKey());
                JsonNode hexNode = sizeEntry.getValue();
                if (hexNode.isNull()) continue;
                out.add(new Triple(fixture, size, hexNode.asText()));
            }
        }
        return out;
    }

    public record Triple(String fixture, int size, String hex) {}

    private Goldens() {}
}
