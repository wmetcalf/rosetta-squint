package io.github.wmetcalf.rosettasquint.hash;

import io.github.wmetcalf.rosettasquint.hash.internal.BitPack;

import java.util.Arrays;
import java.util.Objects;

/**
 * Container for a 2-D boolean hash. Backed by a row-major boolean[][]
 * of shape (height, width).
 */
public final class ImageHash {
    private final boolean[][] bits;

    public ImageHash(boolean[][] bits) {
        Objects.requireNonNull(bits, "bits");
        if (bits.length == 0 || bits[0].length == 0)
            throw new IllegalArgumentException("bits must be non-empty");
        int w = bits[0].length;
        for (boolean[] row : bits) {
            if (row.length != w)
                throw new IllegalArgumentException("bits must be rectangular; got width " + row.length + " vs " + w);
        }
        this.bits = bits;
    }

    public String toHex() {
        return BitPack.pack(bits);
    }

    public int subtract(ImageHash other) {
        Objects.requireNonNull(other, "other");
        if (other.bits.length != bits.length || other.bits[0].length != bits[0].length)
            throw new IllegalArgumentException("ImageHash shapes don't match: this=" + shape() + ", other=" + other.shape());
        int diff = 0;
        for (int y = 0; y < bits.length; y++)
            for (int x = 0; x < bits[0].length; x++)
                if (bits[y][x] != other.bits[y][x]) diff++;
        return diff;
    }

    public int bitCount() {
        return bits.length * bits[0].length;
    }

    private String shape() {
        return "(" + bits.length + "," + bits[0].length + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ImageHash other)) return false;
        if (other.bits.length != bits.length) return false;
        for (int y = 0; y < bits.length; y++)
            if (!Arrays.equals(other.bits[y], bits[y])) return false;
        return true;
    }

    /**
     * Returns a full 32-bit Java {@code int} hash derived from every row's
     * boolean array. Intentionally differs from Python {@code imagehash.ImageHash.__hash__},
     * which deliberately truncates to an 8-bit bucket value to support
     * coarse-grained bucketing of similar hashes (see Python source comment).
     *
     * <p><b>Migrating from Python?</b> If your Python code relied on
     * {@code hash(img_hash) & 0xFF} for 8-bit bucket compression, replicate it
     * here as {@code imgHash.hashCode() & 0xFF}. The low 8 bits are not
     * guaranteed bit-for-bit identical to Python's value (the algorithms differ),
     * but both reduce a hash to a single byte usable as a bucket key.
     *
     * <p>This implementation does NOT truncate so that the full-int variant can
     * be used as a regular Java {@code Object} hashCode in {@link java.util.HashMap}
     * and {@link java.util.HashSet} with proper collision resistance.
     */
    @Override
    public int hashCode() {
        int h = 1;
        for (boolean[] row : bits) {
            h = 31 * h + Arrays.hashCode(row);
        }
        return h;
    }

    @Override
    public String toString() {
        return toHex();
    }
}
