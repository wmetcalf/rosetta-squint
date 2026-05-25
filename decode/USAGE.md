# rosetta-image-decode — Usage

API examples for all 5 ports. For install steps and supported formats, see [STATUS.md](./STATUS.md).

Every port exposes the same minimal API:

- `decode(bytes)` → `DecodedImage { width, height, data, channels, format }`. The `data` buffer is row-major interleaved RGB or RGBA bytes — exactly what `PIL.Image.tobytes()` produces.
- `detect_format(bytes)` → `Format` or `null/None` — sniffs magic bytes, never decodes.
- `supported_formats()` → list of `Format` enum values.
- `DecodeError` (or equivalent) with a `kind` field: `unsupportedFormat`, `corruptInput`, `truncated`, `unsupportedFeature`.

A successful `decode()` is the cross-language equivalence point. Hash the resulting `data` buffer (or hand it to [`../hash`](../hash)) and you get the same hash in every language.

---

## Python (reference — the thing the ports match)

```python
from PIL import Image

img = Image.open("photo.jpg")
img = img.convert("RGB") if img.mode != "RGBA" else img      # match port output
width, height = img.size
pixels = img.tobytes()                                       # the cross-port buffer
print(f"{width}x{height} {img.mode} {len(pixels)} bytes")
```

---

## Rust

```rust
use std::fs;
use rosetta_image_decode::{decode, detect_format, Channels, DecodeErrorKind, Format};

fn main() -> Result<(), Box<dyn std::error::Error>> {
    let bytes = fs::read("photo.jpg")?;

    // Sniff first if you care
    if let Some(fmt) = detect_format(&bytes) {
        println!("detected: {:?}", fmt);
    }

    match decode(&bytes) {
        Ok(img) => {
            println!("{}x{} channels={:?} format={:?}", img.width, img.height, img.channels, img.format);
            // img.data is Vec<u8>, len == width * height * (3 or 4)
            // First pixel:
            let r = img.data[0]; let g = img.data[1]; let b = img.data[2];
            println!("top-left: ({}, {}, {})", r, g, b);
        }
        Err(e) => {
            match e.kind {
                DecodeErrorKind::UnsupportedFormat => eprintln!("not a recognized image"),
                DecodeErrorKind::CorruptInput      => eprintln!("corrupt: {}", e.detail),
                DecodeErrorKind::Truncated         => eprintln!("truncated"),
                DecodeErrorKind::UnsupportedFeature=> eprintln!("feature not in v1: {}", e.detail),
            }
        }
    }
    Ok(())
}
```

`Cargo.toml`:
```toml
[dependencies]
rosetta-image-decode = { path = "../rosetta-image-decode/rust/rosetta-image-decode" }   # not on crates.io yet
```

Types: `DecodedImage { width: usize, height: usize, data: Vec<u8>, channels: Channels, format: Format }`. `Channels::Rgb` or `Channels::Rgba`. `Format` covers `Bmp, Png, Gif, Jpeg, Webp, Tiff, Heic`. `DecodeError` has `kind: DecodeErrorKind`, `format: Option<Format>`, `detail: String`.

---

## Go

```go
package main

import (
    "fmt"
    "log"
    "os"

    "github.com/wmetcalf/rosetta-squint/decode/go/imagedecode"
)

func main() {
    bytes, err := os.ReadFile("photo.jpg")
    if err != nil { log.Fatal(err) }

    if fmt_, ok := imagedecode.DetectFormat(bytes); ok {
        fmt.Println("detected:", fmt_)
    }

    img, err := imagedecode.Decode(bytes)
    if err != nil {
        var de *imagedecode.DecodeError
        if errors.As(err, &de) {
            fmt.Println("decode failed:", de.Kind, de.Detail)
        }
        return
    }
    fmt.Printf("%dx%d channels=%v format=%v\n", img.Width, img.Height, img.Channels, img.Format)
    // img.Data is []byte, len == Width * Height * bytesPerPixel
}
```

Public entry points: `Decode(b []byte) (DecodedImage, error)`, `DetectFormat(b []byte) (Format, bool)`, `SupportedFormats() []Format`. Type `DecodedImage` has fields `Width, Height int`, `Data []byte`, `Channels`, `Format`. `DecodeError` has `Kind DecodeErrorKind`, `Format Format`, `FormatKnown bool`, `Detail string`.

`DecodeErrorKind` values: `UnsupportedFormat`, `CorruptInput`, `Truncated`, `UnsupportedFeature`. `Format` values: `Bmp, Png, Gif, Jpeg, Webp, Tiff, Heic`.

---

## Java

```java
import io.rosetta.imagedecode.Decoder;
import io.rosetta.imagedecode.DecodedImage;
import io.rosetta.imagedecode.DecodeException;
import io.rosetta.imagedecode.Format;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public class Demo {
    public static void main(String[] args) throws Exception {
        byte[] bytes = Files.readAllBytes(Path.of("photo.jpg"));

        Optional<Format> sniff = Decoder.detectFormat(bytes);
        sniff.ifPresent(f -> System.out.println("detected: " + f));

        try {
            DecodedImage img = Decoder.decode(bytes);
            System.out.printf("%dx%d channels=%s format=%s%n",
                img.width(), img.height(), img.channels(), img.format());
            byte[] pixels = img.data();          // RGB or RGBA, row-major
        } catch (DecodeException e) {
            System.err.println("decode failed: " + e.kind() + " " + e.detail());
        }
    }
}
```

Public surface: `Decoder.decode(byte[]) throws DecodeException`, `Decoder.detectFormat(byte[]) -> Optional<Format>`, `Decoder.supportedFormats() -> Set<Format>`. `DecodedImage` exposes `width(), height(), data(), channels(), format()` (the `data()` method returns a defensive copy — cache it if you'll touch it in a hot loop). `DecodeException extends IOException`; `Kind` enum is `UNSUPPORTED_FORMAT`, `CORRUPT_INPUT`, `TRUNCATED`, `UNSUPPORTED_FEATURE`.

Maven (until published):
```xml
<dependency>
  <groupId>io.rosetta</groupId>
  <artifactId>rosetta-image-decode</artifactId>
  <version>0.1.0</version>
</dependency>
```
(`mvn install` the local module first.)

---

## JavaScript / TypeScript

```ts
import { readFileSync } from "node:fs";
import {
    decode, detectFormat, supportedFormats, DecodeError,
} from "rosetta-image-decode";

const bytes = new Uint8Array(readFileSync("photo.jpg"));

const sniff = detectFormat(bytes);                      // "jpeg" | "png" | ... | null
console.log("detected:", sniff);

try {
    const img = await decode(bytes);                    // async — WASM init
    console.log(`${img.width}x${img.height} channels=${img.channels} format=${img.format}`);
    // img.data is Uint8Array, length = width * height * (3 or 4)
} catch (e) {
    if (e instanceof DecodeError) {
        console.error("decode failed:", e.kind, e.detail);
    } else {
        throw e;
    }
}
```

**Important: `decode()` is async.** The JPEG/WebP/HEIC backends are WASM modules and initialize asynchronously. Always `await`.

Exported symbols: `decode(bytes: Uint8Array): Promise<DecodedImage>`, `detectFormat(bytes): Format | null`, `supportedFormats(): Format[]`, `DecodeError` (class with `.kind`, `.format`, `.detail`). Types: `DecodedImage { width, height, data: Uint8Array, channels: 3 | 4, format }`, `Format = "bmp" | "png" | "gif" | "jpeg" | "webp" | "tiff" | "heic"`, `DecodeErrorKind = "unsupportedFormat" | "corruptInput" | "truncated" | "unsupportedFeature"`.

**HEIC caveat:** the JS port's HEIC output may diverge from other ports by ±1–2 per pixel due to the bundled libheif WASM build differing slightly from system libheif. Plenty good for perceptual hashing; not bit-identical to Rust/Go/Java/Swift HEIC. See `js/rosetta-image-decode/DECODER_NOTES.md`.

---

## Swift

```swift
import Foundation
import RosettaImageDecode

let bytes = Array(try Data(contentsOf: URL(fileURLWithPath: "photo.jpg")))

if let fmt = Decoder.detectFormat(bytes) {
    print("detected:", fmt)
}

do {
    let img = try Decoder.decode(bytes)
    print("\(img.width)x\(img.height) channels=\(img.channels) format=\(img.format)")
    // img.data is [UInt8], count = width * height * (3 or 4)
} catch let e as DecodeError {
    print("decode failed:", e)
}
```

Public surface: `enum Decoder` with static methods `decode(_ bytes: [UInt8]) throws -> DecodedImage`, `detectFormat(_ bytes: [UInt8]) -> Format?`, `supportedFormats() -> [Format]`. `DecodedImage` is a `struct` with `width: Int`, `height: Int`, `data: [UInt8]`, `channels: Channels`, `format: Format`. `DecodeError` is an `enum` conforming to `Error, Equatable` with cases `.unsupportedFormat(detail:)`, `.corruptInput(format:detail:)`, `.truncated(format:detail:)`, `.unsupportedFeature(format:detail:)`.

`Package.swift` dependency (until published):
```swift
.package(path: "../rosetta-image-decode/swift/RosettaImageDecode"),
```

The 4 system-library targets (`Cjpeg`, `Cwebp`, `Ctiff`, `Cheif`) are internal — consumers only see the `RosettaImageDecode` module. Build system handles linking via `pkg-config`. On macOS you may need `PKG_CONFIG_PATH=$(brew --prefix libheif)/lib/pkgconfig:$(brew --prefix libtiff)/lib/pkgconfig swift build` if Homebrew put the libraries in keg-only paths.

---

## End-to-end example — same hash in 5 languages

The whole point of this project pair (`rosetta-image-decode` + `rosetta-image-hash`) is byte-exact equivalence. Here's the same image hashed identically in every port:

**Python (the reference):**
```python
from PIL import Image
import imagehash
img = Image.open("photo.jpg").convert("RGB")
print(imagehash.phash(img, hash_size=8))                  # "c3f8a1b27d0e4f96"
```

**Rust:**
```rust
let bytes = std::fs::read("photo.jpg")?;
let decoded = rosetta_image_decode::decode(&bytes)?;
let img = image::RgbImage::from_raw(
    decoded.width as u32, decoded.height as u32, decoded.data
).unwrap();
let h = rosetta_image_hash::phash(&image::DynamicImage::ImageRgb8(img), 8)?;
println!("{}", h.to_hex());                                // "c3f8a1b27d0e4f96"
```

**Go:**
```go
bytes, _ := os.ReadFile("photo.jpg")
decoded, _ := imagedecode.Decode(bytes)
img := goImageFromBuffer(decoded.Width, decoded.Height, decoded.Data)  // helper
h, _ := imagehash.PHash(img, 8)
fmt.Println(h.ToHex())                                     // "c3f8a1b27d0e4f96"
```

**JS:**
```ts
const bytes = new Uint8Array(readFileSync("photo.jpg"));
const decoded = await decode(bytes);
const rgb = { width: decoded.width, height: decoded.height, data: decoded.data };
const h = phash(rgb, 8);
console.log(h.toString());                                  // "c3f8a1b27d0e4f96"
```

**Swift:**
```swift
let bytes = Array(try Data(contentsOf: URL(fileURLWithPath: "photo.jpg")))
let decoded = try Decoder.decode(bytes)
let rgb = RGBImage(width: decoded.width, height: decoded.height, data: decoded.data)
let h = try phash(rgb, hashSize: 8)
print(h)                                                    // "c3f8a1b27d0e4f96"
```

**Java:**
```java
byte[] bytes = Files.readAllBytes(Path.of("photo.jpg"));
DecodedImage decoded = Decoder.decode(bytes);
BufferedImage img = bufferedImageFromBuffer(decoded);       // helper
ImageHash h = PHash.compute(img, 8);
System.out.println(h);                                      // "c3f8a1b27d0e4f96"
```

(The Go/Java/Swift adapter helpers are a few lines each — the projects don't ship them yet because the conversion target differs per usage; trivial to write.)
