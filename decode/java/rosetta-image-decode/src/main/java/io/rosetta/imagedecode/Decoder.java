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
            case PNG -> io.rosetta.imagedecode.internal.PNGDecoder.decode(bytes);
            default -> throw new DecodeException(DecodeException.Kind.UNSUPPORTED_FORMAT, fmt.get(), "");
        };
    }

    public static Optional<Format> detectFormat(byte[] bytes) {
        if (bytes == null || bytes.length < 2) return Optional.empty();
        if (bytes[0] == 0x42 && bytes[1] == 0x4D) return Optional.of(Format.BMP);
        if (bytes.length >= 8
            && bytes[0] == (byte) 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4E && bytes[3] == 0x47
            && bytes[4] == 0x0D && bytes[5] == 0x0A && bytes[6] == 0x1A && bytes[7] == 0x0A) {
            return Optional.of(Format.PNG);
        }
        return Optional.empty();
    }

    public static Set<Format> supportedFormats() {
        return EnumSet.of(Format.BMP, Format.PNG);
    }
}
