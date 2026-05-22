// decode-cli — decode an image and emit raw bytes (spec/SPEC.md §2 wire
// format: 12-byte header + row-major pixels) to stdout.
//
// Used by tools/cross-port-diff/diff_all.py for live cross-port equivalence.
//
// Usage: java -jar decode-cli.jar <fixture.path>
// Exit:  0 on success, 1 on decode error, 2 on harness error.
package io.rosetta.imagedecode.cli;

import io.rosetta.imagedecode.Channels;
import io.rosetta.imagedecode.DecodeException;
import io.rosetta.imagedecode.DecodedImage;
import io.rosetta.imagedecode.Decoder;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

public final class DecodeCli {
    private DecodeCli() {}

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("usage: decode-cli <fixture>");
            System.exit(2);
        }
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(Path.of(args[0]));
        } catch (IOException e) {
            System.err.println("read " + args[0] + ": " + e.getMessage());
            System.exit(2);
            return;
        }
        DecodedImage img;
        try {
            img = Decoder.decode(bytes);
        } catch (DecodeException e) {
            System.err.println("decode error: " + e.kind() + ": " + e.detail());
            System.exit(1);
            return;
        }
        byte channels = img.channels() == Channels.RGBA ? (byte) 4 : (byte) 3;
        ByteBuffer hdr = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN);
        hdr.putInt(img.width());
        hdr.putInt(img.height());
        hdr.put(channels);
        hdr.put((byte) 0);
        hdr.put((byte) 0);
        hdr.put((byte) 0);
        try (BufferedOutputStream out = new BufferedOutputStream(System.out)) {
            out.write(hdr.array());
            out.write(img.unsafeData());
            out.flush();
        } catch (IOException e) {
            System.err.println("write: " + e.getMessage());
            System.exit(2);
        }
    }
}
