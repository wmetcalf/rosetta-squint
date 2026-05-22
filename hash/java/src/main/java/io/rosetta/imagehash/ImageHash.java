package io.rosetta.imagehash;

import io.rosetta.imagehash.internal.BitPack;

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
