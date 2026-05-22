package io.rosetta.imagedecode.internal.libwebp;

import com.sun.jna.Structure;

import java.util.List;

/**
 * Maps the C struct WebPBitstreamFeatures from {@code <webp/decode.h>}.
 *
 * <pre>
 * struct WebPBitstreamFeatures {
 *   int width;
 *   int height;
 *   int has_alpha;
 *   int has_animation;
 *   int format;
 *   uint32_t pad[5];
 * };
 * </pre>
 *
 * All fields are 32-bit; the pad array is represented as five ints so that the
 * struct size matches the native layout exactly.
 */
public class WebPBitstreamFeatures extends Structure {

    public int width;
    public int height;
    /** Non-zero if the bitstream contains an alpha channel. */
    public int has_alpha;
    /** Non-zero if the bitstream is an animation. */
    public int has_animation;
    /** 0 = undefined/mixed, 1 = lossy, 2 = lossless. */
    public int format;
    /** Reserved padding — must be present to match native struct size. */
    public int[] pad = new int[5];

    public WebPBitstreamFeatures() {
        super();
    }

    @Override
    protected List<String> getFieldOrder() {
        return List.of("width", "height", "has_alpha", "has_animation", "format", "pad");
    }
}
