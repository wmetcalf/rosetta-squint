# rosetta-squint-decode — Swift port

Byte-exact PIL-compatible image decoder library, Swift port (5.9+, Linux + macOS).

Decodes BMP, PNG, GIF (first frame), JPEG, WebP, TIFF, and HEIC to a raw RGB or RGBA byte buffer matching what `PIL.Image.open(...).tobytes()` produces.

## Quick start

```swift
import Foundation
import RosettaSquintDecode

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

## Build + test

```
swift build
swift test                  # 48 tests, all passing on Linux x86-64
```

Tests resolve fixtures and goldens from `../../spec/`. Run from this package root.

**Linux:** install Swift 5.9+ from swift.org. Development used `/opt/swift/swift-5.9.2-RELEASE-ubuntu22.04/usr/bin` — export it on `$PATH` first.

**macOS:** Xcode 15+ ships Swift 5.9. The `.brew([...])` providers in `Package.swift` should resolve the system libraries from Homebrew, but **macOS is not tested by us.** If pkg-config can't find the keg-only Homebrew formulas:

```
PKG_CONFIG_PATH="$(brew --prefix libheif)/lib/pkgconfig:$(brew --prefix libtiff)/lib/pkgconfig:$(brew --prefix jpeg-turbo)/lib/pkgconfig:$(brew --prefix webp)/lib/pkgconfig" \
    swift test
```

## API

```swift
public enum Decoder {
    public static func decode(_ bytes: [UInt8]) throws -> DecodedImage
    public static func detectFormat(_ bytes: [UInt8]) -> Format?
    public static func supportedFormats() -> [Format]
}

public struct DecodedImage: Equatable {
    public let width: Int
    public let height: Int
    public let data: [UInt8]            // row-major, count = width * height * (3 or 4)
    public let channels: Channels       // .rgb | .rgba
    public let format: Format
}

public enum Format: String { case bmp, png, gif, jpeg, webp, tiff, heic, emf, wmf }

public enum DecodeError: Error, Equatable {
    case unsupportedFormat(detail: String)
    case corruptInput(format: Format, detail: String)
    case truncated(format: Format, detail: String)
    case unsupportedFeature(format: Format, detail: String)
}
```

(The `.emf` / `.wmf` cases of `Format` exist but are never returned — those formats are out of scope for v1.)

## Dependencies

System C libraries (via `pkg-config`):

| Module | pkg-config name | Linux apt | macOS Homebrew |
|---|---|---|---|
| `Cjpeg` | `libturbojpeg` | `libturbojpeg0-dev` | `jpeg-turbo` |
| `Cwebp` | `libwebp` | `libwebp-dev` | `webp` |
| `Ctiff` | `libtiff-4` | `libtiff-dev` | `libtiff` |
| `Cheif` | `libheif` | `libheif-dev` | `libheif` |

Plus on Linux you'll also want `libheif-plugin-libde265`, `libde265-dev`, and `libde265-0` for HEVC decoding.

Full Ubuntu install:
```
sudo apt install \
    libturbojpeg libturbojpeg0-dev \
    libwebp-dev libsharpyuv-dev \
    libtiff-dev \
    libheif-dev libheif-plugin-libde265 libde265-dev libde265-0 \
    pkg-config
```

macOS:
```
brew install jpeg-turbo webp libtiff libheif pkg-config
```

SwiftPM package dependency: [tayloraswift/swift-png](https://github.com/tayloraswift/swift-png) `4.0.0..<4.4.0` for PNG. GIF is a hand-written pure-Swift decoder in `Sources/RosettaSquintDecode/Internal/GIFDecoder.swift` (~518 LOC, LZW + 4-pass interlacing).

## Package.swift

Not on Swift Package Index yet. Path-based dependency:

```swift
.package(path: "../rosetta-squint-decode/swift/RosettaSquintDecode"),
```

Then in your target:
```swift
.product(name: "RosettaSquintDecode", package: "RosettaSquintDecode"),
```

## Format support

| Format | Status | Backend |
|---|---|---|
| BMP | byte-exact | hand-written |
| PNG | byte-exact | swift-png 4.x |
| GIF | byte-exact, first frame only | hand-written pure-Swift |
| JPEG | byte-exact | Cjpeg → libturbojpeg |
| WebP | byte-exact | Cwebp → libwebp |
| TIFF | byte-exact, baseline only | Ctiff → libtiff (TIFFReadRGBAImageOriented) |
| HEIC | byte-exact, single still image | Cheif → libheif 1.17 |

## See also

- [USAGE.md](../../USAGE.md)
- [STATUS.md](../../STATUS.md)
- [`../../spec/SPEC.md`](../../spec/SPEC.md)

## License

BSD-2-Clause.
