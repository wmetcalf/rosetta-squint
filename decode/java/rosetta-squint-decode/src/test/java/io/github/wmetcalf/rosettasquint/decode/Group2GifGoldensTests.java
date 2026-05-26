package io.github.wmetcalf.rosettasquint.decode;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class Group2GifGoldensTests {
    @Test
    public void byteExactAllGif() throws IOException {
        List<String> fixtures = TestKit.listValidFixtures("gif");
        assertFalse(fixtures.isEmpty(), "should have GIF fixtures");

        List<String> failures = new ArrayList<>();
        for (String rel : fixtures) {
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
                        failures.add(rel + ": pixel byte " + i + " got=" + (gotData[i] & 0xFF) + " want=" + (want.pixels()[i] & 0xFF));
                        break;
                    }
                }
            } catch (DecodeException e) {
                failures.add(rel + ": threw " + e.kind() + ": " + e.detail());
            }
        }
        if (!failures.isEmpty()) {
            fail(failures.size() + " GIF byte-exact failures:\n  " + String.join("\n  ", failures));
        }
    }
}
