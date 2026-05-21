import type { Format } from "./types.js";

export type DecodeErrorKind =
  | "unsupportedFormat"
  | "corruptInput"
  | "truncated"
  | "unsupportedFeature"
  | "imageTooLarge";

export class DecodeError extends Error {
  readonly kind: DecodeErrorKind;
  readonly format: Format | null;
  readonly detail: string;

  constructor(kind: DecodeErrorKind, format: Format | null, detail: string) {
    const fmtPart = format != null ? `[${format}]` : "";
    const detailPart = detail ? `: ${detail}` : "";
    super(`${kind}${fmtPart}${detailPart}`);
    this.name = "DecodeError";
    this.kind = kind;
    this.format = format;
    this.detail = detail;
  }
}
