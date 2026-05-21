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
            case GIF -> io.rosetta.imagedecode.internal.GIFDecoder.decode(bytes);
            case JPEG -> io.rosetta.imagedecode.internal.JPEGDecoder.decode(bytes);
            case WEBP -> io.rosetta.imagedecode.internal.WebPDecoder.decode(bytes);
            case TIFF -> io.rosetta.imagedecode.internal.TIFFDecoder.decode(bytes);
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
        if (bytes.length >= 6
            && bytes[0] == 0x47 && bytes[1] == 0x49 && bytes[2] == 0x46 && bytes[3] == 0x38
            && (bytes[4] == 0x37 || bytes[4] == 0x39) && bytes[5] == 0x61) {
            return Optional.of(Format.GIF);
        }
        if (bytes.length >= 2 && bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xD8) {
            return Optional.of(Format.JPEG);
        }
        if (bytes.length >= 12
            && bytes[0] == 0x52 && bytes[1] == 0x49 && bytes[2] == 0x46 && bytes[3] == 0x46
            && bytes[8] == 0x57 && bytes[9] == 0x45 && bytes[10] == 0x42 && bytes[11] == 0x50) {
            return Optional.of(Format.WEBP);
        }
        // TIFF: little-endian "II\x2A\x00" or big-endian "MM\x00\x2A"
        if (bytes.length >= 4
            && ((bytes[0] == 0x49 && bytes[1] == 0x49 && bytes[2] == 0x2A && bytes[3] == 0x00)
             || (bytes[0] == 0x4D && bytes[1] == 0x4D && bytes[2] == 0x00 && bytes[3] == 0x2A))) {
            return Optional.of(Format.TIFF);
        }
        return Optional.empty();
    }

    public static Set<Format> supportedFormats() {
        return EnumSet.of(Format.BMP, Format.PNG, Format.GIF, Format.JPEG, Format.WEBP, Format.TIFF);
    }
}
