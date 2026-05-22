package io.rosetta.imagedecode;

public enum Channels {
    RGB(3),
    RGBA(4);

    private final int bytesPerPixel;

    Channels(int bytesPerPixel) {
        this.bytesPerPixel = bytesPerPixel;
    }

    public int bytesPerPixel() {
        return bytesPerPixel;
    }
}
