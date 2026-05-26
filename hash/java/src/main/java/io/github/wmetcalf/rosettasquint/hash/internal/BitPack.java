package io.github.wmetcalf.rosettasquint.hash.internal;

import java.math.BigInteger;

/** Boolean array ↔ hex string conversion. Row-major MSB-first. */
public final class BitPack {

    public static String pack(boolean[][] bits) {
        int h = bits.length;
        int w = bits[0].length;
        int total = h * w;
        StringBuilder bs = new StringBuilder(total);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                bs.append(bits[y][x] ? '1' : '0');
        BigInteger value = new BigInteger(bs.toString(), 2);
        int width = (total + 3) / 4;
        String hex = value.toString(16);
        if (hex.length() < width) {
            StringBuilder padded = new StringBuilder(width);
            for (int i = hex.length(); i < width; i++) padded.append('0');
            padded.append(hex);
            return padded.toString();
        }
        return hex;
    }

    public static boolean[][] unpackSquare(String hex) {
        int totalBits = hex.length() * 4;
        int n = (int) Math.round(Math.sqrt(totalBits));
        if (n * n != totalBits) {
            throw new IllegalArgumentException("Hex length " + hex.length() + " doesn't correspond to a square shape (total bits=" + totalBits + ", sqrt=" + Math.sqrt(totalBits) + ")");
        }
        boolean[] flat = hexToBits(hex, totalBits);
        boolean[][] out = new boolean[n][n];
        int idx = 0;
        for (int y = 0; y < n; y++)
            for (int x = 0; x < n; x++)
                out[y][x] = flat[idx++];
        return out;
    }

    public static boolean[][] unpackFlat(String hex, int secondAxis) {
        int totalBits = 14 * secondAxis;
        boolean[] flat = hexToBits(hex, totalBits);
        boolean[][] out = new boolean[14][secondAxis];
        int idx = 0;
        for (int y = 0; y < 14; y++)
            for (int x = 0; x < secondAxis; x++)
                out[y][x] = flat[idx++];
        return out;
    }

    private static boolean[] hexToBits(String hex, int totalBits) {
        if (!hex.matches("[0-9a-f]+"))
            throw new IllegalArgumentException("Invalid hex: " + hex);
        BigInteger value = new BigInteger(hex, 16);
        boolean[] bits = new boolean[totalBits];
        for (int i = 0; i < totalBits; i++) {
            bits[totalBits - 1 - i] = value.testBit(i);
        }
        return bits;
    }

    private BitPack() {}
}
