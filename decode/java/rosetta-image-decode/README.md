# rosetta-image-decode — Java port

Byte-exact PIL-compatible image decoder library, Java port (Java 17+).

Decodes BMP, PNG, GIF (first frame), JPEG, WebP, TIFF, and HEIC to a raw RGB or RGBA byte buffer matching what `PIL.Image.open(...).tobytes()` produces.

## Quick start

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
            byte[] pixels = img.data();    // RGB or RGBA, row-major (defensive copy — cache it)
        } catch (DecodeException e) {
            System.err.println("decode failed: " + e.kind() + " " + e.detail());
        }
    }
}
```

## Build + test

```
cd java/rosetta-image-decode
mvn -B -ntp test               # 40 tests, all passing on Linux x86-64
```

Tests resolve fixtures and goldens from `../../spec/`. Run from this module root.

## API

| Method | Signature |
|---|---|
| `Decoder.decode` | `(byte[]) -> DecodedImage` (throws `DecodeException`) |
| `Decoder.detectFormat` | `(byte[]) -> Optional<Format>` |
| `Decoder.supportedFormats` | `() -> Set<Format>` |

```java
public final class DecodedImage {
    public int width();
    public int height();
    public byte[] data();      // defensive copy; cache it if you reuse
    public Channels channels();    // RGB or RGBA
    public Format format();        // BMP, PNG, GIF, JPEG, WEBP, TIFF, HEIC
}

public class DecodeException extends IOException {
    public Kind kind();            // UNSUPPORTED_FORMAT | CORRUPT_INPUT | TRUNCATED | UNSUPPORTED_FEATURE
    public Format format();        // may be null
    public String detail();
}
```

## Dependencies (Maven)

```xml
<dependency>
  <groupId>org.libjpeg-turbo</groupId>
  <artifactId>turbojpeg</artifactId>
  <version>2.1.5</version>
  <scope>system</scope>
  <systemPath>/usr/share/java/turbojpeg.jar</systemPath>
</dependency>
<dependency>
  <groupId>org.sejda.imageio</groupId>
  <artifactId>webp-imageio</artifactId>
  <version>0.1.6</version>
</dependency>
<dependency>
  <groupId>com.twelvemonkeys.imageio</groupId>
  <artifactId>imageio-tiff</artifactId>
  <version>3.10.1</version>
</dependency>
<dependency>
  <groupId>net.java.dev.jna</groupId>
  <artifactId>jna</artifactId>
  <version>5.18.1</version>
</dependency>
```

PNG and GIF use `javax.imageio` from the JDK. The system-scope `turbojpeg.jar` is from the Ubuntu package `libturbojpeg-java` (`/usr/share/java/turbojpeg.jar`). HEIC uses a hand-written JNA wrapper around `libheif.so.1` (in `io.rosetta.imagedecode.internal.libheif`).

System packages (Ubuntu):

```
sudo apt install \
    libturbojpeg libturbojpeg0-dev libturbojpeg-java \
    libwebp-dev libsharpyuv-dev \
    libtiff-dev \
    libheif-dev libheif-plugin-libde265 libde265-dev libde265-0 \
    pkg-config
```

macOS Homebrew:
```
brew install jpeg-turbo webp libtiff libheif
```

(On macOS you'll need a Mac-native TurboJPEG JAR — adjust `systemPath` in pom.xml.)

## Maven dependency

Not on Maven Central yet. After `mvn install` from this directory:

```xml
<dependency>
  <groupId>io.rosetta</groupId>
  <artifactId>rosetta-image-decode</artifactId>
  <version>0.1.0</version>
</dependency>
```

## Format support

| Format | Status | Backend |
|---|---|---|
| BMP | byte-exact | hand-written |
| PNG | byte-exact | `javax.imageio` |
| GIF | byte-exact, first frame only | `javax.imageio` |
| JPEG | byte-exact | TurboJPEG via system JAR |
| WebP | byte-exact | `sejda:webp-imageio` |
| TIFF | byte-exact, baseline only | `twelvemonkeys:imageio-tiff` |
| HEIC | byte-exact, single still image | hand-written JNA wrapper around libheif 1.17 |

## See also

- [USAGE.md](../../USAGE.md)
- [STATUS.md](../../STATUS.md)
- [`../../spec/SPEC.md`](../../spec/SPEC.md)

## License

BSD-2-Clause.
