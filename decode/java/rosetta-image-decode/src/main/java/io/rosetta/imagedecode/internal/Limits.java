package io.rosetta.imagedecode.internal;

import io.rosetta.imagedecode.DecodeException;
import io.rosetta.imagedecode.Format;

public final class Limits {
    private Limits() {}

    /** Maximum allowed pixel count (width × height). Equal to 256 MiB of pixels = 268,435,456. */
    public static final long MAX_PIXELS = 256L * 1024L * 1024L;

    /**
     * Validates image dimensions: both must be positive and width × height must not exceed
     * {@link #MAX_PIXELS}. Call this after reading dimensions from the image header, before
     * allocating any pixel buffer.
     *
     * @throws DecodeException with kind {@link DecodeException.Kind#CORRUPT_INPUT} for non-positive
     *     dimensions, or kind {@link DecodeException.Kind#IMAGE_TOO_LARGE} if the pixel count
     *     exceeds {@code MAX_PIXELS}.
     */
    public static void checkDimensions(int width, int height, Format format) throws DecodeException {
        if (width <= 0 || height <= 0) {
            throw new DecodeException(DecodeException.Kind.CORRUPT_INPUT, format,
                "non-positive dimensions " + width + "x" + height);
        }
        long pixels = (long) width * (long) height;  // long arithmetic, no overflow
        if (pixels > MAX_PIXELS) {
            throw new DecodeException(DecodeException.Kind.IMAGE_TOO_LARGE, format,
                "declared dimensions " + width + "x" + height + " = " + pixels
                + " pixels exceeds MAX_PIXELS = " + MAX_PIXELS);
        }
    }
}
