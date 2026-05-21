package io.rosetta.imagedecode.internal;

import io.rosetta.imagedecode.DecodeException;
import io.rosetta.imagedecode.DecodedImage;
import io.rosetta.imagedecode.Format;

public final class BMPDecoder {
    private BMPDecoder() {}

    public static DecodedImage decode(byte[] bytes) throws DecodeException {
        throw new DecodeException(DecodeException.Kind.UNSUPPORTED_FEATURE, Format.BMP, "BMP decoder not yet implemented");
    }
}
