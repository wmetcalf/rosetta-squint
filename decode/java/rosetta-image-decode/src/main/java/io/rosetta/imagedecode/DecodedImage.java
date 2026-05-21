package io.rosetta.imagedecode;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;

/**
 * An immutable, decoded raster image.
 *
 * <p>Pixel data access:
 * <ul>
 *   <li>{@link #data()} — safe copy; allocates a new array on every call. Use when you need
 *       an independent, mutable copy of the pixels.
 *   <li>{@link #unsafeData()} — zero-copy access to the underlying array. <em>Do not mutate
 *       the returned array</em>; doing so corrupts the immutable state of this object. Use only
 *       in performance-critical paths where you can guarantee read-only access.
 *   <li>{@link #asReadOnlyBuffer()} — zero-copy {@link ByteBuffer} backed by the underlying
 *       array, marked read-only. Suitable for passing to NIO or channel-based APIs that accept
 *       a {@code ByteBuffer} without mutating it.
 * </ul>
 *
 * <p>Constructor contract: the {@code data} array passed to the constructor must be a
 * freshly-allocated buffer owned exclusively by the caller (i.e. not shared with any other
 * live reference). The constructor stores the reference directly without cloning, so the caller
 * must not retain or mutate the array after construction. All internal decoders satisfy this
 * precondition by passing {@code new byte[...]} arrays.
 */
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
        // Store directly: caller guarantees this is a freshly-allocated, exclusively-owned buffer.
        this.data = data;
        this.channels = channels;
        this.format = format;
    }

    public int width() { return width; }
    public int height() { return height; }

    /**
     * Returns a defensive copy of the pixel data. Safe to mutate; allocates a new array each call.
     *
     * @return a new byte array containing a copy of the pixel data
     */
    public byte[] data() { return data.clone(); }

    /**
     * Returns the underlying pixel-data array without copying. <em>Do not mutate the returned
     * array.</em> Mutating it will corrupt the immutable state of this {@code DecodedImage}.
     *
     * @return the backing pixel array (zero-copy)
     */
    public byte[] unsafeData() { return data; }

    /**
     * Returns a read-only {@link ByteBuffer} backed by the underlying pixel array (zero-copy).
     * The buffer's position is 0 and limit is {@code data.length}. Because the buffer is
     * read-only, callers cannot mutate the pixel data through it.
     *
     * @return a read-only view of the pixel data
     */
    public ByteBuffer asReadOnlyBuffer() {
        return ByteBuffer.wrap(data).asReadOnlyBuffer();
    }

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
