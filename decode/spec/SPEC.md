# rosetta-image-decode SPEC

Bit-level specification for byte-exact image decoding across all supported
formats and language ports. Each format section describes its reference
decoder library, configuration, output conventions, and known divergence
points.

**Reference Python lib:** Pillow 11.0.0 (pinned in `requirements.txt`).
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

Format sub-projects MUST document each error variant they can produce
and provide at least one Group-4 (error semantics) test per variant.

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
| HEIC   | (ISOBMFF `ftyp` box with brand `heic`/`heix`/`mif1`) | variable |
| EMF    | `01 00 00 00` at offset 0 + `20 45 4d 46` at offset 40 | structured |
| WMF    | `d7 cd c6 9a` (placeable) or `01 00 09 00` (standard) | 4 |

Sub-projects MUST keep these prefixes in sync with their
`detectFormat()` implementation.

---

## Format Sections

The following sections are populated by their corresponding sub-projects:

### §10 BMP

*(Populated by the BMP sub-project. Currently planned. See `formats.json`
for status.)*

### §11 PNG

*(Populated by the PNG sub-project. Currently planned. See `formats.json`
for status.)*

### §12 GIF

*(Populated by the GIF sub-project. Currently planned. See `formats.json`
for status.)*

### §13 JPEG

*(Populated by the JPEG sub-project. Currently planned. See `formats.json`
for status.)*

### §14 WebP

*(Populated by the WebP sub-project. Currently planned. See `formats.json`
for status.)*

### §15 TIFF

*(Populated by the TIFF sub-project. Currently planned. See `formats.json`
for status.)*

### §16 HEIC

*(Populated by the HEIC sub-project. Currently planned. See `formats.json`
for status.)*

### §17 EMF / WMF

*(Populated by the EMF/WMF sub-project. Currently planned. See
`formats.json` for status.)*

---

## Versioning

- `spec-decode-v0.1.0` — shared core (this document plus surrounding infrastructure)
- `<format>-v0.1.0` — first complete port set for a format (e.g., `bmp-v0.1.0` when all 5 ports support BMP)
