export type Channels = 3 | 4;

export type Format =
  | "bmp"
  | "png"
  | "gif"
  | "jpeg"
  | "webp"
  | "tiff"
  | "heic"
  | "emf"
  | "wmf";

export interface DecodedImage {
  width: number;
  height: number;
  data: Uint8Array;
  channels: Channels;
  format: Format;
}
