# rosetta-squint-hash — Java port

Byte-exact port of Python `imagehash==4.3.2` algorithms to Java 17+.

The hex string produced here equals the hex Python `imagehash` produces for the same image, algorithm, and hash size.

## Quick start

```java
import io.github.wmetcalf.rosettasquint.hash.PHash;
import io.github.wmetcalf.rosettasquint.hash.Hex;
import io.github.wmetcalf.rosettasquint.hash.ImageHash;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

public class Demo {
    public static void main(String[] args) throws Exception {
        BufferedImage img = ImageIO.read(new File("photo.png"));

        ImageHash h = PHash.compute(img, 8);
        System.out.println(h);                              // "c3f8a1b27d0e4f96"

        BufferedImage other = ImageIO.read(new File("other.png"));
        int distance = h.subtract(PHash.compute(other, 8));

        ImageHash restored = Hex.hexToHash(h.toString());
        System.out.println(restored.equals(h));             // true
    }
}
```

Input is `BufferedImage` of any type — non-`TYPE_INT_RGB` inputs are normalized internally via `Graphics2D.drawImage` composite-on-black, matching PIL `convert('RGB')`.

## Build + test

```
cd java
mvn -B test                 # 53 tests, all passing on Linux x86-64
```

Tests read fixtures and goldens from `../spec/`. Run `mvn` from this directory.

## API

| Class.Method | Signature |
|---|---|
| `AverageHash.compute` | `(BufferedImage)` / `(BufferedImage, int hashSize)` |
| `DHash.compute` | `(BufferedImage, int hashSize)` |
| `PHash.compute` | `(BufferedImage, int hashSize)` / `(BufferedImage, int hashSize, int highfreqFactor)` |
| `WHashHaar.compute` | `(BufferedImage, int hashSize)` — `hashSize` must be power of 2 |
| `ColorHash.compute` | `(BufferedImage)` / `(BufferedImage, int binbits)` |
| `ColorHash.binEncode` | `(int v, int binbits) -> boolean[]` |
| `Hex.hexToHash` | `(String) -> ImageHash` |
| `Hex.hexToFlathash` | `(String, int hashSize) -> ImageHash` |

All `compute(...)` methods return `ImageHash`. `ImageHash` exposes `toString()` (hex), `subtract(ImageHash) -> int`, `equals(Object)`, `hashCode()`. Invalid sizes throw `IllegalArgumentException`.

## Test groups

| Group | Purpose |
|---|---|
| 1 | Per-kernel unit tests against `../spec/*_cases.json` and `lanczos_cases/*.bin`. |
| 2 | Each algorithm × fixture × size from `goldens.json`, using pre-decoded RGB buffers (decoder-bypassed). |
| 3 | Same as Group 2 but loads PNG via `ImageIO.read` (end-to-end). PNG decoder exemptions documented in `DECODER_NOTES.md`. |
| 4 | Hex round-trip on every Group-2 hash. |
| 5 | Hamming distance + error semantics. |

## Parity guarantee

Every test in Groups 1–4 asserts byte-exact equality with Python `imagehash 4.3.2`. Any Group-3 failure that passes Group 2 is a PNG decoder discrepancy; see `DECODER_NOTES.md`.

## Maven dependency

Maven coordinates (Maven Central):

```xml
<dependency>
  <groupId>io.github.wmetcalf</groupId>
  <artifactId>rosetta-squint-hash</artifactId>
  <version>1.0.0</version>
</dependency>
```

Compile deps: JUnit 5 (test scope), Jackson (test scope only — reads goldens JSON). No runtime third-party deps.

## See also

- [USAGE.md](../USAGE.md) — examples for all 6 ports
- [STATUS.md](../STATUS.md)
- [`../spec/SPEC.md`](../spec/SPEC.md)

## License

BSD-2-Clause.
