package check;

import io.github.wmetcalf.rosettasquint.Squint;

import java.nio.file.Path;

/**
 * Hashes one fixture per decode path a consumer can reach without system
 * libraries: PNG (ImageIO), TIFF (TwelveMonkeys imageio-tiff) and HEIC (the
 * shared WASM decoder on Chicory). Each pulls classes from a different runtime
 * dependency of rosetta-squint / rosetta-squint-decode, so a POM that drops
 * any of them fails here.
 */
public final class Main {
    public static void main(String[] args) throws Exception {
        Path fixtures = Path.of(args[0]);
        String[] files = {
            "png/valid/all-black-32.png",
            "tiff/valid/16x16-rgb-lzw.tif",
            "heic/valid/16x16.heic",
        };
        for (String f : files) {
            System.out.println(f + " phash=" + Squint.phash(fixtures.resolve(f).toString(), 8));
        }
    }
}
