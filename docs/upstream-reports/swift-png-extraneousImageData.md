# swift-png upstream issue: false-positive `extraneousImageData` on multi-IDAT PNGs

**For filing at:** https://github.com/tayloraswift/swift-png/issues

Pre-filled bug report below — copy/paste into a new GitHub issue.

---

## Title

`extraneousImageData` false-positive when IDAT chunks split at certain
deflate-stream offsets (PIL's default 64 KB boundary triggers it)

## Version tested

- swift-png **4.3.0** (the version we pin and reproduce on).

We did not run the bisection against 4.5.1, but inspecting the diff between
4.3.0 and 4.5.1 shows no changes to the LZ77 inflator's `push()`/`pull()`
codepath — only `format the code` and doc updates — so we expect the bug
still reproduces. If a maintainer reviewing this confirms the inflator was
changed in any subsequent release, please disregard the "still present"
implication.

## Reproduction

Any PNG that splits its IDAT chunk at a deflate-stream offset in roughly the
[32 KB .. 65 KB] band can trip this. PIL emits multi-IDAT PNGs by default at
the 64 KB chunk boundary, so any PIL-encoded PNG ≥ 64 KB of compressed pixel
data is a candidate.

Minimal repro (Swift, using swift-png directly):

```swift
import PNG

// Read bytes from any PIL-emitted ≥64KB PNG.
let bytes: [UInt8] = try Array(Data(contentsOf: URL(fileURLWithPath: "test.png")))

// Decode:
do {
    let image = try PNG.Image(bytes: bytes)
    print("ok: \(image.size)")
} catch let e {
    print("error: \(e)")  // → corruptInput(...extraneousImageData)
}
```

## Bisection

We generated 706 PNGs with identical pixel data but the IDAT split at offsets
1, 101, 201, … 70 501 (100-byte steps). Results:

| IDAT-split offset | swift-png |
|---|---|
| `1..32700` | passes |
| `32801..65500` | **fails with `extraneousImageData`** |
| `65601..70501` | passes |

The ~33 KB failure band suggests a sliding-window or back-reference offset
calculation bug crossing the `push()` seam in the LZ77 inflator.

Content-dependent: the same offset scan on a different PNG's pixels shows
every split position passes. So whether the bug triggers depends on the
specific deflate stream's back-reference layout near the boundary, not just
the boundary position itself.

## Affected check

`swift-png/Sources/PNG/Decoding/PNG.Decoder.swift:143-147` — after pulling
all scanlines, the LZ77 inflator's output buffer reports extra unread bytes
(`pull()` not empty). The check is correct in concept; the bug is upstream
in the deflate inflator that's leaving the bytes in the buffer.

## Validation that the inputs ARE structurally valid

For all failing PNGs we verified:
- `file(1)`, `pngcheck`, libpng, Python PIL, Go `image/png`, Java `javax.imageio`,
  Rust `image::png`, JS `pngjs` all decode them successfully.
- CRC32s pass on every chunk.
- zlib reaches EOF cleanly with `unused_data=0`.
- Decompressed size equals expected `h * (1 + w * bpp)` exactly.
- IDAT bodies concatenated form a valid deflate stream.

So this is genuinely a swift-png inflator bug, not a malformed-input case.

## Workaround we deployed

Until upstream fixes the bug, we added a pre-pass at our decoder boundary
that walks the PNG chunk stream, concatenates all IDAT bodies into a single
merged IDAT, recomputes the CRC32, and re-emits the PNG with one combined
IDAT chunk before invoking swift-png. This eliminates the inflator's
boundary issue because there's only one IDAT to inflate.

Our implementation is at `decode/swift/RosettaImageDecode/Sources/RosettaImageDecode/Internal/PNGDecoder.swift`
(open-source under BSD-2-Clause if useful as a reference).

## Why this is worth fixing upstream

Every other PNG decoder we tested (libpng, Go stdlib, Java imageio, Rust
`image` crate, JS pngjs, Python PIL) handles multi-IDAT PNGs natively
because the PNG spec explicitly permits and encourages chunking IDAT for
streaming. swift-png is the only library in our cross-port comparison that
rejects them.

## Cooperation

Happy to provide:
- Sample PNGs that trigger the bug
- Our re-chunk workaround code
- The bisection script

Reach: william.metcalf@gmail.com
