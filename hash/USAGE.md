# rosetta-squint-hash — Usage

API examples for all 6 ports (Python, Java, Go, Rust, JS/TS, Swift). For install steps and what's implemented, see [STATUS.md](./STATUS.md).

Every port follows the same shape:

1. Decode a PNG (or otherwise produce an RGB byte buffer) → port's image type.
2. Call `average_hash` / `phash` / `dhash` / `whash_haar` / `colorhash` with an integer `hash_size` (or `binbits` for colorhash).
3. Get back a `Hash` value. Convert it to hex with `.to_hex()` / `.toString()`. Compute Hamming distance with `.subtract(other)`.

The hex output is the **same** across all 6 ports and matches the Python `imagehash` package exactly.

---

## Python (reference — the thing the ports match)

```python
from PIL import Image
import imagehash

img = Image.open("photo.png")
h = imagehash.phash(img, hash_size=8)
print(str(h))                           # e.g. "c3f8a1b27d0e4f96"
print(h - imagehash.phash(other_img))   # Hamming distance (int)
```

---

## Rust

```rust
use image::ImageReader;
use rosetta_squint_hash::{phash, average_hash, dhash, whash_haar, colorhash, hex_to_hash};

fn main() -> Result<(), Box<dyn std::error::Error>> {
    let img = ImageReader::open("photo.png")?.decode()?;

    let h = phash(&img, 8)?;
    println!("{}", h.to_hex());                   // "c3f8a1b27d0e4f96"

    let other = phash(&ImageReader::open("other.png")?.decode()?, 8)?;
    let distance = h.subtract(&other)?;           // usize, Hamming distance
    println!("distance: {}", distance);

    // Round-trip from a stored hex string
    let restored = hex_to_hash("c3f8a1b27d0e4f96")?;
    assert_eq!(restored.to_hex(), h.to_hex());

    Ok(())
}
```

`Cargo.toml`:
```toml
[dependencies]
rosetta-squint-hash = { path = "../rosetta-squint-hash/rust/rosetta-squint-hash" }   # path; not on crates.io yet
image = { version = "0.25", default-features = false, features = ["png"] }
```

All public functions: `average_hash(img, size)`, `dhash(img, size)`, `phash(img, size)`, `phash_with_factor(img, size, highfreq_factor)`, `whash_haar(img, size)`, `colorhash(img, binbits)`, `colorhash_bin_encode(v, binbits)`, `hex_to_hash(hex)`, `hex_to_flathash(hex, hash_size)`.

---

## Go

```go
package main

import (
    "fmt"
    "image"
    _ "image/png"
    "os"

    "github.com/wmetcalf/rosetta-squint/hash/go/imagehash"
)

func main() {
    f, _ := os.Open("photo.png")
    defer f.Close()
    img, _, _ := image.Decode(f)

    h, err := imagehash.PHash(img, 8)
    if err != nil {
        panic(err)
    }
    fmt.Println(h.ToHex())                              // "c3f8a1b27d0e4f96"

    other, _ := imagehash.PHash(otherImage, 8)
    distance, _ := h.Subtract(other)
    fmt.Println("distance:", distance)

    // Restore from stored hex
    restored, _ := imagehash.HexToHash("c3f8a1b27d0e4f96")
    fmt.Println(restored.ToHex())
}
```

Public functions: `AverageHash(img, hashSize)`, `DHash(img, hashSize)`, `PHash(img, hashSize)`, `PHashWithFactor(img, hashSize, highfreqFactor)`, `WHashHaar(img, hashSize)`, `ColorHash(img, binbits)`, `ColorhashBinEncode(v, binbits)`, `HexToHash(hex)`, `HexToFlathash(hex, hashSize)`. All return `(Hash, error)`. `Hash` has `.ToHex()`, `.Subtract(other)`, `.Equals(other)`.

Input type is `image.Image` from the stdlib `image` package — register decoders with the usual `_ "image/png"`, `_ "image/jpeg"` blank imports, or pass anything that implements `image.Image`.

---

## Java

```java
import io.github.wmetcalf.rosettasquint.hash.AverageHash;
import io.github.wmetcalf.rosettasquint.hash.PHash;
import io.github.wmetcalf.rosettasquint.hash.DHash;
import io.github.wmetcalf.rosettasquint.hash.WHashHaar;
import io.github.wmetcalf.rosettasquint.hash.ColorHash;
import io.github.wmetcalf.rosettasquint.hash.Hex;
import io.github.wmetcalf.rosettasquint.hash.ImageHash;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

public class Demo {
    public static void main(String[] args) throws Exception {
        BufferedImage img = ImageIO.read(new File("photo.png"));

        ImageHash h = PHash.compute(img, 8);
        System.out.println(h);                          // "c3f8a1b27d0e4f96"

        BufferedImage other = ImageIO.read(new File("other.png"));
        int distance = h.subtract(PHash.compute(other, 8));
        System.out.println("distance: " + distance);

        ImageHash restored = Hex.hexToHash("c3f8a1b27d0e4f96");
        System.out.println(restored.equals(h));         // true
    }
}
```

Public static methods: `AverageHash.compute(img)`, `AverageHash.compute(img, hashSize)`, `PHash.compute(img, hashSize)`, `PHash.compute(img, hashSize, highfreqFactor)`, `DHash.compute(img, hashSize)`, `WHashHaar.compute(img, hashSize)`, `ColorHash.compute(img)`, `ColorHash.compute(img, binbits)`, `ColorHash.binEncode(v, binbits)`, `Hex.hexToHash(hex)`, `Hex.hexToFlathash(hex, hashSize)`. Input is `java.awt.image.BufferedImage`. `ImageHash` has `toString()` (hex), `equals()`, `hashCode()`, `subtract(other) → int`.

The Java port is not on Maven Central yet — depend on the local `java/` module from a sibling Maven project, or `mvn install` it once and reference `io.rosetta:rosetta-squint-hash:0.1.0-SNAPSHOT`.

---

## JavaScript / TypeScript

```ts
import { readFileSync } from "node:fs";
import {
    averageHash, phash, dhash, whashHaar, colorhash,
    decodePng, hexToHash,
} from "rosetta-squint-hash";

const bytes = new Uint8Array(readFileSync("photo.png"));
const img = decodePng(bytes);                            // RgbImage = { width, height, data }

const h = phash(img, 8);
console.log(h.toString());                               // "c3f8a1b27d0e4f96"

const other = phash(decodePng(new Uint8Array(readFileSync("other.png"))), 8);
console.log("distance:", h.subtract(other));

const restored = hexToHash("c3f8a1b27d0e4f96");
console.log(restored.toString() === h.toString());       // true
```

Named exports from the package root: `averageHash`, `dhash`, `phash`, `whashHaar`, `colorhash`, `colorhashBinEncode`, `hexToHash`, `hexToFlathash`, `decodePng`, plus the types `Hash`, `ImageHashError`, `RgbImage`, `ImageHashErrorKind`.

All hash functions are synchronous; the only async-ish thing in the library is `decodePng`, which is also synchronous (it uses `pngjs` in sync mode). `Hash` has `.toString()` (hex), `.subtract(other)`, `.equals(other)`.

---

## Swift

```swift
import Foundation
import RosettaSquintHash

let bytes = Array(try Data(contentsOf: URL(fileURLWithPath: "photo.png")))
let img = try decodePNG(bytes)                           // RGBImage

let h = try phash(img, hashSize: 8)
print(h)                                                 // "c3f8a1b27d0e4f96"

let other = try phash(decodePNG(otherBytes), hashSize: 8)
let distance = try h.subtract(other)
print("distance: \(distance)")

let restored = try hexToHash("c3f8a1b27d0e4f96")
print(restored == h)                                     // true
```

Public symbols: `averageHash(_:hashSize:)`, `dhash(_:hashSize:)`, `phash(_:hashSize:highfreqFactor:)` (default `highfreqFactor: 4`), `whashHaar(_:hashSize:)`, `colorhash(_:binbits:)`, `colorhashBinEncode(_:binbits:)`, `hexToHash(_:)`, `hexToFlathash(_:hashSize:)`, `decodePNG(_:)`. All hashing functions throw `ImageHashError`. `Hash` conforms to `Equatable`, `Hashable`, `CustomStringConvertible` (hex), and exposes `subtract(_:) throws -> Int`.

`Package.swift` dependency (path-based until the package is on Swift Package Index):
```swift
.package(path: "../rosetta-squint-hash/swift/RosettaSquintHash"),
```

---

## Hash sizes — what's valid

All hash functions take an integer `hash_size` (or `binbits` for `colorhash`). Constraints (consistent across ports):

| Algorithm | Constraint |
|---|---|
| `average_hash` | `hash_size >= 2` |
| `dhash` | `hash_size >= 2` |
| `phash` | `hash_size >= 2`; output is `hash_size * hash_size` bits |
| `whash_haar` | `hash_size` must be a power of 2, `>= 2`, and `<= image_scale`. Default `image_scale = 2^floor(log2(min(W,H)))` |
| `colorhash` | `binbits >= 1`; output shape is `(14, binbits)` — non-square — so use `hex_to_flathash(hex, binbits)` to restore, not `hex_to_hash` |

Invalid sizes throw / return a typed error (`ImageHashError::InvalidHashSize` in Rust, `IllegalArgumentException` in Java, etc.) — not a panic.

---

## Storing & comparing hashes across ports

The hex string is the cross-language interchange format. A `phash` of `photo.png` at `hash_size=8` produces **the exact same hex** in Python, Rust, Go, Java, JS, and Swift. You can:

- Hash an image in Java, store the hex in a database, query for similar images later from a Go or Rust service.
- Hash a million images in parallel via Go workers and compare against a reference set computed in Python — no risk of "library X uses a different mean rounding rule".

For `colorhash` specifically: the hex string represents a `(14, binbits)` matrix, not a square. Use `hex_to_flathash(hex, binbits)` to restore, and remember that the Hamming distance is over `14 * binbits` bits.
