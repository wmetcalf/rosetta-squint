package io.github.wmetcalf.rosettasquint.decode;

import java.io.IOException;

public class DecodeException extends IOException {
    public enum Kind {
        UNSUPPORTED_FORMAT,
        CORRUPT_INPUT,
        TRUNCATED,
        UNSUPPORTED_FEATURE,
        IMAGE_TOO_LARGE
    }

    private final Kind kind;
    private final Format format;
    private final String detail;

    public DecodeException(Kind kind, Format format, String detail) {
        super(kind.name() + (format != null ? "[" + format + "]" : "") + (detail != null && !detail.isEmpty() ? ": " + detail : ""));
        this.kind = kind;
        this.format = format;
        this.detail = detail != null ? detail : "";
    }

    public Kind kind() { return kind; }
    public Format format() { return format; }
    public String detail() { return detail; }
}
