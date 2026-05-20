# rosetta-image-hash — Swift port

Byte-exact port of Python `imagehash==4.3.2` algorithms to Swift 5.9+ (Linux + macOS).

## Build + test

```
cd ~/rosetta-image-hash/swift/RosettaImageHash
swift build
swift test
```

Tests resolve fixtures and goldens from `../../spec/` (relative to the package root). Run `swift test` from the package root.

## v1 algorithms

`averageHash`, `dhash`, `phash`, `whashHaar`, `colorhash`, plus `hexToHash` and `hexToFlathash`. All take an `RGBImage` (`struct RGBImage { width, height, data: [UInt8], channels: .rgb | .rgba }`) and return a `Hash` (or throw `ImageHashError`).

For PNG callers, a `decodePNG(_ bytes: [UInt8]) throws -> RGBImage` helper is exported.

## Dependencies

- Runtime: `swift-png` 4.x (pure-Swift PNG decoder)

## License

BSD-2-Clause.
