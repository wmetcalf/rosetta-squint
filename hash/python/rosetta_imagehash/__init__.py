"""rosetta_imagehash — cross-port-stable extensions to Python `imagehash`.

This package re-exports the public surface of `imagehash` (PyPI 4.3.2+) and
adds extensions that don't exist upstream but are implemented across the 5
ports of rosetta-image-hash (Rust, Go, Java, JS/TS, Swift).

The most important extension is `whash_db4_robust`, a snap-to-zero variant
of `whash(mode='db4')` that resolves the ULP-level tie-point ambiguity on
pathological symmetric inputs at the cost of byte-exact parity with upstream
`imagehash` on those specific inputs. Real-world photos are unaffected and
produce the same hash as `imagehash.whash(mode='db4')`.

See ``spec/SPEC.md`` §whash_db4_robust for the full specification.
"""

from __future__ import annotations

# Re-export the entire upstream public surface so callers can use this as a
# drop-in replacement.
from imagehash import (  # noqa: F401
    ImageHash,
    ImageMultiHash,
    average_hash,
    colorhash,
    crop_resistant_hash,
    dhash,
    dhash_vertical,
    hex_to_flathash,
    hex_to_hash,
    hex_to_multihash,
    old_hex_to_hash,
    phash,
    phash_simple,
    whash,
)

from ._impl import WHASH_DB4_ROBUST_EPS, whash_db4_robust

__version__ = "0.1.0"

__all__ = [
    "ImageHash",
    "ImageMultiHash",
    "average_hash",
    "colorhash",
    "crop_resistant_hash",
    "dhash",
    "dhash_vertical",
    "hex_to_flathash",
    "hex_to_hash",
    "hex_to_multihash",
    "old_hex_to_hash",
    "phash",
    "phash_simple",
    "whash",
    # Extensions
    "whash_db4_robust",
    "WHASH_DB4_ROBUST_EPS",
    "__version__",
]
