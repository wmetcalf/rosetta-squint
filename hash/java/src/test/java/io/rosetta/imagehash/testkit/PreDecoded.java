package io.rosetta.imagehash.testkit;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;

public final class PreDecoded {

    /** Reads spec/decoded/<name>.rgb.bin and returns a TYPE_INT_RGB BufferedImage. */
    public static BufferedImage loadAsBufferedImage(String name) {
        byte[] data;
        try {
            data = Files.readAllBytes(SpecPath.DECODED.resolve(name + ".rgb.bin"));
        } catch (IOException e) {
            throw new RuntimeException("Cannot read decoded buffer for " + name, e);
        }
        ByteBuffer bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        int w = bb.getInt();
        int h = bb.getInt();
        if (data.length != 8 + w * h * 3) {
            throw new IllegalStateException("Length mismatch in " + name + ": expected " + (8 + w * h * 3) + ", got " + data.length);
        }
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        int off = 8;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int r = data[off] & 0xFF;
                int g = data[off + 1] & 0xFF;
                int b = data[off + 2] & 0xFF;
                img.setRGB(x, y, (r << 16) | (g << 8) | b);
                off += 3;
            }
        }
        return img;
    }

    private PreDecoded() {}
}
