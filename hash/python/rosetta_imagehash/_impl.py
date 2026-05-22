"""Implementation of rosetta_imagehash extensions.

The single substantive function is `whash_db4_robust`. See module docstring
in ``__init__.py`` and ``spec/SPEC.md`` §whash_db4_robust for the rationale.
"""

from __future__ import annotations

import numpy as np
import pywt
import imagehash
from PIL import Image

WHASH_DB4_ROBUST_EPS: float = 1e-12
"""ε threshold for snap-to-zero. Coefficients with ``|c| < EPS`` are snapped
to exactly 0 before median + threshold, eliminating ULP-level tie-point
ambiguity on pathological symmetric inputs.

Fixed across all 5 ports — do not vary per-call without coordinating a spec
change."""


def whash_db4_robust(
    image: Image.Image,
    hash_size: int = 8,
    image_scale: int | None = None,
) -> imagehash.ImageHash:
    """Cross-port-stable variant of ``imagehash.whash(mode='db4')``.

    Identical pipeline to ``imagehash.whash(image, hash_size, mode='db4',
    remove_max_haar_ll=True)`` up to producing the LL band; then applies::

        dwt_low = np.where(np.abs(dwt_low) < WHASH_DB4_ROBUST_EPS, 0.0, dwt_low)

    before computing the median and threshold.

    Effect:

    - For real-world photographs, LL coefficients are O(0.1)–O(10) so none
      get snapped; output is byte-identical to ``imagehash.whash(mode='db4')``.
    - For pathological symmetric inputs (checkerboards, certain high-contrast
      line art), the LL band consists of near-zero float noise (~1e-17) where
      PyWavelets' C+SIMD/FMA inner-loop accumulation can resolve the sign
      differently than portable double arithmetic in the 5 ports. The snap
      step collapses all near-zero coefficients to exactly 0 so the median
      tie-point comparison is deterministic across all 6 implementations
      (Python + the 5 ports).

    **Caveat:** for the pathological inputs the output of this function
    *differs* from ``imagehash.whash(image, mode='db4')``. The trade-off is
    cross-port stability vs. upstream parity. Use this function when stable
    hashing across language implementations matters more than matching
    pre-existing imagehash output.

    Args:
        image: PIL Image.
        hash_size: power-of-2 hash output side. Default 8.
        image_scale: pre-resize scale (power of 2). Default = max image
            natural scale, ``hash_size``.

    Returns:
        ``imagehash.ImageHash`` of shape ``(hash_size, hash_size)``.

    Raises:
        AssertionError: if ``hash_size`` is not a power of 2, or if
            ``image_scale`` is provided and not a power of 2, or if
            ``hash_size`` exceeds the allowed range for the input.
    """
    if image_scale is not None:
        assert image_scale & (image_scale - 1) == 0, "image_scale must be power of 2"
    else:
        image_natural_scale = 2 ** int(np.log2(min(image.size)))
        image_scale = max(image_natural_scale, hash_size)

    ll_max_level = int(np.log2(image_scale))
    level = int(np.log2(hash_size))
    assert hash_size & (hash_size - 1) == 0, "hash_size must be power of 2"
    assert level <= ll_max_level, "hash_size in a wrong range"
    dwt_level = ll_max_level - level

    L = image.convert("L").resize((image_scale, image_scale), imagehash.ANTIALIAS)
    pixels = np.asarray(L) / 255.0

    # remove_max_haar_ll step: hardcoded to Haar even when mode='db4', matching
    # upstream imagehash.whash. See spec/SPEC.md §whash_db4.
    coeffs = pywt.wavedec2(pixels, "haar", level=ll_max_level)
    coeffs = list(coeffs)
    coeffs[0] *= 0
    pixels = pywt.waverec2(coeffs, "haar")

    # db4 final decomposition
    coeffs = pywt.wavedec2(pixels, "db4", level=dwt_level)
    dwt_low = coeffs[0]

    # The only difference from imagehash.whash(mode='db4'): snap near-zero to 0.
    dwt_low = np.where(np.abs(dwt_low) < WHASH_DB4_ROBUST_EPS, 0.0, dwt_low)

    med = np.median(dwt_low)
    diff = dwt_low > med
    return imagehash.ImageHash(diff)
