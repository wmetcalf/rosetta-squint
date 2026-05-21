package io.rosetta.imagedecode.internal.libheif;

import com.sun.jna.Pointer;
import com.sun.jna.Structure;

import java.util.List;

/**
 * Maps the C struct heif_error { int code; int subcode; const char* message; }.
 * Used as a by-value return from libheif functions.
 */
public class HeifError extends Structure {

    /** heif_error_Ok = 0 */
    public int code;
    /** heif_suberror_code */
    public int subcode;
    /** textual message (always non-null per libheif docs) */
    public Pointer message;

    public HeifError() {
        super();
    }

    @Override
    protected List<String> getFieldOrder() {
        return List.of("code", "subcode", "message");
    }

    public boolean isOk() {
        return code == 0;
    }

    public String messageString() {
        if (message == null) return "(null)";
        return message.getString(0);
    }

    /** By-value variant used as a function return type in JNA. */
    public static class ByValue extends HeifError implements Structure.ByValue {
        public ByValue() {
            super();
        }
    }
}
