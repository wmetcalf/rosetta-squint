"""Implementation of rosetta_imagehash extensions and port-local algorithm
overrides.

This module provides:

- ``WHASH_DB4_ROBUST_EPS`` / ``whash_db4_robust``: the original snap-to-zero
  extension on top of ``imagehash.whash(mode='db4')`` for pathological
  symmetric inputs.
- ``SNAP_EPS``: the cross-port snap-to-threshold tie-break constant.
- ``phash`` / ``phash_simple`` / ``whash_db4``: port-local replacements for
  the matching upstream ``imagehash`` functions that apply the snap-to-
  threshold tie-break. ``rosetta_imagehash`` exports THESE (not the upstream
  ones) so callers get cross-port stable hashes at every supported size.
- ``average_hash`` / ``dhash`` / ``dhash_vertical`` / ``whash`` /
  ``colorhash`` / ``crop_resistant_hash``: port-local wrappers around the
  matching upstream functions that first composite RGBA → RGB on opaque
  black using the same truncated 8-bit formula
  (``out_c = floor((src_c * alpha) / 255)``) that the 5 native ports use.
  PIL's ``image.convert('L')`` / ``convert('HSV')`` on an RGBA input would
  simply *drop* the alpha channel, leaving the source RGB unchanged — which
  diverges from every native port on any partially-transparent input.

See ``__init__.py`` docstring and ``spec/SPEC.md`` §"Threshold tie-break"
for the rationale.
"""

from __future__ import annotations

import numpy as np
import pywt
import scipy.fftpack
import imagehash
from PIL import Image


def _composite_over_black_truncated(image: Image.Image) -> Image.Image:
    """Composite an RGBA / LA image onto opaque black using truncated 8-bit
    alpha-premultiplication (``out_c = (src_c * alpha) // 255``).

    This matches the formula every native port uses (Rust ``img_rgb::to_rgb``,
    JS ``toRgb`` with ``Math.trunc((r * a) / 255)``, Swift ``ImgRGB.toRGB``,
    and — after the matching fix — the Go ``imgrgb.ToRGB`` and Java
    ``BufferedImageRgb.convertToIntRgb``).

    Why not PIL's own composite operations? Both ``Image.paste(mask=alpha)``
    and ``Image.alpha_composite`` use round-half-up rounding (i.e.
    ``(src_c * alpha + 127) // 255``). The 1-bit difference on partial-alpha
    pixels cascades through the downstream Lanczos / DCT / wavelet pipelines
    into ±1 LSB hash differences, which the differential fuzzer surfaces as
    cross-port disagreements. The truncated form is therefore the normative
    cross-port formula for this project.

    Non-RGBA / non-LA inputs are returned in ``RGB`` mode via the standard
    PIL ``convert('RGB')`` (which is a no-op fast path for already-RGB
    images), so callers can immediately apply ``convert('L')`` /
    ``convert('HSV')`` / etc. without further mode checks.

    Args:
        image: PIL Image in any mode.

    Returns:
        PIL Image in mode ``RGB``, with any partial alpha already
        composited against opaque black. Caller is free to mutate (a new
        image is returned).
    """
    mode = image.mode
    if mode == "RGBA":
        arr = np.asarray(image, dtype=np.uint16)
        alpha = arr[..., 3:4]
        rgb = (arr[..., :3] * alpha) // 255
        return Image.fromarray(rgb.astype(np.uint8), "RGB")
    if mode == "LA":
        # Grayscale-with-alpha — same truncated multiply, then duplicate L
        # into all three RGB channels so downstream ``convert('L')`` /
        # ``convert('HSV')`` behave identically to native ports (which only
        # ever see RGB inputs).
        arr = np.asarray(image, dtype=np.uint16)
        alpha = arr[..., 1:2]
        l = (arr[..., 0:1] * alpha) // 255
        rgb = np.repeat(l, 3, axis=-1)
        return Image.fromarray(rgb.astype(np.uint8), "RGB")
    if mode == "RGB":
        return image
    # Palette ("P") with transparency, "1", "L", "I", "F", "CMYK", "YCbCr",
    # etc. — let PIL handle the convert; alpha (if any embedded in a "P"
    # palette index) is not composited because non-RGBA / non-LA inputs don't
    # carry a separate alpha plane this composite step is responsible for.
    # Native ports' decoders also expand "P" without alpha here.
    return image.convert("RGB")

WHASH_DB4_ROBUST_EPS: float = 1e-12
"""ε threshold for snap-to-zero. Coefficients with ``|c| < EPS`` are snapped
to exactly 0 before median + threshold, eliminating ULP-level tie-point
ambiguity on pathological symmetric inputs.

Fixed across all 6 ports — do not vary per-call without coordinating a spec
change."""

SNAP_EPS: float = 1e-10
"""ε threshold for the snap-to-threshold tie-break. Used by ``phash``,
``phash_simple``, ``whash_db4``, and ``whash_db4_robust`` to deterministically
map near-threshold coefficients to bit 0 (``bit = v > threshold + SNAP_EPS``).

Sized to comfortably exceed cross-port FP accumulation noise (~1e-12 for DCT
on uint8 inputs, ~1e-15 for db4 wavedec2 on uint8/255 inputs) while staying
far below any meaningful signal. See ``spec/SPEC.md`` §"Threshold tie-break"."""


def phash(image: Image.Image, hash_size: int = 8, highfreq_factor: int = 4) -> imagehash.ImageHash:
    """Port-local ``phash`` with snap-to-threshold tie-break and cross-port
    alpha compositing.

    Identical to ``imagehash.phash`` except:
    - any RGBA / LA input is first composited against opaque black via the
      truncated 8-bit formula (see :func:`_composite_over_black_truncated`),
      matching all 5 native ports rather than PIL's default alpha-drop;
    - the per-bit comparison is ``v > median + SNAP_EPS`` (deterministic
      bit 0 on ties) instead of strict ``v > median``. This eliminates
      cross-port FP-noise divergence at large hash sizes (32, 64) where
      many DCT coefficients land within one ULP of the median.
    """
    if hash_size < 2:
        raise ValueError("Hash size must be greater than or equal to 2")
    image = _composite_over_black_truncated(image)
    img_size = hash_size * highfreq_factor
    L = image.convert("L").resize((img_size, img_size), imagehash.ANTIALIAS)
    pixels = np.asarray(L, dtype=np.float64)
    dct = scipy.fftpack.dct(scipy.fftpack.dct(pixels, axis=0), axis=1)
    block = dct[:hash_size, :hash_size]
    med = np.median(block)
    diff = block > med + SNAP_EPS
    return imagehash.ImageHash(diff)


def phash_simple(image: Image.Image, hash_size: int = 8, highfreq_factor: int = 4) -> imagehash.ImageHash:
    """Port-local ``phash_simple`` with snap-to-threshold tie-break and
    cross-port alpha compositing. See :func:`phash` for the rationale of
    both deviations from upstream.
    """
    if hash_size < 2:
        raise ValueError("Hash size must be greater than or equal to 2")
    image = _composite_over_black_truncated(image)
    img_size = hash_size * highfreq_factor
    L = image.convert("L").resize((img_size, img_size), imagehash.ANTIALIAS)
    pixels = np.asarray(L, dtype=np.float64)
    dct = scipy.fftpack.dct(pixels)
    dctlowfreq = dct[:hash_size, 1:hash_size + 1]
    mean = dctlowfreq.mean()
    diff = dctlowfreq > mean + SNAP_EPS
    return imagehash.ImageHash(diff)


def whash_db4(
    image: Image.Image,
    hash_size: int = 8,
    image_scale: int | None = None,
) -> imagehash.ImageHash:
    """Port-local ``whash(mode='db4')`` with snap-to-threshold tie-break and
    cross-port alpha compositing.

    Pipeline matches ``imagehash.whash(image, hash_size, mode='db4',
    remove_max_haar_ll=True)`` exactly, except (a) any RGBA / LA input is
    first composited against opaque black via the truncated 8-bit formula
    (see :func:`_composite_over_black_truncated`) and (b) the per-bit
    comparison is ``v > median + SNAP_EPS`` instead of strict
    ``v > median``. See :func:`phash` for the rationale.
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

    image = _composite_over_black_truncated(image)
    L = image.convert("L").resize((image_scale, image_scale), imagehash.ANTIALIAS)
    pixels = np.asarray(L) / 255.0

    coeffs = pywt.wavedec2(pixels, "haar", level=ll_max_level)
    coeffs = list(coeffs)
    coeffs[0] *= 0
    pixels = pywt.waverec2(coeffs, "haar")

    coeffs = pywt.wavedec2(pixels, "db4", level=dwt_level)
    dwt_low = coeffs[0]

    med = np.median(dwt_low)
    diff = dwt_low > med + SNAP_EPS
    return imagehash.ImageHash(diff)


def whash_db4_robust(
    image: Image.Image,
    hash_size: int = 8,
    image_scale: int | None = None,
) -> imagehash.ImageHash:
    """Cross-port-stable variant of ``imagehash.whash(mode='db4')``.

    Identical pipeline to ``imagehash.whash(image, hash_size, mode='db4',
    remove_max_haar_ll=True)`` up to producing the LL band; then applies
    BOTH stabilization steps::

        dwt_low = np.where(np.abs(dwt_low) < WHASH_DB4_ROBUST_EPS, 0.0, dwt_low)
        ...
        diff = dwt_low > median + SNAP_EPS

    The first step (snap-to-zero) handles pathological symmetric inputs whose
    LL band is mathematically 0 + ULP-level noise; the second (snap-to-
    threshold) handles natural images whose LL band has many coefficients
    clustered near the median.

    Effect:

    - Real-world photographs: LL coefficients are O(0.1)–O(10) so neither snap
      kicks in for non-tie values; output equals :func:`whash_db4` (modulo
      bit assignments on ties).
    - Pathological symmetric inputs: both snaps collapse near-zero / near-
      median coefficients to a deterministic bit 0, so all 6 ports produce
      identical hashes.

    **Caveat:** for pathological inputs the output differs from upstream
    ``imagehash.whash(mode='db4')``. Trade-off: cross-port stability vs.
    upstream parity. Use this when stable hashing across language
    implementations matters more than matching pre-existing imagehash output.

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

    image = _composite_over_black_truncated(image)
    L = image.convert("L").resize((image_scale, image_scale), imagehash.ANTIALIAS)
    pixels = np.asarray(L) / 255.0

    coeffs = pywt.wavedec2(pixels, "haar", level=ll_max_level)
    coeffs = list(coeffs)
    coeffs[0] *= 0
    pixels = pywt.waverec2(coeffs, "haar")

    coeffs = pywt.wavedec2(pixels, "db4", level=dwt_level)
    dwt_low = coeffs[0]

    # First stabilization: snap |c| < WHASH_DB4_ROBUST_EPS to exactly 0.
    dwt_low = np.where(np.abs(dwt_low) < WHASH_DB4_ROBUST_EPS, 0.0, dwt_low)

    med = np.median(dwt_low)
    # Second stabilization: snap coefficients within SNAP_EPS of the median
    # to bit 0 via `v > median + SNAP_EPS`.
    diff = dwt_low > med + SNAP_EPS
    return imagehash.ImageHash(diff)


# ─── RGBA-composite overrides for the remaining 6 upstream algorithms ────────
#
# These wrappers exist solely to apply :func:`_composite_over_black_truncated`
# before forwarding into the upstream ``imagehash`` implementation. The
# upstream functions otherwise call ``image.convert('L')`` (or
# ``convert('HSV')``) directly on RGBA inputs, which would strip the alpha
# channel without compositing. The 5 native ports all composite — see the
# spec / triage report for the cross-port disagreement that motivated this.
#
# We DO forward to the upstream function (rather than re-implementing each
# pipeline) so any future upstream bugfix in the body of e.g. ``dhash``
# remains live. Only the alpha-handling preamble is overridden.


def average_hash(image: Image.Image, hash_size: int = 8, mean=np.mean) -> imagehash.ImageHash:
    """Port-local ``average_hash`` with cross-port alpha compositing.

    Identical to ``imagehash.average_hash`` except RGBA / LA inputs are
    composited against opaque black using the truncated 8-bit formula
    before the upstream algorithm runs. See
    :func:`_composite_over_black_truncated`.
    """
    return imagehash.average_hash(
        _composite_over_black_truncated(image), hash_size=hash_size, mean=mean
    )


def dhash(image: Image.Image, hash_size: int = 8) -> imagehash.ImageHash:
    """Port-local ``dhash`` with cross-port alpha compositing.

    See :func:`average_hash` for the rationale.
    """
    return imagehash.dhash(
        _composite_over_black_truncated(image), hash_size=hash_size
    )


def dhash_vertical(image: Image.Image, hash_size: int = 8) -> imagehash.ImageHash:
    """Port-local ``dhash_vertical`` with cross-port alpha compositing.

    See :func:`average_hash` for the rationale.
    """
    return imagehash.dhash_vertical(
        _composite_over_black_truncated(image), hash_size=hash_size
    )


def whash(
    image: Image.Image,
    hash_size: int = 8,
    image_scale: int | None = None,
    mode: str = "haar",
    remove_max_haar_ll: bool = True,
) -> imagehash.ImageHash:
    """Port-local ``whash`` with cross-port alpha compositing.

    Identical to ``imagehash.whash`` except RGBA / LA inputs are composited
    against opaque black using the truncated 8-bit formula before the
    upstream algorithm runs. Callers using ``mode='db4'`` should prefer
    :func:`whash_db4` (which additionally applies the snap-to-threshold
    tie-break) for cross-port stable hashes; this :func:`whash` wrapper is
    here so ``whash(mode='haar')`` callers also get correct RGBA semantics
    without needing to know the underlying machinery.
    """
    return imagehash.whash(
        _composite_over_black_truncated(image),
        hash_size=hash_size,
        image_scale=image_scale,
        mode=mode,
        remove_max_haar_ll=remove_max_haar_ll,
    )


def colorhash(image: Image.Image, binbits: int = 3) -> imagehash.ImageHash:
    """Port-local ``colorhash`` with cross-port alpha compositing.

    ``imagehash.colorhash`` internally calls both ``image.convert('L')`` and
    ``image.convert('HSV')`` on the input; PIL's RGBA→HSV conversion likewise
    strips alpha rather than compositing. We composite first so the HSV
    histogram is computed from the same RGB pixels the 5 native ports use.
    """
    return imagehash.colorhash(
        _composite_over_black_truncated(image), binbits=binbits
    )


def crop_resistant_hash(
    image: Image.Image,
    hash_func=None,
    limit_segments: int | None = None,
    segment_threshold: int = 128,
    min_segment_size: int = 500,
    segmentation_image_size: int = 300,
) -> imagehash.ImageMultiHash:
    """Port-local ``crop_resistant_hash`` with cross-port alpha compositing.

    Composites the input once at the top of the pipeline so the
    segmentation pass *and* each per-segment hash both see RGB pixels with
    alpha already premultiplied against black. The default ``hash_func`` is
    :func:`dhash` (the port-local snap/composite-applying wrapper, not
    upstream's ``imagehash.dhash``) so per-segment hashes also see the
    cross-port semantics — but on the composited RGB input that's already a
    no-op for the alpha path, so the wrapper is only providing the
    composite preamble belt-and-braces.
    """
    composited = _composite_over_black_truncated(image)
    if hash_func is None:
        hash_func = dhash
    return imagehash.crop_resistant_hash(
        composited,
        hash_func=hash_func,
        limit_segments=limit_segments,
        segment_threshold=segment_threshold,
        min_segment_size=min_segment_size,
        segmentation_image_size=segmentation_image_size,
    )
