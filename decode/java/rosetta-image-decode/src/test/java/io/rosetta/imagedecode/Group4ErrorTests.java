package io.rosetta.imagedecode;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class Group4ErrorTests {
    @Test
    public void allBmpInvalidFixturesThrowExpectedKindAndDetail() throws IOException {
        Map<String, TestKit.ExpectedError> errors = TestKit.readErrors();
        List<String> failures = new ArrayList<>();

        for (Map.Entry<String, TestKit.ExpectedError> entry : errors.entrySet()) {
            String key = entry.getKey();
            if (!key.startsWith("bmp/") && !key.startsWith("png/") && !key.startsWith("gif/")) continue;
            TestKit.ExpectedError expected = entry.getValue();

            byte[] bytes;
            try {
                bytes = TestKit.readFixture(key);
            } catch (IOException e) {
                failures.add(key + ": cannot read fixture: " + e.getMessage());
                continue;
            }

            try {
                Decoder.decode(bytes);
                failures.add(key + ": decode succeeded, expected " + expected.expectedKind());
            } catch (DecodeException ex) {
                String expectedKind = expected.expectedKind();
                String actualKind = switch (ex.kind()) {
                    case UNSUPPORTED_FORMAT -> "unsupportedFormat";
                    case CORRUPT_INPUT -> "corruptInput";
                    case TRUNCATED -> "truncated";
                    case UNSUPPORTED_FEATURE -> "unsupportedFeature";
                };
                if (!actualKind.equals(expectedKind)) {
                    failures.add(key + ": kind " + actualKind + " != expected " + expectedKind);
                    continue;
                }
                String detail = ex.detail() != null ? ex.detail() : "";
                if (!expected.expectedDetailSubstring().isEmpty() && !detail.contains(expected.expectedDetailSubstring())) {
                    failures.add(key + ": detail '" + detail + "' does not contain '" + expected.expectedDetailSubstring() + "'");
                }
            } catch (Exception other) {
                failures.add(key + ": threw unexpected " + other.getClass().getSimpleName() + ": " + other.getMessage());
            }
        }

        if (!failures.isEmpty()) {
            fail(failures.size() + " Group-4 failures:\n  " + String.join("\n  ", failures));
        }
    }
}
