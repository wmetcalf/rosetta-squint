package io.rosetta.imagehash.fuzz;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;
import io.rosetta.imagehash.Hex;

/**
 * Coverage-guided fuzz for the hex parsers. Property: never throw anything
 * other than the documented IllegalArgumentException; never SIGSEGV the JVM.
 *
 * Excluded from the default {@code mvn test} run via a surefire exclude
 * pattern (see pom.xml). Run with {@code mvn -Pfuzz -Dtest='HexParserFuzzTest' test}.
 */
public class HexParserFuzzTest {

	@FuzzTest(maxDuration = "60s")
	void hexToHash(FuzzedDataProvider data) {
		String s = data.consumeRemainingAsString();
		try {
			Hex.hexToHash(s);
		} catch (IllegalArgumentException ignored) {
			// expected on malformed input
		}
	}

	@FuzzTest(maxDuration = "60s")
	void hexToFlathash(FuzzedDataProvider data) {
		int size = data.consumeInt(0, 255);
		String s = data.consumeRemainingAsString();
		try {
			Hex.hexToFlathash(s, size);
		} catch (IllegalArgumentException ignored) {
			// expected on malformed input or hashSize < 1
		}
	}

	@FuzzTest(maxDuration = "60s")
	void hexToMultiHash(FuzzedDataProvider data) {
		String s = data.consumeRemainingAsString();
		try {
			Hex.hexToMultiHash(s);
		} catch (IllegalArgumentException ignored) {
			// expected on malformed input
		}
	}
}
