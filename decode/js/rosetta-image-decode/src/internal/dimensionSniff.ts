/**
 * Pre-decode dimension sniffers for JPEG, WebP, and HEIC.
 *
 * Spec §3.1 requires every decoder to enforce ``MAX_PIXELS`` against
 * file-declared dimensions BEFORE invoking the underlying decoder. The
 * JS port relies on @jsquash/jpeg, @jsquash/webp, and libheif-js, none of
 * which expose a separate "get dimensions" entry point — calling decode()
 * already allocates the raster inside WASM linear memory. These helpers
 * walk just enough of the file structure to recover declared width/height
 * so checkDimensions() can fire before the WASM decoder runs.
 *
 * Each helper returns ``null`` on malformed input; callers should treat
 * that as "skip the pre-check and let the underlying decoder produce the
 * canonical error", not as a hard failure.
 */

/** Sniff JPEG dimensions from the first SOF (Start of Frame) marker.
 *
 * JPEG layout: SOI (0xFF D8), then a sequence of markers (0xFF XX). Each
 * non-RST/non-SOI/non-EOI marker is followed by a 2-byte big-endian length
 * (inclusive of the length bytes). The Start-of-Frame markers — 0xFFC0
 * through 0xFFCF excluding 0xFFC4 (DHT), 0xFFC8 (JPG), and 0xFFCC (DAC) —
 * carry image dimensions starting at offset +5 (precision, height, width).
 */
export function sniffJpegDimensions(bytes: Uint8Array): { width: number; height: number } | null {
  if (bytes.length < 4 || bytes[0] !== 0xFF || bytes[1] !== 0xD8) return null;
  let i = 2;
  while (i + 3 < bytes.length) {
    if (bytes[i] !== 0xFF) return null;
    // Skip any 0xFF fill bytes (rare but legal between markers).
    while (i < bytes.length && bytes[i] === 0xFF) i++;
    if (i >= bytes.length) return null;
    const marker = bytes[i]!;
    i++;
    // RST0..RST7 (0xD0..0xD7), SOI (0xD8), EOI (0xD9), TEM (0x01): no length field.
    if (marker === 0xD9 || marker === 0x01) return null;
    if (marker >= 0xD0 && marker <= 0xD7) continue;
    if (i + 2 > bytes.length) return null;
    const len = (bytes[i]! << 8) | bytes[i + 1]!;
    // SOF markers: 0xC0..0xCF excluding 0xC4 (DHT), 0xC8 (JPG reserved), 0xCC (DAC).
    if (marker >= 0xC0 && marker <= 0xCF && marker !== 0xC4 && marker !== 0xC8 && marker !== 0xCC) {
      if (i + 7 > bytes.length) return null;
      const height = (bytes[i + 3]! << 8) | bytes[i + 4]!;
      const width = (bytes[i + 5]! << 8) | bytes[i + 6]!;
      return { width, height };
    }
    if (len < 2) return null;
    i += len;
  }
  return null;
}

/** Sniff WebP dimensions from the RIFF/VP8/VP8L/VP8X chunk layout.
 *
 * Container: "RIFF" + size + "WEBP" + chunks. The first body chunk's
 * fourcc determines the bitstream:
 *  - "VP8 ": lossy keyframe; dimensions in 14-bit BE-ish fields inside the
 *           keyframe header (offsets 6-9 from chunk data, masked to 14 bits).
 *  - "VP8L": lossless; dims are stored bit-packed in the 4 bytes after the
 *           0x2F signature byte (14 bits width-1, then 14 bits height-1, LE).
 *  - "VP8X": extended container; 24-bit LE width-1 and height-1 at chunk
 *           data offsets 4 and 7.
 */
export function sniffWebpDimensions(bytes: Uint8Array): { width: number; height: number } | null {
  if (bytes.length < 30) return null;
  if (
    bytes[0] !== 0x52 || bytes[1] !== 0x49 || bytes[2] !== 0x46 || bytes[3] !== 0x46 ||  // RIFF
    bytes[8] !== 0x57 || bytes[9] !== 0x45 || bytes[10] !== 0x42 || bytes[11] !== 0x50    // WEBP
  ) return null;
  // Chunk fourcc at 12..16, chunk size at 16..20, chunk data starts at 20.
  const chunkType = String.fromCharCode(bytes[12]!, bytes[13]!, bytes[14]!, bytes[15]!);
  if (chunkType === "VP8X") {
    // VP8X: 4 bytes flags, then 3 bytes (width-1) LE, then 3 bytes (height-1) LE.
    if (bytes.length < 30) return null;
    const wMinus1 = bytes[24]! | (bytes[25]! << 8) | (bytes[26]! << 16);
    const hMinus1 = bytes[27]! | (bytes[28]! << 8) | (bytes[29]! << 16);
    return { width: wMinus1 + 1, height: hMinus1 + 1 };
  }
  if (chunkType === "VP8L") {
    // VP8L signature byte at offset 20 = 0x2F, then 4 bytes of bit-packed dims.
    if (bytes.length < 25 || bytes[20] !== 0x2F) return null;
    const sig =
      bytes[21]! |
      (bytes[22]! << 8) |
      (bytes[23]! << 16) |
      (bytes[24]! << 24);
    const width = (sig & 0x3FFF) + 1;
    const height = ((sig >>> 14) & 0x3FFF) + 1;
    return { width, height };
  }
  if (chunkType === "VP8 ") {
    // VP8 lossy keyframe: 3-byte tag, then 3-byte start code (0x9D 0x01 0x2A),
    // then 2 bytes width (14 LSB) + 2 bytes height (14 LSB). LE.
    if (bytes.length < 30) return null;
    if (bytes[23] !== 0x9D || bytes[24] !== 0x01 || bytes[25] !== 0x2A) return null;
    const wRaw = bytes[26]! | (bytes[27]! << 8);
    const hRaw = bytes[28]! | (bytes[29]! << 8);
    return { width: wRaw & 0x3FFF, height: hRaw & 0x3FFF };
  }
  return null;
}

/** Sniff HEIC dimensions from the first ``ispe`` (Image Spatial Extents) box.
 *
 * HEIF/HEIC is an ISO Base Media File Format container. Boxes are size+type
 * pairs; ``ispe`` payload is fixed format: 4 bytes version+flags, 4 bytes
 * width (big-endian u32), 4 bytes height (big-endian u32). The box lives
 * inside ``meta`` → ``iprp`` → ``ipco`` → ``ispe``; rather than walking the
 * full nested hierarchy we scan the byte stream for the literal "ispe"
 * fourcc and read the 8 bytes of dimensions that follow the version word.
 * For multi-image HEICs only the first occurrence is used (matches the
 * "primary image" convention).
 */
export function sniffHeicDimensions(bytes: Uint8Array): { width: number; height: number } | null {
  if (bytes.length < 30) return null;
  // Scan up to a reasonable limit — ispe usually lives in the first kilobyte
  // of metadata. Cap at 1 MiB to keep the scan O(file-prefix).
  const limit = Math.min(bytes.length - 16, 1024 * 1024);
  for (let i = 0; i < limit; i++) {
    if (
      bytes[i] === 0x69 &&     // 'i'
      bytes[i + 1] === 0x73 && // 's'
      bytes[i + 2] === 0x70 && // 'p'
      bytes[i + 3] === 0x65    // 'e'
    ) {
      // Skip 4-byte version+flags after the type tag.
      const wOff = i + 4 + 4;
      if (wOff + 8 > bytes.length) return null;
      // JS bitwise ops are signed 32-bit, so a 2^31+ width would emerge
      // negative from the shift+OR. Convert with `>>> 0` BEFORE comparing
      // against zero so any non-zero declared width is preserved.
      const width = (
        (bytes[wOff]! << 24) |
        (bytes[wOff + 1]! << 16) |
        (bytes[wOff + 2]! << 8) |
        bytes[wOff + 3]!
      ) >>> 0;
      const height = (
        (bytes[wOff + 4]! << 24) |
        (bytes[wOff + 5]! << 16) |
        (bytes[wOff + 6]! << 8) |
        bytes[wOff + 7]!
      ) >>> 0;
      // Reject zero declared dims; let the underlying decoder produce the
      // canonical error in that case rather than emitting a spurious
      // "too large".
      if (width === 0 || height === 0) return null;
      return { width, height };
    }
  }
  return null;
}
