package io.rosetta.imagedecode;

import java.util.Arrays;
import java.util.Objects;

public final class DecodedImage {
    private final int width;
    private final int height;
    private final byte[] data;
    private final Channels channels;
    private final Format format;

    public DecodedImage(int width, int height, byte[] data, Channels channels, Format format) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("width/height must be positive");
        }
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(channels, "channels");
        Objects.requireNonNull(format, "format");
        if (data.length != width * height * channels.bytesPerPixel()) {
            throw new IllegalArgumentException(
                "data length " + data.length + " != " + width + "*" + height + "*" + channels.bytesPerPixel()
            );
        }
        this.width = width;
        this.height = height;
        this.data = data.clone();
        this.channels = channels;
        this.format = format;
    }

    public int width() { return width; }
    public int height() { return height; }
    public byte[] data() { return data.clone(); }
    public Channels channels() { return channels; }
    public Format format() { return format; }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof DecodedImage d)) return false;
        return width == d.width
            && height == d.height
            && channels == d.channels
            && format == d.format
            && Arrays.equals(data, d.data);
    }

    @Override
    public int hashCode() {
        return Objects.hash(width, height, channels, format) * 31 + Arrays.hashCode(data);
    }
}
