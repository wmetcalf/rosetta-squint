package io.github.wmetcalf.rosettasquint.cli;

import io.github.wmetcalf.rosettasquint.Squint;

/**
 * squint-cli — compute a perceptual image hash and print the hex string to stdout.
 *
 * <p>Used by tools/cross-squint-diff for live cross-port equivalence checking.
 *
 * <p>Usage: squint-cli &lt;algo&gt; &lt;size&gt; &lt;path&gt;
 * <ul>
 *   <li>algo  — one of: phash, phash_simple, dhash, dhash_vertical, average_hash,
 *               whash_haar, whash_db4, whash_db4_robust, colorhash, crop_resistant_hash</li>
 *   <li>size  — hash_size (or binbits for colorhash); pass "-" for crop_resistant_hash</li>
 *   <li>path  — path to the image file</li>
 * </ul>
 *
 * <p>Exit: 0 on success, 1 on hash/decode error, 2 on usage error.
 */
public final class SquintCli {
    private SquintCli() {}

    public static void main(String[] args) {
        if (args.length != 3) {
            System.err.println("usage: squint-cli <algo> <size> <path>");
            System.exit(2);
        }
        String algo = args[0];
        int size = 8;
        try {
            size = Integer.parseInt(args[1]);
        } catch (NumberFormatException ignored) {
            // "-" placeholder for crop_resistant_hash — size stays 8 (unused)
        }
        String path = args[2];

        String hex;
        try {
            hex = switch (algo) {
                case "phash"              -> Squint.phash(path, size).toString();
                case "phash_simple"       -> Squint.phashSimple(path, size).toString();
                case "dhash"              -> Squint.dhash(path, size).toString();
                case "dhash_vertical"     -> Squint.dhashVertical(path, size).toString();
                case "average_hash"       -> Squint.averageHash(path, size).toString();
                case "whash_haar"         -> Squint.whashHaar(path, size).toString();
                case "whash_db4"          -> Squint.whashDb4(path, size).toString();
                case "whash_db4_robust"   -> Squint.whashDb4Robust(path, size).toString();
                case "colorhash"          -> Squint.colorhash(path, size).toString();
                case "crop_resistant_hash"-> Squint.cropResistantHash(path).toString();
                default -> {
                    System.err.println("unknown algo: " + algo);
                    System.exit(2);
                    yield "";
                }
            };
        } catch (Exception e) {
            System.err.println("error: " + e.getMessage());
            System.exit(1);
            return;
        }
        System.out.println(hex);
    }
}
