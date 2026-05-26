import { DecodeError } from "../errors.js";
import type { DecodedImage } from "../types.js";
import { decodeGifFirstFrame } from "./gif-decoder.js";

export function decodeGif(bytes: Uint8Array): DecodedImage {
  let frame;
  try {
    frame = decodeGifFirstFrame(bytes);
  } catch (e) {
    if (e instanceof DecodeError) throw e;
    throw new DecodeError("corruptInput", "gif", `GIF decode failed: ${(e as any).message ?? e}`);
  }

  return {
    width: frame.width,
    height: frame.height,
    data: frame.pixels,
    channels: frame.channels,
    format: "gif",
  };
}
