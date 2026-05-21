package io.rosetta.imagedecode;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TestKit {
    /** Path to the shared /spec/ dir, relative to the Maven module root (where `mvn test` runs). */
    public static final Path SPEC_DIR = Paths.get("..", "..", "spec");

    private TestKit() {}

    public static byte[] readFixture(String relPath) throws IOException {
        return Files.readAllBytes(SPEC_DIR.resolve("fixtures").resolve(relPath));
    }

    /** Reads a decoded/<rel>.bin golden, returning (width, height, channels, pixelBytes). */
    public static DecodedGolden readGolden(String fixtureRel) throws IOException {
        byte[] blob = Files.readAllBytes(SPEC_DIR.resolve("decoded").resolve(fixtureRel + ".bin"));
        if (blob.length < 12) {
            throw new IOException("golden " + fixtureRel + ".bin too short: " + blob.length);
        }
        ByteBuffer bb = ByteBuffer.wrap(blob, 0, 12).order(ByteOrder.LITTLE_ENDIAN);
        int width = bb.getInt();
        int height = bb.getInt();
        int channels = blob[8] & 0xFF;
        byte[] pixels = new byte[blob.length - 12];
        System.arraycopy(blob, 12, pixels, 0, pixels.length);
        return new DecodedGolden(width, height, channels, pixels);
    }

    public record DecodedGolden(int width, int height, int channels, byte[] pixels) {}

    /** Returns a list of (relative_path) of every valid fixture under fixtures/<format>/valid/. */
    public static List<String> listValidFixtures(String format) throws IOException {
        Path dir = SPEC_DIR.resolve("fixtures").resolve(format).resolve("valid");
        try (var stream = Files.list(dir)) {
            return stream.filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().endsWith("." + format))
                .map(p -> format + "/valid/" + p.getFileName().toString())
                .sorted()
                .toList();
        }
    }

    /** Reads errors.json and returns an ordered map of fixture path → expected error info. */
    public static Map<String, ExpectedError> readErrors() throws IOException {
        String json = Files.readString(SPEC_DIR.resolve("errors.json"));
        Map<String, ExpectedError> out = new LinkedHashMap<>();
        // Minimal JSON parsing: locate every fixture entry by regex.
        // errors.json structure: { "schema_version": 1, "fixtures": { "<key>": { "format": ..., "expected_kind": ..., "expected_detail_substring": "..." }, ... } }
        Pattern entry = Pattern.compile(
            "\"(?<key>[^\"]+)\"\\s*:\\s*\\{\\s*" +
            "\"format\"\\s*:\\s*(?:\"(?<format>[^\"]+)\"|null)\\s*,\\s*" +
            "\"expected_kind\"\\s*:\\s*\"(?<kind>[^\"]+)\"\\s*,\\s*" +
            "\"expected_detail_substring\"\\s*:\\s*\"(?<detail>[^\"]*)\"\\s*\\}",
            Pattern.DOTALL);
        // Only consider entries inside the "fixtures" object (skip top-level schema_version which won't match the entry shape anyway).
        Matcher m = entry.matcher(json);
        while (m.find()) {
            String key = m.group("key");
            // Skip the top-level keys (schema_version, fixtures) which don't match the entry shape but be defensive
            if (key.equals("schema_version") || key.equals("fixtures")) continue;
            out.put(key, new ExpectedError(m.group("format"), m.group("kind"), m.group("detail")));
        }
        return out;
    }

    public record ExpectedError(String format, String expectedKind, String expectedDetailSubstring) {}
}
