# rosetta-image-hash — Swift port

Byte-exact port of Python `imagehash==4.3.2` algorithms to Swift 5.9+ (Linux + macOS).

The hex string produced here equals the hex Python `imagehash` produces for the same image, algorithm, and `hashSize`.

## Quick start

```swift
import Foundation
import RosettaImageHash

let bytes = Array(try Data(contentsOf: URL(fileURLWithPath: "photo.png")))
let img = try decodePNG(bytes)                         // RGBImage

let h = try phash(img, hashSize: 8)
print(h)                                               // "c3f8a1b27d0e4f96"

// Hamming distance
let other = try phash(decodePNG(otherBytes), hashSize: 8)
let distance = try h.subtract(other)

// Round-trip
let restored = try hexToHash(String(describing: h))
print(restored == h)                                   // true
```

If you already have RGB pixels (e.g. from `CGImage` rendering), construct an `RGBImage` directly:

```swift
let img = RGBImage(width: w, height: h, data: rgbBytes, channels: .rgb)
let h = try phash(img, hashSize: 8)
```

## Build + test

```
swift build
swift test                    # 54 tests, all passing on Linux x86-64
```

Tests resolve fixtures and goldens from `../../spec/`. Run from the package root.

**Linux:** install Swift 5.9+ from swift.org. Development used `/opt/swift/swift-5.9.2-RELEASE-ubuntu22.04/usr/bin` — export it on `$PATH` first.

**macOS:** Xcode 15+ ships Swift 5.9. Should just work — pure Swift, no system libraries. **Not tested by us on macOS.**

## API

| Function | Signature |
|---|---|
| `averageHash` | `(_ image: RGBImage, hashSize: Int) throws -> Hash` |
| `dhash` | `(_ image: RGBImage, hashSize: Int) throws -> Hash` |
| `phash` | `(_ image: RGBImage, hashSize: Int, highfreqFactor: Int = 4) throws -> Hash` |
| `whashHaar` | `(_ image: RGBImage, hashSize: Int) throws -> Hash` — `hashSize` must be power of 2 |
| `colorhash` | `(_ image: RGBImage, binbits: Int) throws -> Hash` |
| `colorhashBinEncode` | `(_ v: Int, binbits: Int) -> [Bool]` |
| `hexToHash` | `(_ hex: String) throws -> Hash` |
| `hexToFlathash` | `(_ hex: String, hashSize: Int) throws -> Hash` |
| `decodePNG` | `(_ bytes: [UInt8]) throws -> RGBImage` |

`Hash` conforms to `Equatable`, `Hashable`, `CustomStringConvertible` (hex string), and exposes `subtract(_:) throws -> Int`.

`RGBImage` is a struct with `width: Int`, `height: Int`, `data: [UInt8]`, `channels: Channels (.rgb | .rgba)`.

Errors throw `ImageHashError` — an `enum` with cases for invalid hash sizes, invalid binbits, non-power-of-2, invalid hex, shape mismatch.

## Package.swift

Not on Swift Package Index yet. Path-based dependency:

```swift
.package(path: "../rosetta-image-hash/swift/RosettaImageHash"),
```

Then in your target:
```swift
.product(name: "RosettaImageHash", package: "RosettaImageHash"),
```

Package deps: [tayloraswift/swift-png](https://github.com/tayloraswift/swift-png) pinned `4.0.0..<4.4.0`. Pure Swift, no system libraries.

## See also

- [USAGE.md](../../USAGE.md) — examples for all 5 ports
- [STATUS.md](../../STATUS.md)
- [`../../spec/SPEC.md`](../../spec/SPEC.md)

## License

BSD-2-Clause.
