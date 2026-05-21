package io.rosetta.imagedecode;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

public final class Decoder {
    private Decoder() {}

    public static DecodedImage decode(byte[] bytes) throws DecodeException {
        Optional<Format> fmt = detectFormat(bytes);
        if (fmt.isEmpty()) {
            throw new DecodeException(DecodeException.Kind.UNSUPPORTED_FORMAT, null, "");
        }
        return switch (fmt.get()) {
            case BMP -> io.rosetta.imagedecode.internal.BMPDecoder.decode(bytes);
            default -> throw new DecodeException(DecodeException.Kind.UNSUPPORTED_FORMAT, fmt.get(), "");
        };
    }

    public static Optional<Format> detectFormat(byte[] bytes) {
        if (bytes == null || bytes.length < 2) return Optional.empty();
        if (bytes[0] == 0x42 && bytes[1] == 0x4D) return Optional.of(Format.BMP);
        return Optional.empty();
    }

    public static Set<Format> supportedFormats() {
        return EnumSet.of(Format.BMP);
    }
}
