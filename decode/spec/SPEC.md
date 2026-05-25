# rosetta-image-decode SPEC

Bit-level specification for byte-exact image decoding across all supported
formats and language ports. Each format section describes its reference
decoder library, configuration, output conventions, and known divergence
points.

**Reference Python lib:** Pillow 12.2.0 (pinned in `requirements.txt`).
Golden pixel buffers in `decoded/<format>/` are produced by
`regenerate.py` using `Image.open(...).convert('RGB' or 'RGBA').tobytes()`.

**Format registry:** `formats.json` tracks per-format status, reference
decoder library + version, ports implemented, and sub-spec sections.

---

## §1 Output Format

Decoders return a `DecodedImage` value type:

| Field      | Type    | Description                                     |
|------------|---------|-------------------------------------------------|
| `width`    | int     | positive                                        |
| `height`   | int     | positive                                        |
| `channels` | 3 or 4  | RGB (3) or RGBA (4); format-dependent           |
| `data`     | bytes   | row-major; length = width × height × channels   |
| `format`   | Format  | enum tag identifying which decoder handled it   |

**Alpha channel policy:** Decoders preserve alpha from the source. PNG
with alpha → 4 channels. BMP without alpha → 3 channels. Composite-to-RGB
is the caller's job (rosetta-image-hash uses `ImgRGB.toRGB()` for this).

**Color space policy:** All output is in sRGB. Embedded ICC profiles are
ignored in v1; format-specific sub-projects may add ICC handling later.

**EXIF orientation policy:** Decoders return pixels in their stored
orientation. Auto-rotation is a caller concern.

---

## §2 Golden File Format (`decoded/<format>/<fixture>.bin`)

Each pre-decoded golden is a binary file with this layout:

```
+--------+--------+--------+----------+--------------------+
| offset | length | type   | name     | value              |
+--------+--------+--------+----------+--------------------+
|   0    |   4    | u32 LE | width    | pixel width        |
|   4    |   4    | u32 LE | height   | pixel height       |
|   8    |   1    | u8     | channels | 3 (RGB) or 4 (RGBA)|
|   9    |   3    | u8[3]  | reserved | zeros              |
|  12    |   N    | u8[N]  | pixels   | row-major bytes    |
+--------+--------+--------+----------+--------------------+
```

Where `N = width × height × channels`. The header is 12 bytes total
(4 + 4 + 1 + 3 padding). Pixel layout is row-major with no per-row
padding; for `channels=3` each pixel is `(R, G, B)`, for `channels=4`
each pixel is `(R, G, B, A)`.

---

## §3 Error Semantics

The public `decode(bytes)` function throws (or returns Result, per
language conventions) `DecodeError` variants:

| Variant              | When                                                          |
|----------------------|---------------------------------------------------------------|
| `unsupportedFormat`  | Magic bytes don't match any known format                      |
| `corruptInput`       | Structurally invalid for the detected format                  |
| `truncated`          | Stream ended before decoder expected                          |
| `unsupportedFeature` | Valid input uses a format feature this port doesn't support   |
| `imageTooLarge`      | Header-declared dimensions exceed `MAX_PIXELS` (see §3.1)     |

Format sub-projects MUST document each error variant they can produce
and provide at least one Group-4 (error semantics) test per variant.

### §3.1 Decompression-bomb / dimension cap

To protect against malicious inputs that declare gigantic dimensions in
their header (a small file claiming `width=65536, height=65536` would
otherwise drive an underlying decoder to allocate 12 GB+), every port
MUST check the file-declared dimensions before invoking the underlying
decoder and reject inputs whose pixel count exceeds `MAX_PIXELS`:

```
MAX_PIXELS = 268_435_456    // 256 * 1024 * 1024 = 256 mega-pixels
```

The cap is per-`width × height`, **not** per-byte and not per-channel.
For comparison: PIL's `Image.MAX_IMAGE_PIXELS` default is ~89.5 MP. 256
MP gives substantial headroom for legitimate large photos while still
bounding worst-case memory at ~1 GB (RGBA, before any downstream
processing).

Ports MUST:

1. Sniff dimensions from the file header (no full decode) before any
   allocation proportional to width × height.
2. Reject with `imageTooLarge` if `width * height > MAX_PIXELS`.
3. Compute `width * height * channels` as a 64-bit value (or with
   explicit overflow checks) — never as native int multiplication that
   can wrap silently.

Group 4 (error semantics) tests MUST include at least one fixture per
format that triggers `imageTooLarge`.

### §3.2 Tolerance divergence on malformed input

This spec guarantees **byte-exact agreement across ports for inputs the
decoder accepts as valid**. It does NOT guarantee that every port reaches
the same accept/reject decision on every malformed input.

Differential fuzzing (`tools/cross-squint-diff/differential_fuzz.py`)
surfaces three known classes of cross-port tolerance drift:

1. **Java GIF over-permissive LZW**: `javax.imageio.GIFImageReader`
   accepts LZW streams with out-of-range codes by filling the affected
   pixels with the last valid color. Every other port (Go `image/gif`,
   in-tree TS GIF decoder, Rust `image` crate gif feature, Swift in-tree
   GIF decoder, Python PIL) rejects with `corruptInput`. A future revision
   may replace `javax.imageio` with the in-tree LZW decoder used by JS
   for consistency.
2. **JPEG decoder strictness — TurboJPEG vs libjpeg-turbo direct**: Go,
   Java, and Swift use TurboJPEG (`tjDecompress2`), which strictly rejects
   "premature end of data segment" on truncated SOS. JS, Python, and Rust
   use libjpeg-turbo directly (via @jsquash/jpeg, PIL, mozjpeg-sys), which
   zero-fills the missing data and produces a decoded output. All three
   "lenient" ports agree byte-exact among themselves but produce a
   different hex than the three "strict" ports would (which reject).
3. **JPEG decode output divergence on tail-corrupt input**: Python (PIL)
   uses different IDCT/upsampling defaults than the other lenient ports;
   in `hex-disagreement` findings, Python produces a markedly different
   hash from JS/Rust/Swift on the same tail-corrupt JPEG.

**Implication for callers handling untrusted input**: the safe contract
is "if any port returns an error, treat as un-hashable". Don't compare
hashes across ports when one port errored and another succeeded — that
case violates the byte-exact invariant.

A `imageTooLarge` fixture pre-decode check (§3.1) prevents the
decompression-bomb category, but tolerance-level drift on otherwise-
hostile input is a known limitation in v1.

#### Why these aren't aligned in v1

For each tolerance class, alignment was investigated and intentionally deferred:

- **Java GIF lenient LZW**: a separate fix replaces `javax.imageio.GIFImageReader`
  with the same in-tree pure-Java LZW decoder the JS port uses. See
  `decode/java/.../internal/GIFDecoder.java`.
- **JPEG strictness alignment**: configuring all 6 ports to the same strictness
  level requires either (a) making libjpeg-turbo strict by intercepting the
  `emit_message` callback to treat truncation warnings as fatal errors —
  infeasible in JS (the @jsquash WASM build doesn't expose this) and in
  Python (PIL's libjpeg integration swallows the warning), OR (b) making
  TurboJPEG (`tjDecompress2`) accept truncation, which the public TurboJPEG
  API does not expose. **The v1 contract is: callers depending on byte-exact
  agreement across ports MUST treat any one port erroring as "input is not
  decodable" and skip the hash.** The cross-port lenient/strict split affects
  only inputs where any port errors; for inputs all 6 ports accept, the
  pixels (and therefore hashes) agree byte-exact.
- **PIL upsampling on tail-corrupt JPEG**: hex-disagreements where Python (PIL)
  produces a markedly different hash from JS/Rust/Swift on tail-corrupt JPEGs
  reflect different libjpeg-turbo configuration in PIL's own bindings. This
  is the same root cause as the strictness split — invisible to callers
  treating any port error as "skip hashing".

The differential fuzzer (`make differential-fuzz`) is wired in CI and can
detect any new tolerance divergence introduced by future changes.

---

## §4 Format Detection (magic byte prefixes)

`detectFormat(bytes)` returns the `Format` enum value matching the
shortest prefix that uniquely identifies the format. Returns nil/None
if no known format matches or input is too short.

Magic byte prefixes (recognized in v1 once the sub-project lands):

| Format | Prefix (hex)                          | Length |
|--------|---------------------------------------|--------|
| BMP    | `42 4d`                               | 2      |
| PNG    | `89 50 4e 47 0d 0a 1a 0a`             | 8      |
| GIF    | `47 49 46 38 37 61` or `47 49 46 38 39 61` | 6 |
| JPEG   | `ff d8 ff`                            | 3      |
| WebP   | `52 49 46 46 ?? ?? ?? ?? 57 45 42 50` | 12     |
| TIFF   | `49 49 2a 00` or `4d 4d 00 2a`        | 4      |
| HEIC   | (ISOBMFF `ftyp` box with brand `heic`/`heix`/`mif1`/`msf1`/`hevc`/`hevx`) | variable |

The HEIC brand whitelist is intentionally narrow — `heim`/`heis`/`hevm`/
`hevs` (multi-image / sequence brands) are rejected as `unsupportedFormat`
in v1, which only supports single still images. `avif` brand (AV1 codec)
is also rejected. Future sub-projects may broaden this.

Sub-projects MUST keep these prefixes in sync with their
`detectFormat()` implementation.

---

## Format Sections

The following sections are populated by their corresponding sub-projects:

### §10 BMP

#### §10.1 File header (BITMAPFILEHEADER, 14 bytes)

```
+--------+------+--------+-----------+--------------------------------------------+
| offset | size | type   | field     | value                                      |
+--------+------+--------+-----------+--------------------------------------------+
|   0    |  2   | u8[2]  | bfType    | 'BM' (0x42 0x4D); any other → unsupportedFormat |
|   2    |  4   | u32 LE | bfSize    | total file size (informational; not validated) |
|   6    |  4   | u8[4]  | reserved  | zeros (ignored)                            |
|  10    |  4   | u32 LE | bfOffBits | byte offset from file start to pixel array |
+--------+------+--------+-----------+--------------------------------------------+
```

#### §10.2 DIB header (variable size; 40, 108, or 124 bytes supported)

The DIB header starts at byte 14. The first u32 LE at offset 14 is `biSize`, which
determines the header variant. Unsupported sizes (e.g., 12 for OS/2 v1, 64 for OS/2 v2)
→ `unsupportedFeature(format=bmp, feature="DIB header size <N>")`.

**40-byte BITMAPINFOHEADER fields:**

| Field | Type | Offset | Notes |
|---|---|---|---|
| `biSize` | u32 LE | 14 | Header size: 40, 108, or 124 |
| `biWidth` | i32 LE | 18 | Must be > 0; otherwise `corruptInput("biWidth must be positive")` |
| `biHeight` | i32 LE | 22 | > 0 = bottom-up rows; < 0 = top-down rows; 0 → `corruptInput("biHeight must be non-zero")` |
| `biPlanes` | u16 LE | 26 | Must be 1; otherwise `corruptInput("biPlanes must be 1")` |
| `biBitCount` | u16 LE | 28 | Valid: 1, 4, 8, 16, 24, 32; other → `corruptInput("biBitCount=<N> not supported")` |
| `biCompression` | u32 LE | 30 | 0=BI_RGB, 1=BI_RLE8, 2=BI_RLE4, 3=BI_BITFIELDS, 4=BI_JPEG, 5=BI_PNG, 6=BI_ALPHABITFIELDS; other → `corruptInput` |
| `biSizeImage` | u32 LE | 34 | May be 0 for BI_RGB; always trust geometry |
| `biXPelsPerMeter` | i32 LE | 38 | DPI (ignored) |
| `biYPelsPerMeter` | i32 LE | 42 | DPI (ignored) |
| `biClrUsed` | u32 LE | 46 | Palette entries used; 0 means 2^biBitCount for ≤8-bit, 0 for ≥16-bit |
| `biClrImportant` | u32 LE | 50 | Ignored |

**108-byte BITMAPV4HEADER** (biSize == 108) extends the above with:

| Field | Offset | Notes |
|---|---|---|
| redMask, greenMask, blueMask, alphaMask | 54–69 | RGBA bit masks (each u32 LE); used when biCompression == BI_BITFIELDS or BI_ALPHABITFIELDS |
| bV4CSType | 70–73 | Color space type (ignored) |
| bV4Endpoints | 74–109 | CIE XYZ endpoints (ignored) |
| bV4GammaRed/Green/Blue | 110–121 | Gamma values (ignored) |

**124-byte BITMAPV5HEADER** (biSize == 124) adds 16 more bytes after V4:

| Field | Offset | Notes |
|---|---|---|
| bV5Intent | 122–125 | Rendering intent (ignored) |
| bV5ProfileData | 126–129 | Profile byte offset (profile data ignored even if present) |
| bV5ProfileSize | 130–133 | Profile size (ignored) |
| bV5Reserved | 134–137 | Reserved (ignored) |

**Mask field availability:** Masks at offsets 54–69 are also read when
`biCompression` ∈ {BI_BITFIELDS, BI_ALPHABITFIELDS} even with a 40-byte header
(the masks appear immediately after the 40-byte header in that case).
alphaMask is only read if `biSize ≥ 56` or `biCompression == BI_ALPHABITFIELDS`.

#### §10.3 Color table (paletted modes only)

Applies when `biBitCount` ∈ {1, 4, 8}. The color table immediately follows the
DIB header (at offset `14 + biSize`).

- Entry count = `biClrUsed` if non-zero; else `2^biBitCount`.
- Each entry is 4 bytes in BGR-reserved order: `[Blue, Green, Red, reserved]`.
- Discard the reserved (alpha) byte; build palette as `[(R, G, B), ...]` triples.
- Palette-based decoders always output RGB (3 channels), never RGBA.

#### §10.4 Pixel layout

- **Pixel array start:** at byte offset `bfOffBits` from the beginning of the file.
- **Row order:** if `biHeight > 0`, rows are stored bottom-up (last row in file = top
  row of image); iterate in reverse. If `biHeight < 0`, rows are top-down; iterate
  in natural order. Use `abs(biHeight)` for the image height.
- **Row stride:** each row is padded to a multiple of 4 bytes (DWORD-aligned).
  Formula: `row_stride = ((row_bytes + 3) / 4) * 4`. Padding bytes at the end of
  each row are NOT emitted in the output.
- **Within-row order:** pixels are left-to-right.
- **BGR channel order:** 24-bit and 32-bit BI_RGB store pixels as B-G-R (or B-G-R-A
  for 32-bit). Decoders MUST swap bytes to produce R-G-B(-A) output.

#### §10.5 BI_RGB decode

**24-bit (biBitCount == 24):**
- 3 bytes per pixel: `[B, G, R]`.
- Row stride: `((width * 3 + 3) / 4) * 4`.
- Output mode: RGB (3 channels).
- Per pixel: emit `R = src[2], G = src[1], B = src[0]`.

**32-bit (biBitCount == 32):**
- 4 bytes per pixel: `[B, G, R, A]`.
- Row stride: `width * 4` (already DWORD-aligned).
- Two-pass alpha detection (see §10.10).
- If any alpha byte is non-zero → output mode RGBA; emit R, G, B, A.
- If all alpha bytes are zero → output mode RGB; emit R, G, B only.

**8-bit paletted (biBitCount == 8):**
- 1 byte per pixel = palette index.
- Row stride: `((width + 3) / 4) * 4`.
- Output mode: RGB (3 channels).
- Per pixel: look up `palette[index]`, emit R, G, B.

#### §10.6 BI_BITFIELDS decode

Applies when `biCompression` ∈ {BI_BITFIELDS (3), BI_ALPHABITFIELDS (6)}.
Valid `biBitCount` values: 16 and 32.

**Mask shift/scale formula** (integer arithmetic only):
```
shift = number of trailing zero bits in mask
range = mask >> shift
channel_byte = ((pixel_word & mask) >> shift) * 255 / range
```

If any of redMask, greenMask, or blueMask is zero →
`corruptInput("BI_BITFIELDS mask is zero")`.

**16-bit:**
- 2 bytes per pixel as u16 LE.
- Row stride: `((width * 2 + 3) / 4) * 4`.

**32-bit:**
- 4 bytes per pixel as u32 LE.
- Row stride: `width * 4`.

**Output mode:**
- If alphaMask is present and non-zero (BI_ALPHABITFIELDS, or biSize ≥ 56 with
  non-zero alphaMask): output RGBA (4 channels).
- Otherwise: output RGB (3 channels).

Common mask layouts (informational):

| Layout | redMask | greenMask | blueMask | alphaMask |
|---|---|---|---|---|
| 16-bit 5-5-5 | `0x7C00` | `0x03E0` | `0x001F` | — |
| 16-bit 5-6-5 | `0xF800` | `0x07E0` | `0x001F` | — |
| 32-bit BGRA  | `0x00FF0000` | `0x0000FF00` | `0x000000FF` | `0xFF000000` |

#### §10.7 Paletted 1-bit / 4-bit decode

Both modes use the color table from §10.3 and produce RGB (3-channel) output.

**1-bit (biBitCount == 1):**
- 8 pixels packed per byte, MSB first (bit 7 = leftmost pixel).
- Each bit is a 1-bit palette index (0 or 1).
- Row stride: `((width + 31) / 32) * 4`.

**4-bit (biBitCount == 4):**
- 2 pixels packed per byte, high nibble first (bits 7–4 = left pixel, bits 3–0 = right pixel).
- Each nibble is a 4-bit palette index (0–15).
- Row stride: `((width * 4 + 31) / 32) * 4`.

Both modes apply the same bottom-up / top-down row ordering as §10.4.

#### §10.8 BI_RLE4 / BI_RLE8 state machine

Applies when `biCompression` ∈ {BI_RLE8 (1), BI_RLE4 (2)}.
Output mode is always RGB (via palette lookup). Uses the color table from §10.3.

The compressed pixel stream is a sequence of byte pairs `(count, data)`:

| count | data | Meaning |
|---|---|---|
| 0 | 0 | End-of-line (EOL). Pad remaining pixels in the current row with palette[0]. Advance to the next row; reset column to 0. |
| 0 | 1 | End-of-bitmap (EOF). Pad any unfilled rows with palette[0] and stop. |
| 0 | 2 | Cursor delta. Read the next 2 bytes as `(dx, dy)`; advance the cursor `dx` columns and `dy` rows. Fill skipped pixels with palette[0]. |
| 0 | N ≥ 3 | Absolute mode. Read the next `N` bytes (RLE8) or `ceil(N/2)` bytes (RLE4, high nibble first) as pixel indices; emit N pixels. After absolute-mode data, read padding so the total absolute-mode byte count is word-aligned (even). |
| N > 0 | P | Encoded mode. Emit N copies of palette[P] (RLE8). For RLE4: emit N pixels alternating the high and low nibbles of P. |

**Buffer overrun detection:** if the total number of pixels emitted exceeds
`width * height`, throw `corruptInput("RLE pixel buffer overrun")`.

After decoding, if any row was not closed by an EOL marker before EOF, treat it
as implicitly complete and pad remaining cells with palette[0].

#### §10.9 BI_JPEG / BI_PNG handling

`biCompression == 4` indicates an embedded JPEG stream; `biCompression == 5`
indicates an embedded PNG stream. Both are recognized in v1 but not decoded.

Without reading further pixel data, immediately throw:
- `unsupportedFeature(format=bmp, feature="embedded JPEG")` for biCompression = 4.
- `unsupportedFeature(format=bmp, feature="embedded PNG")` for biCompression = 5.

Decoding embedded JPEG/PNG is deferred to follow-up patches after the PNG
sub-project (Plan 9) and JPEG sub-project (Plan 10) land.

#### §10.10 Alpha channel inference for 32-bit BI_RGB

PIL's BMP loader uses a two-pass algorithm for 32-bit BI_RGB images to decide
whether the fourth byte is a meaningful alpha channel:

1. Read all pixel data (4 bytes per pixel: B, G, R, A).
2. Scan every pixel's alpha byte (the 4th byte).
3. If **any** alpha byte is non-zero → output mode = RGBA (4 channels); emit R, G, B, A.
4. If **all** alpha bytes are zero → output mode = RGB (3 channels); discard alpha; emit R, G, B.

Ports MUST replicate this two-pass detection to produce byte-exact output
matching the golden files. For BI_BITFIELDS 32-bit with an explicit alpha mask,
skip the two-pass detection: always output RGBA.

### §11 PNG

**Reference behavior:** Pillow 12.2.0's built-in PNG plugin (`PIL/PngImagePlugin.py` + libpng via `libImaging/PngImagePlugin.c`).

**Decoded pixel output by color type:**

| Color type | Output mode | Channels |
|---|---|---|
| 0 (grayscale, no alpha) | RGB | 3 (replicate gray value across R, G, B) |
| 2 (RGB) | RGB | 3 |
| 3 (paletted, no tRNS) | RGB | 3 (look up palette per pixel) |
| 3 (paletted + tRNS) | RGBA | 4 (palette index → palette RGB; tRNS gives alpha per palette entry) |
| 4 (grayscale + alpha) | RGBA | 4 |
| 6 (RGB + alpha) | RGBA | 4 |

**Bit depth handling:**
- 1, 2, 4-bit: expanded to 8-bit (palette indices for color type 3; gray values scaled to [0, 255] for color type 0)
- 8-bit: native
- 16-bit: downsampled to 8-bit per PIL default (high byte kept)

**Ancillary chunk policy:** read but NOT applied to pixel data.
- gAMA, cHRM, sRGB, iCCP — color management opt-in only; not applied here.
- pHYs, tIME — informational, ignored.
- tEXt, zTXt, iTXt — metadata, ignored.

**Interlacing:** Adam7 interlaced PNGs are de-interlaced as part of the PNG decode (handled by libpng or equivalent in each port's library).

**Invalid input handling:**
- File shorter than 8 bytes or doesn't start with `89 50 4E 47 0D 0A 1A 0A` → `unsupportedFormat`
- IHDR truncated or invalid color-type/bit-depth combo → `corruptInput`
- Missing IEND chunk → behavior is library-dependent; documented per-port in DECODER_NOTES.md
- CRC mismatch in any chunk → `corruptInput("CRC mismatch in <chunk>")`
- APNG (multi-frame) → currently decode first frame only; multi-frame deferred to v0.2.

**Per-port reference library:**
- **Java**: `javax.imageio.ImageIO` (JDK built-in)
- **Go**: `image/png` (stdlib)
- **Rust**: `image` crate with png feature
- **JS/TS**: `pngjs` ^7
- **Swift**: `swift-png` 4.x (pinned `..<4.4.0` for Swift 5.9 compatibility)

### §12 GIF

**Reference behavior:** Pillow 12.2.0's built-in GIF plugin returns the first
frame of a GIF89a file as a paletted image. We convert that to RGB or RGBA
matching PIL's default `.convert('RGB')` or `.convert('RGBA')` semantics.

**Output channels:**
- GIF without Graphic Control Extension transparency → RGB (3 channels) via palette lookup
- GIF with GCE transparency index → RGBA (4 channels); transparent pixels get alpha=0

**Multi-frame:** v1 decodes the first frame only. Matches PIL's
`Image.open(gif).convert(...).tobytes()` default. Multi-frame iteration
deferred to v0.2.

**Per-port reference library:**
- Java: `javax.imageio.ImageIO`
- Go: `image/gif` (stdlib)
- Rust: `image` crate with gif feature
- JS: `omggif ^1.0`
- Swift: **pure-Swift** GIF89a decoder (no external deps)

**Invalid input handling:**
- Magic ≠ "GIF87a" or "GIF89a" → `unsupportedFormat`
- Truncated header → `truncated`
- Malformed LZW code stream → `corruptInput`

### §13 JPEG

**Reference behavior:** libjpeg-turbo (the C library Pillow 12.2.0 uses internally), configured with:
- `dct_method = JDCT_ISLOW` (deterministic across architectures)
- `out_color_space = JCS_RGB`
- Default chroma upsampling (fancy upsampling enabled — libjpeg-turbo's default)

**Output channels:** Always RGB (3 channels). JPEG has no alpha channel. Grayscale JPEGs (Y-only) output RGB via channel replication.

**Color spaces supported in v1:**
- YCbCr (most common) → RGB ✓
- Grayscale → RGB (replicate) ✓
- CMYK → `unsupportedFeature(format=jpeg, feature="CMYK color space")` (defer to v0.2)
- YCCK (Adobe) → `unsupportedFeature` (defer)

**Chroma subsampling supported:**
- 4:4:4 (no subsampling) ✓
- 4:2:2 (horizontal 2:1) ✓
- 4:2:0 (horizontal + vertical 2:1) ✓

**Progressive vs baseline:** Both supported transparently (libjpeg-turbo handles).

**Cross-architecture parity:** libjpeg-turbo with ISLOW is bit-exact across x86, ARM, MIPS, etc.

**Per-port reference library:**
- Java: `org.libjpeg-turbo:turbojpeg` (Maven)
- Go: `github.com/pixiv/go-libjpeg` (cgo)
- Rust: `mozjpeg-sys` (compiles from source)
- JS: `@squoosh/jpeg` (WASM-compiled mozjpeg)
- Swift: C interop with system libjpeg-turbo via SwiftPM systemLibrary

**System dependency:** Most ports require `libjpeg-turbo` installed (Linux: `apt install libjpeg-turbo8-dev libturbojpeg0-dev`). Rust + JS bundle the C code internally.

**Invalid input handling:**
- Magic ≠ `0xFF 0xD8 0xFF` → `unsupportedFormat`
- Corrupt JPEG markers → `corruptInput`
- Truncated → `truncated` or `corruptInput` (depends on per-port lib behavior)
- CMYK color space → `unsupportedFeature(format=jpeg, feature="CMYK color space")`

### §14 WebP

**Reference behavior:** libwebp via Pillow 12.2.0's PIL WebP plugin.

**Output channels:**
- Lossy/lossless without alpha → RGB (3 channels)
- Lossy/lossless with alpha → RGBA (4 channels)

**Variants supported in v1:** lossy (VP8) + lossless (VP8L), with or without alpha. Out of scope: animated WebP iteration (first frame only); extended WebP metadata (XMP, ICC, EXIF).

**Per-port reference library:**
- Java: webp-imageio Maven artifact or custom JNI
- Go: `github.com/chai2010/webp` cgo
- Rust: `libwebp-sys2` (compiles libwebp from source)
- JS: `@jsquash/webp` WASM
- Swift: Cwebp systemLibrary

**System dep (most ports):** libwebp + libwebp-dev. Rust and JS bundle libwebp internally.

**Invalid input handling:**
- Magic ≠ `RIFF...WEBP` → unsupportedFormat
- Truncated → corruptInput
- Corrupt VP8/VP8L chunk → corruptInput

### §15 TIFF

**Reference behavior:** libtiff via Pillow 12.2.0's PIL TIFF plugin.

**v1 supported subset:**
- Baseline TIFF only (uncompressed, LZW, Deflate)
- 8-bit samples per channel
- RGB and Grayscale photometric interpretations
- Strip-based organization (not tiled)
- Single image per file (no multi-page)

**Output channels:**
- RGB photometric → RGB (3 channels)
- Grayscale photometric → RGB (3 channels, replicated)

**Out of scope (v1):**
- CCITT G3/G4 (fax compression)
- JPEG-in-TIFF
- BigTIFF
- Tiled organization
- 16-bit samples
- CMYK
- Multi-page
- EXIF orientation auto-rotation

**Magic bytes:** `49 49 2A 00` (little-endian) or `4D 4D 00 2A` (big-endian).

**Per-port reference library:**
- Java: TwelveMonkeys ImageIO TIFF plugin (`com.twelvemonkeys.imageio:imageio-tiff:3.10.1`)
- Go: `golang.org/x/image/tiff` (pure-Go); fall back to cgo if byte-exact diverges
- Rust: `image` crate with tiff feature; fall back to libtiff-sys if diverges
- JS: `utif2` npm package
- Swift: C interop with libtiff via Ctiff systemLibrary

### §16 HEIC

**Reference behavior:** system libheif 1.17.6 (Ubuntu 24.04) + libde265 1.0.15. Golden pixel bytes are decoded via a ctypes wrapper around `/lib/x86_64-linux-gnu/libheif.so.1` — NOT pillow-heif's bundled libheif 1.21.2. pillow-heif 1.3.0 is used only for HEIC fixture *encoding* (synth_heic.py); its bundled libheif is not used for golden generation.

**v1 supported subset:**
- HEVC-coded HEIF only (HEIC brand: `heic`, `heix`, `mif1`, `msf1` with `hevc1`/`hvc1` codec)
- Single still image — primary item only; multi-image collections use primary item only
- 8-bit YUV420 → RGB conversion
- Optional alpha via `auxC` auxiliary image with `aux_type` containing "alpha"

**Output channels:**
- RGB HEIC (no alpha aux image) → RGB (3 channels)
- RGB HEIC + auxC alpha → RGBA (4 channels); matches `Image.mode == "RGBA"` from pillow-heif

**Out of scope (v1):**
- AVIF (`avif` brand, AV1 codec) → unsupportedFormat
- Animations and image sequences
- 10-bit / 12-bit HDR
- Image grids and tiles
- Multi-image bursts and depth maps
- EXIF / XMP / ICC metadata handling
- Secondary items

**Magic bytes / format detection:**
HEIC is an ISOBMFF container. Detection looks for an `ftyp` box in the first 12 bytes with major brand ∈ {`heic`, `heix`, `mif1`, `msf1`}. The ISOBMFF box header is 8 bytes: 4-byte size (big-endian u32) + 4-byte box type (`ftyp`). Major brand is at offset 8. Minor version is at offset 12; compatible brands follow at offset 16.

**System dependency (Linux):**
```
apt install libheif-dev libheif1 libheif-plugin-libde265 libde265-dev libde265-0
```
macOS: `brew install libheif`.

**Per-port reference library:**
- Python (golden generation): ctypes wrapper around system libheif 1.17.6 (`/lib/x86_64-linux-gnu/libheif.so.1`)
- Java: `com.github.gotson:libheif-java` via JNA; fall back to JNI shim if unmaintained
- Go: `github.com/strukturag/libheif/go/heif` (official cgo bindings)
- Rust: `libheif-rs` (FFI to system libheif)
- JS: `libheif-js` (libheif compiled to WebAssembly, official Strukturag distribution)
- Swift: Cheif systemLibrary via pkg-config `libheif` (`heif_context_read_from_memory_without_copy`)

**Invalid input handling:**
- `ftyp` box absent or box type ≠ `ftyp` at offset 4 → `unsupportedFormat`
- `ftyp` major brand = `avif` → `unsupportedFormat` (out of v1 scope)
- `ftyp` major brand ∈ HEIC set but file truncated or HEVC stream corrupt → `corruptInput`

**Fixture corpus:** 10 valid synth fixtures (gradient RGB + RGBA, q50/q90/q100 lossless, various sizes) + 3 invalid (bad-magic, truncated, avif-brand). Encoded by `spec/synth_heic.py` using pillow-heif 1.3.0; decoded for goldens via ctypes wrapper around system libheif 1.17.6.

---

## Versioning

- `spec-decode-v0.1.0` — shared core (this document plus surrounding infrastructure)
- `<format>-v0.1.0` — first complete port set for a format (e.g., `bmp-v0.1.0` when all 5 ports support BMP)
