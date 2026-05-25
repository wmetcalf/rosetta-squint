"""rosetta_imagehash — cross-port-stable variants of Python `imagehash`.

This package re-exports the public surface of `imagehash` (PyPI 4.3.2+) and
overrides every hash algorithm with a port-local wrapper. Two classes of
overrides:

1. **RGBA composite overrides** — every hash function (``average_hash``,
   ``dhash``, ``dhash_vertical``, ``whash``, ``colorhash``,
   ``crop_resistant_hash``, plus the four below) first composites RGBA / LA
   input against opaque black using the truncated 8-bit formula
   ``out_c = (src_c * alpha) // 255``. PIL's default ``convert('L')`` /
   ``convert('HSV')`` on RGBA inputs would simply *drop* the alpha channel
   without compositing, which diverges from every native port (Rust, Go,
   Java, JS, Swift) on any partial-alpha input.

2. **Snap-applying overrides** — ``phash``, ``phash_simple``, ``whash_db4``,
   ``whash_db4_robust`` apply both the RGBA composite *and* a
   snap-to-threshold tie-break (``bit = v > threshold + SNAP_EPS``). The
   snap eliminates ULP-level FP-noise divergence between this Python
   reference and the matching Rust / Go / Java / JS / Swift ports,
   especially at large hash sizes (32, 64) where many DCT / wavelet
   coefficients cluster within ULP of the threshold.

The behaviour at sizes 4, 8, and 16 is *intentionally* allowed to drift
from upstream `imagehash` for certain pathological inputs — see
``spec/SPEC.md`` §"Threshold tie-break" for the rationale.

Also provides:

- ``whash_db4_robust`` — the original snap-to-zero extension for
  pathological symmetric inputs. Now additionally applies the snap-to-
  threshold tie-break and the RGBA composite preamble.

Callers who need byte-exact upstream parity on any input (and are willing
to give up cross-port stability for partial-alpha or near-threshold
inputs) should ``import imagehash`` directly.

See ``spec/SPEC.md`` for the full pipeline-by-pipeline specification.
"""

from __future__ import annotations

# Re-export the upstream public surface that we DON'T override (constants,
# hash classes, hex helpers). Every algorithm name is imported from
# ._impl below as a port-local override.
from imagehash import (  # noqa: F401
    ANTIALIAS,
    ImageHash,
    ImageMultiHash,
    hex_to_flathash,
    hex_to_hash,
    hex_to_multihash,
    old_hex_to_hash,
)

from ._impl import (  # noqa: F401
    SNAP_EPS,
    WHASH_DB4_ROBUST_EPS,
    average_hash,
    colorhash,
    crop_resistant_hash,
    dhash,
    dhash_vertical,
    phash,
    phash_simple,
    whash,
    whash_db4,
    whash_db4_robust,
)

__version__ = "0.1.0"

__all__ = [
    "ANTIALIAS",
    "ImageHash",
    "ImageMultiHash",
    "hex_to_flathash",
    "hex_to_hash",
    "hex_to_multihash",
    "old_hex_to_hash",
    # RGBA-compositing overrides
    "average_hash",
    "colorhash",
    "crop_resistant_hash",
    "dhash",
    "dhash_vertical",
    "whash",
    # Snap + composite overrides
    "phash",
    "phash_simple",
    "whash_db4",
    "whash_db4_robust",
    # Constants
    "WHASH_DB4_ROBUST_EPS",
    "SNAP_EPS",
    "__version__",
]
