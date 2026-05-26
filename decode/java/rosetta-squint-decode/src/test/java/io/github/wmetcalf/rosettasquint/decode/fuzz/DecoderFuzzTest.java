package io.github.wmetcalf.rosettasquint.decode.fuzz;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;
import io.github.wmetcalf.rosettasquint.decode.DecodeException;
import io.github.wmetcalf.rosettasquint.decode.Decoder;

/**
 * Coverage-guided fuzz for the decoder. Property: any input either produces
 * a valid DecodedImage or throws a DecodeException — never anything else,
 * never a JVM crash via JNI (libheif / libwebp / libjpeg-turbo).
 *
 * Excluded from the default {@code mvn test} run via a surefire exclude
 * pattern (see pom.xml). Run with {@code mvn -Pfuzz -Dtest='DecoderFuzzTest' test}.
 */
public class DecoderFuzzTest {

	@FuzzTest(maxDuration = "60s")
	void decodeAny(FuzzedDataProvider data) {
		byte[] bytes = data.consumeRemainingAsBytes();
		try {
			Decoder.decode(bytes);
		} catch (DecodeException ignored) {
			// expected on any malformed / unsupported input
		}
	}

	@FuzzTest(maxDuration = "60s")
	void decodeWithPrefix(FuzzedDataProvider data) {
		byte[][] prefixes = {
			{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A},
			{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0},
			{'G', 'I', 'F', '8', '9', 'a'},
			{'B', 'M'},
			{'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'},
			{'I', 'I', 0x2A, 0x00},
			{0, 0, 0, 0x18, 'f', 't', 'y', 'p', 'h', 'e', 'i', 'c'},
		};
		byte[] prefix = prefixes[data.consumeInt(0, prefixes.length - 1)];
		byte[] body = data.consumeRemainingAsBytes();
		byte[] combined = new byte[prefix.length + body.length];
		System.arraycopy(prefix, 0, combined, 0, prefix.length);
		System.arraycopy(body, 0, combined, prefix.length, body.length);
		try {
			Decoder.decode(combined);
		} catch (DecodeException ignored) {
			// expected on malformed input behind a valid magic-byte prefix
		}
	}
}
