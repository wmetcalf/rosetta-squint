# Security

`rosetta-image-hash` operates on **already-decoded RGB or RGBA byte buffers**. The library does no image-format parsing (except the `decodePng` / `decodePNG` convenience helpers in the JS and Swift ports). Compared to `rosetta-image-decode`, the attack surface is much smaller.

## Threat model

1. Caller provides a buffer with mismatched `width × height × channels` length → library returns a typed error.
2. Caller provides hostile `hex` strings to `hex_to_hash` / `hex_to_flathash` → library validates length and characters before allocation.
3. Caller provides excessive `hash_size` (or `binbits` for colorhash) → library allocates O(hash_size²) memory.

## Defenses

### Hash size validation

Every algorithm validates its size argument before any allocation:

- `average_hash`, `dhash`, `phash`: `hash_size >= 2`
- `whash`: `hash_size >= 2` AND `hash_size` is a power of two AND `hash_size <= image_scale` (computed from the input dimensions, so it's also bounded by the image)
- `colorhash`: `binbits >= 1`

Invalid sizes throw `ImageHashError::InvalidHashSize` (or per-port equivalent — `IllegalArgumentException` in Java, `ImageHashError.invalidHashSize` in Swift, etc.).

### Hex parsing validation

`hex_to_hash(hex)`:
- Rejects non-square lengths (must be `4 * n²` chars for a perfect-square bit array)
- Rejects invalid hex characters
- Allocates O(length²) bits — no exponential blowup

`hex_to_flathash(hex, hash_size)`:
- Rejects when `len(hex) * 4 != 14 * hash_size` (colorhash shape is fixed at 14 × binbits)
- Rejects invalid hex characters

### Input buffer validation

The decoders (when present: `decodePng`, `decodePNG`) reject malformed PNG signatures and propagate errors from the underlying PNG library (`image::ImageReader` Rust, `pngjs` JS, swift-png). They do **not** apply a MAX_PIXELS cap — the hash library is intended for already-trusted RGB input. **If you're hashing untrusted images, decode them with [rosetta-image-decode](../decode) first**, which does enforce MAX_PIXELS.

## Self-inflicted DoS to watch out for

The library does NOT impose an upper bound on `hash_size`. A caller passing `hash_size = 100_000` will allocate ~10 GB for the phash intermediate buffer. **The library trusts the caller's `hash_size` argument.**

Recommended safe ranges:

- `hash_size`: 8 (default) through 64. Most production use cases use 8 or 16.
- `binbits`: 3 (default) through 8.
- `highfreqFactor`: 4 (default), rarely changed.

Going above `hash_size = 128` is almost never useful — the algorithm starts encoding noise, not perceptual structure.

## Known native dependencies

Only the PNG decoder helpers depend on native code:

| Port | PNG backend |
|---|---|
| Rust | `image` crate 0.25 (PNG feature, pure Rust) |
| Go | `image/png` from the stdlib (pure Go) |
| Java | `javax.imageio.ImageIO` |
| JS | `pngjs ^7` (pure JS) |
| Swift | `tayloraswift/swift-png` 4.x (pure Swift) |

No C library FFI in this project. All PNG decoders are pure-language implementations.

## Reporting a vulnerability

If you find a security issue, please email william.metcalf@gmail.com — do not open a public issue.
