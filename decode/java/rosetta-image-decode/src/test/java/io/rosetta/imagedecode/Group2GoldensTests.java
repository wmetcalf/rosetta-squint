package io.rosetta.imagedecode;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.regex.Pattern;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

public class Group2GoldensTests {
    /** Files matching Tier 1: BI_RGB 24/32/8-paletted. */
    private static final Pattern TIER1 = Pattern.compile(
        "bmp/valid/(?:rgb24-|rgba32-|pal8|real-bmpsuite-g-rgb24|real-bmpsuite-g-pal8gs).*\\.bmp"
    );

    @Test
    public void byteExactTier1() throws IOException {
        List<String> fixtures = TestKit.listValidFixtures("bmp");
        List<String> tier1 = fixtures.stream().filter(p -> TIER1.matcher(p).matches()).toList();
        assertFalse(tier1.isEmpty(), "should have Tier-1 fixtures");

        List<String> failures = new ArrayList<>();
        for (String rel : tier1) {
            byte[] input = TestKit.readFixture(rel);
            try {
                DecodedImage got = Decoder.decode(input);
                TestKit.DecodedGolden want = TestKit.readGolden(rel);
                if (got.width() != want.width() || got.height() != want.height() || got.channels().bytesPerPixel() != want.channels()) {
                    failures.add(rel + ": shape " + got.width() + "x" + got.height() + "c" + got.channels().bytesPerPixel()
                        + " != " + want.width() + "x" + want.height() + "c" + want.channels());
                    continue;
                }
                byte[] gotData = got.data();
                if (gotData.length != want.pixels().length) {
                    failures.add(rel + ": pixel byte count " + gotData.length + " != " + want.pixels().length);
                    continue;
                }
                for (int i = 0; i < gotData.length; i++) {
                    if (gotData[i] != want.pixels()[i]) {
                        failures.add(rel + ": pixel byte " + i + " " + (gotData[i] & 0xFF) + " != " + (want.pixels()[i] & 0xFF));
                        break; // one failure per fixture is enough
                    }
                }
            } catch (DecodeException e) {
                failures.add(rel + ": threw " + e.kind() + ": " + e.detail());
            }
        }
        if (!failures.isEmpty()) {
            fail(failures.size() + " Tier-1 failures:\n  " + String.join("\n  ", failures));
        }
    }
}
