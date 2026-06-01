package io.github.wmetcalf.rosettasquint.decode.internal;

import com.dylibso.chicory.runtime.ImportValues;
import com.dylibso.chicory.runtime.Instance;
import com.dylibso.chicory.runtime.Memory;
import com.dylibso.chicory.wasi.WasiOptions;
import com.dylibso.chicory.wasi.WasiPreview1;
import com.dylibso.chicory.wasm.Parser;
import com.dylibso.chicory.wasm.WasmModule;

import java.io.InputStream;

/**
 * Decodes HEIC via the shared libheif+libde265 WASM module (decode/wasm/), run
 * in Chicory (pure-JVM, no JNA, no system libheif). The same WASM artifact is
 * used by every rosetta-squint port, so HEIC output is byte-identical across
 * languages. See decode/spec/HEIC_REPRODUCIBILITY.md.
 */
public final class HeicWasm {

    // libheif enum values (heif.h)
    private static final int CS_RGB = 1;
    private static final int CHROMA_RGB = 10;
    private static final int CHROMA_RGBA = 11;
    private static final int CHAN_INTERLEAVED = 10;

    private static final WasmModule MODULE = loadModule();

    private HeicWasm() {}

    private static WasmModule loadModule() {
        try (InputStream in = HeicWasm.class.getResourceAsStream("/libheif_decode.wasm")) {
            if (in == null) {
                throw new IllegalStateException("libheif_decode.wasm not found on classpath");
            }
            return Parser.parse(in.readAllBytes());
        } catch (Exception e) {
            throw new IllegalStateException("failed to load libheif_decode.wasm", e);
        }
    }

    /** Decoded interleaved RGB (channels==3) or RGBA (channels==4). */
    public static final class Result {
        public final int width;
        public final int height;
        public final int channels;
        public final byte[] data;

        Result(int width, int height, int channels, byte[] data) {
            this.width = width;
            this.height = height;
            this.channels = channels;
            this.data = data;
        }
    }

    /** Thrown when HEVC dimensions exceed maxPixels (checked before full decode). */
    public static final class TooLargeException extends Exception {
        public final int width;
        public final int height;

        TooLargeException(int width, int height) {
            super("HEIC dimensions " + width + "x" + height + " exceed limit");
            this.width = width;
            this.height = height;
        }
    }

    /** Thrown when libheif rejects the input as corrupt/unsupported. */
    public static final class CorruptException extends Exception {
        CorruptException(String message) {
            super(message);
        }
    }

    public static Result decode(byte[] bytes, long maxPixels)
            throws TooLargeException, CorruptException {
        WasiPreview1 wasi = WasiPreview1.builder().withOptions(WasiOptions.builder().build()).build();
        ImportValues imports = ImportValues.builder()
                .addFunction(wasi.toHostFunctions())
                .build();
        Instance instance = Instance.builder(MODULE)
                .withImportValues(imports)
                .withStart(false)
                .build();
        instance.export("_initialize").apply();
        Memory mem = instance.memory();

        int dataPtr = i(instance.export("malloc").apply(bytes.length));
        mem.write(dataPtr, bytes);
        int errPtr = i(instance.export("malloc").apply(16));
        int ctx = i(instance.export("heif_context_alloc").apply());
        instance.export("heif_context_set_max_decoding_threads").apply(ctx, 0);
        instance.export("heif_context_read_from_memory").apply(errPtr, ctx, dataPtr, bytes.length, 0);
        if (mem.readInt(errPtr) != 0) {
            throw new CorruptException("read_from_memory heif_error " + mem.readInt(errPtr));
        }
        int handlePP = i(instance.export("malloc").apply(4));
        instance.export("heif_context_get_primary_image_handle").apply(errPtr, ctx, handlePP);
        if (mem.readInt(errPtr) != 0) {
            throw new CorruptException("get_primary_image_handle heif_error " + mem.readInt(errPtr));
        }
        int handle = mem.readInt(handlePP);

        int hw = i(instance.export("heif_image_handle_get_width").apply(handle));
        int hh = i(instance.export("heif_image_handle_get_height").apply(handle));
        if ((long) hw * (long) hh > maxPixels) {
            throw new TooLargeException(hw, hh);
        }

        boolean hasAlpha = i(instance.export("heif_image_handle_has_alpha_channel").apply(handle)) != 0;
        int chroma = hasAlpha ? CHROMA_RGBA : CHROMA_RGB;
        int bpp = hasAlpha ? 4 : 3;
        int channels = hasAlpha ? 4 : 3;

        int imgPP = i(instance.export("malloc").apply(4));
        instance.export("heif_decode_image").apply(errPtr, handle, imgPP, CS_RGB, chroma, 0);
        if (mem.readInt(errPtr) != 0) {
            throw new CorruptException("decode_image heif_error " + mem.readInt(errPtr));
        }
        int img = mem.readInt(imgPP);

        int strideP = i(instance.export("malloc").apply(4));
        int planePtr = i(instance.export("heif_image_get_plane_readonly").apply(img, CHAN_INTERLEAVED, strideP));
        int stride = mem.readInt(strideP);
        int w = i(instance.export("heif_image_get_width").apply(img, CHAN_INTERLEAVED));
        int h = i(instance.export("heif_image_get_height").apply(img, CHAN_INTERLEAVED));

        int row = w * bpp;
        byte[] out = new byte[row * h];
        for (int y = 0; y < h; y++) {
            byte[] r = mem.readBytes(planePtr + y * stride, row);
            System.arraycopy(r, 0, out, y * row, row);
        }
        return new Result(w, h, channels, out);
    }

    private static int i(long[] r) {
        return (int) r[0];
    }
}
