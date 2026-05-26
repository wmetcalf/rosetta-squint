package io.github.wmetcalf.rosettasquint.hash.group1;

import io.github.wmetcalf.rosettasquint.hash.internal.BufferedImageRgb;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BufferedImageRgbTest {
    @Test
    void typeIntRgbPassesThrough() {
        BufferedImage img = new BufferedImage(2, 1, BufferedImage.TYPE_INT_RGB);
        img.setRGB(0, 0, 0xFF0000);
        img.setRGB(1, 0, 0x00FF00);
        int[][] rgb = BufferedImageRgb.toIntArray(img);
        assertEquals(1, rgb.length, "height");
        assertEquals(2, rgb[0].length, "width");
        assertEquals(0xFF0000, rgb[0][0] & 0xFFFFFF);
        assertEquals(0x00FF00, rgb[0][1] & 0xFFFFFF);
    }

    @Test
    void typeIntArgbCompositesOnBlack() {
        BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        img.setRGB(0, 0, 0x00FFFFFF);  // fully transparent white
        int[][] rgb = BufferedImageRgb.toIntArray(img);
        assertEquals(0x000000, rgb[0][0] & 0xFFFFFF, "transparent pixel composites against black");
    }

    @Test
    void typeIntArgbSemiTransparent() {
        BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        img.setRGB(0, 0, 0x80FF0000);  // 50% transparent red
        int[][] rgb = BufferedImageRgb.toIntArray(img);
        int r = (rgb[0][0] >> 16) & 0xFF;
        assertEquals(0, (rgb[0][0] >> 8) & 0xFF, "G channel");
        assertEquals(0, rgb[0][0] & 0xFF, "B channel");
        org.junit.jupiter.api.Assertions.assertTrue(r > 50 && r < 200,
                "expected R in (50, 200), got " + r);
    }

    @Test
    void shapeMatchesImageSize() {
        BufferedImage img = new BufferedImage(5, 3, BufferedImage.TYPE_INT_RGB);
        int[][] rgb = BufferedImageRgb.toIntArray(img);
        assertEquals(3, rgb.length, "H");
        assertEquals(5, rgb[0].length, "W");
    }
}
