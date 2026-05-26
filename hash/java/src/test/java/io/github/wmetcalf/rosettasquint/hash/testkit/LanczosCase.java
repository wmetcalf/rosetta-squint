package io.github.wmetcalf.rosettasquint.hash.testkit;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;

public final class LanczosCase {
    public final int srcW, srcH, dstW, dstH;
    public final int[][] src;  // shape [srcH][srcW], values 0..255
    public final int[][] dst;  // shape [dstH][dstW], values 0..255

    private LanczosCase(int srcW, int srcH, int dstW, int dstH, int[][] src, int[][] dst) {
        this.srcW = srcW; this.srcH = srcH; this.dstW = dstW; this.dstH = dstH;
        this.src = src; this.dst = dst;
    }

    public static LanczosCase load(String name) {
        byte[] data;
        try {
            data = Files.readAllBytes(SpecPath.LANCZOS_CASES.resolve(name + ".bin"));
        } catch (IOException e) {
            throw new RuntimeException("Cannot read lanczos case " + name, e);
        }
        ByteBuffer bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        int sw = bb.getInt(), sh = bb.getInt(), dw = bb.getInt(), dh = bb.getInt();
        int off = 16;
        int[][] src = new int[sh][sw];
        for (int y = 0; y < sh; y++)
            for (int x = 0; x < sw; x++)
                src[y][x] = data[off++] & 0xFF;
        int[][] dst = new int[dh][dw];
        for (int y = 0; y < dh; y++)
            for (int x = 0; x < dw; x++)
                dst[y][x] = data[off++] & 0xFF;
        return new LanczosCase(sw, sh, dw, dh, src, dst);
    }
}
