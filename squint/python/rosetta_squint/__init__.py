"""rosetta_squint — point at an image (path or bytes), get the same phash
hex string as every other rosetta-squint port for the same input.

This is the Python implementation of the cross-language perceptual-hash
convenience API. It depends on `rosetta_imagehash` (which re-exports
upstream `imagehash` + adds `whash_db4_robust`) and uses PIL/Pillow for
decoding most formats. HEIC is decoded via a ctypes wrapper around
system libheif so that output matches the 5 native ports (which all FFI
to the same system libheif).

Each public function comes in three flavors:
- `phash(path_or_image, ...)` — accept a file path str/Path OR a PIL.Image
- `phash_bytes(bytes, ...)` — accept raw image bytes in memory

API matches the same names in the non-Python rosetta-squint ports
(`phash`, `dhash`, `average_hash`, `whash_haar`, `colorhash`,
`crop_resistant_hash`, plus the extensions `whash_db4`, `whash_db4_robust`,
`phash_simple`, `dhash_vertical`).
"""

from __future__ import annotations

from ._impl import (
    # Path-based entries
    average_hash,
    colorhash,
    crop_resistant_hash,
    dhash,
    dhash_vertical,
    phash,
    phash_simple,
    whash_db4,
    whash_db4_robust,
    whash_haar,
    # Bytes-based entries
    average_hash_bytes,
    colorhash_bytes,
    crop_resistant_hash_bytes,
    dhash_bytes,
    dhash_vertical_bytes,
    phash_bytes,
    phash_simple_bytes,
    whash_db4_bytes,
    whash_db4_robust_bytes,
    whash_haar_bytes,
    # Decode helpers
    decode_bytes,
    decode_file,
)

# Re-export hash types so callers don't need to import rosetta_imagehash
# separately.
from rosetta_imagehash import (
    ImageHash,
    ImageMultiHash,
    hex_to_flathash,
    hex_to_hash,
    hex_to_multihash,
)

__version__ = "0.1.0"

__all__ = [
    "ImageHash",
    "ImageMultiHash",
    "hex_to_flathash",
    "hex_to_hash",
    "hex_to_multihash",
    "decode_file",
    "decode_bytes",
    "average_hash",
    "average_hash_bytes",
    "colorhash",
    "colorhash_bytes",
    "crop_resistant_hash",
    "crop_resistant_hash_bytes",
    "dhash",
    "dhash_bytes",
    "dhash_vertical",
    "dhash_vertical_bytes",
    "phash",
    "phash_bytes",
    "phash_simple",
    "phash_simple_bytes",
    "whash_db4",
    "whash_db4_bytes",
    "whash_db4_robust",
    "whash_db4_robust_bytes",
    "whash_haar",
    "whash_haar_bytes",
    "__version__",
]
