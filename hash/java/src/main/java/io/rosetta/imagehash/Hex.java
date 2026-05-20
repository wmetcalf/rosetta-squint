package io.rosetta.imagehash;

import io.rosetta.imagehash.internal.BitPack;

public final class Hex {
    public static ImageHash hexToHash(String hex) {
        boolean[][] bits = BitPack.unpackSquare(hex);
        return new ImageHash(bits);
    }

    public static ImageHash hexToFlathash(String hex, int hashSize) {
        if (hashSize < 1) throw new IllegalArgumentException("hashSize must be >= 1");
        boolean[][] bits = BitPack.unpackFlat(hex, hashSize);
        return new ImageHash(bits);
    }

    private Hex() {}
}
