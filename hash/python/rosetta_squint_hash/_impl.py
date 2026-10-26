"""Implementation of rosetta_squint_hash extensions and port-local algorithm
overrides.

This module provides:

- ``WHASH_DB4_ROBUST_EPS`` / ``whash_db4_robust``: the original snap-to-zero
  extension on top of ``imagehash.whash(mode='db4')`` for pathological
  symmetric inputs.
- ``SNAP_EPS``: the cross-port snap-to-threshold tie-break constant.
- ``phash`` / ``phash_simple`` / ``whash_db4``: port-local replacements for
  the matching upstream ``imagehash`` functions that apply the snap-to-
  threshold tie-break. ``rosetta_squint_hash`` exports THESE (not the upstream
  ones) so callers get cross-port stable hashes at every supported size.
- ``average_hash`` / ``dhash`` / ``dhash_vertical`` / ``whash`` /
  ``colorhash`` / ``crop_resistant_hash``: port-local versions of the
  matching upstream functions that first composite RGBA → RGB on opaque
  black using the same truncated 8-bit formula
  (``out_c = floor((src_c * alpha) / 255)``) that the 5 native ports use.
  PIL's ``image.convert('L')`` / ``convert('HSV')`` on an RGBA input would
  simply *drop* the alpha channel, leaving the source RGB unchanged — which
  diverges from every native port on any partially-transparent input.

Every Lanczos resize goes through :func:`_lanczos_resize`, which fixes the
pass order to horizontal-then-vertical as ``spec/SPEC.md`` §"Step 4"
requires, instead of leaving it to Pillow's internal heuristic.

See ``__init__.py`` docstring and ``spec/SPEC.md`` §"Threshold tie-break"
for the rationale.
"""

from __future__ import annotations

from collections.abc import Callable
from typing import Any

import numpy as np
import pywt
import scipy.fftpack
import imagehash
from PIL import Image, ImageFilter


def _lanczos_resize(image: Image.Image, size: tuple[int, int]) -> Image.Image:
    """Lanczos resize with the spec's pass order: horizontal, then vertical.

    Each pass rounds to uint8, so pass order affects output. Pillow chooses
    the order internally (12.x: vertical-first above 100:1; 13.x: vertical-
    first whenever height shrinks >2x more than width), so we issue each
    single-axis pass ourselves to match the 5 native ports on every shape.
    """
    w, h = image.size
    tw, th = size
    out = image
    if tw != w:
        out = out.resize((tw, h), Image.Resampling.LANCZOS)
    if th != h:
        out = out.resize((tw, th), Image.Resampling.LANCZOS)
    return out.copy() if out is image else out


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
        luma = (arr[..., 0:1] * alpha) // 255
        rgb = np.repeat(luma, 3, axis=-1)
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
    L = _lanczos_resize(image.convert("L"), (img_size, img_size))
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
    L = _lanczos_resize(image.convert("L"), (img_size, img_size))
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
    L = _lanczos_resize(image.convert("L"), (image_scale, image_scale))
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
    L = _lanczos_resize(image.convert("L"), (image_scale, image_scale))
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


# ─── Port-local versions of the remaining 6 upstream algorithms ──────────────
#
# Each applies :func:`_composite_over_black_truncated` first (PIL's own
# ``convert('L')`` / ``convert('HSV')`` drops alpha instead of compositing) and
# routes every resize through :func:`_lanczos_resize`. Bodies otherwise mirror
# ``imagehash==4.3.2`` line for line; ``colorhash`` has no resize and still
# forwards upstream.


def average_hash(
    image: Image.Image, hash_size: int = 8, mean: Callable[..., Any] = np.mean
) -> imagehash.ImageHash:
    """Port-local ``average_hash``; mirrors ``imagehash.average_hash``."""
    if hash_size < 2:
        raise ValueError("Hash size must be greater than or equal to 2")
    image = _composite_over_black_truncated(image)
    pixels = np.asarray(_lanczos_resize(image.convert("L"), (hash_size, hash_size)))
    return imagehash.ImageHash(pixels > mean(pixels))


def dhash(image: Image.Image, hash_size: int = 8) -> imagehash.ImageHash:
    """Port-local ``dhash``; mirrors ``imagehash.dhash``."""
    if hash_size < 2:
        raise ValueError("Hash size must be greater than or equal to 2")
    image = _composite_over_black_truncated(image)
    pixels = np.asarray(_lanczos_resize(image.convert("L"), (hash_size + 1, hash_size)))
    return imagehash.ImageHash(pixels[:, 1:] > pixels[:, :-1])


def dhash_vertical(image: Image.Image, hash_size: int = 8) -> imagehash.ImageHash:
    """Port-local ``dhash_vertical``; mirrors ``imagehash.dhash_vertical``."""
    image = _composite_over_black_truncated(image)
    pixels = np.asarray(_lanczos_resize(image.convert("L"), (hash_size, hash_size + 1)))
    return imagehash.ImageHash(pixels[1:, :] > pixels[:-1, :])


def whash(
    image: Image.Image,
    hash_size: int = 8,
    image_scale: int | None = None,
    mode: str = "haar",
    remove_max_haar_ll: bool = True,
) -> imagehash.ImageHash:
    """Port-local ``whash``; mirrors ``imagehash.whash``.

    For ``mode='db4'`` prefer :func:`whash_db4`, which also applies the
    snap-to-threshold tie-break.
    """
    if image_scale is not None:
        assert image_scale & (image_scale - 1) == 0, "image_scale is not power of 2"
    else:
        image_natural_scale = 2 ** int(np.log2(min(image.size)))
        image_scale = max(image_natural_scale, hash_size)

    ll_max_level = int(np.log2(image_scale))
    level = int(np.log2(hash_size))
    assert hash_size & (hash_size - 1) == 0, "hash_size is not power of 2"
    assert level <= ll_max_level, "hash_size in a wrong range"
    dwt_level = ll_max_level - level

    image = _composite_over_black_truncated(image)
    pixels = np.asarray(_lanczos_resize(image.convert("L"), (image_scale, image_scale))) / 255.0

    if remove_max_haar_ll:
        coeffs = list(pywt.wavedec2(pixels, "haar", level=ll_max_level))
        coeffs[0] *= 0
        pixels = pywt.waverec2(coeffs, "haar")

    dwt_low = pywt.wavedec2(pixels, mode, level=dwt_level)[0]
    return imagehash.ImageHash(dwt_low > np.median(dwt_low))


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
    hash_func: Callable[[Image.Image], imagehash.ImageHash] | None = None,
    limit_segments: int | None = None,
    segment_threshold: int = 128,
    min_segment_size: int = 500,
    segmentation_image_size: int = 300,
) -> imagehash.ImageMultiHash:
    """Port-local ``crop_resistant_hash``; mirrors ``imagehash.crop_resistant_hash``.

    The default ``hash_func`` is the port-local :func:`dhash`, so per-segment
    hashes also use the fixed resize pass order.
    """
    if hash_func is None:
        hash_func = dhash
    orig_image = _composite_over_black_truncated(image)

    gray = _lanczos_resize(orig_image.convert("L"), (segmentation_image_size, segmentation_image_size))
    gray = gray.filter(ImageFilter.GaussianBlur()).filter(ImageFilter.MedianFilter())
    pixels = np.array(gray).astype(np.float32)

    segments = imagehash._find_all_segments(  # type: ignore[no-untyped-call]
        pixels, segment_threshold, min_segment_size
    )
    if not segments:
        segments.append({(0, 0), (segmentation_image_size - 1, segmentation_image_size - 1)})
    if limit_segments:
        segments = sorted(segments, key=lambda s: len(s), reverse=True)[:limit_segments]

    orig_w, orig_h = orig_image.size
    scale_w = float(orig_w) / segmentation_image_size
    scale_h = float(orig_h) / segmentation_image_size
    hashes = []
    for segment in segments:
        min_y = min(coord[0] for coord in segment) * scale_h
        min_x = min(coord[1] for coord in segment) * scale_w
        max_y = (max(coord[0] for coord in segment) + 1) * scale_h
        max_x = (max(coord[1] for coord in segment) + 1) * scale_w
        hashes.append(hash_func(orig_image.crop((min_x, min_y, max_x, max_y))))
    return imagehash.ImageMultiHash(hashes)
