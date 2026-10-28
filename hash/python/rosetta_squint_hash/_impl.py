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
  ``colorhash`` / ``crop_resistant_hash``: port-local replacements for the
  matching upstream functions that first composite RGBA → RGB on opaque
  black using the same truncated 8-bit formula
  (``out_c = floor((src_c * alpha) / 255)``) that the 5 native ports use.
  PIL's ``image.convert('L')`` / ``convert('HSV')`` on an RGBA input would
  simply *drop* the alpha channel, leaving the source RGB unchanged — which
  diverges from every native port on any partially-transparent input.
- ``_spec_resize``: the one Lanczos resize every algorithm above goes
  through. It pins the separable pass order (horizontal, then vertical)
  that ``spec/SPEC.md`` §Step 4 defines, independent of which order the
  installed Pillow happens to use (every Pillow picks the order per call).

See ``__init__.py`` docstring and ``spec/SPEC.md`` §"Threshold tie-break"
for the rationale.
"""

from __future__ import annotations

import numpy as np
import pywt
import scipy.fftpack
import imagehash
from PIL import Image, ImageFilter


def _spec_resize(image: Image.Image, size: tuple[int, int]) -> Image.Image:
    """Lanczos resize in the spec's pass order: horizontal, then vertical.

    ``spec/SPEC.md`` §Step 4 defines the resize as a separable Lanczos3
    filter run as a horizontal pass over the source rows followed by a
    vertical pass over that result, each pass rounding to uint8. Every
    native port implements exactly that, unconditionally.

    Pillow does not. Because each pass rounds to 8 bits, running the
    vertical pass first changes the output, and Pillow does so in two
    cases:

    - Pillow ≤ 12.3's ``Image.resize`` (Python level): when the source is
      more than 100x taller than wide and the height shrinks. Releases up
      to 1.0.1 called ``Image.resize`` directly and so disagreed with every
      other port on such inputs.
    - Pillow 13.0's ``ImagingResampleInner`` (``src/libImaging/Resample.c``):
      when the height shrinks by more than twice as much as the width,
      i.e. inputs taller than roughly 2:1.

    The per-axis arithmetic is the same in both versions, so issuing the
    two passes as two explicit ``resize`` calls gives the spec order, and
    the native ports' output, on any Pillow version. It matches Pillow
    ≤ 12.3's single-call output byte-for-byte below 100:1.

    Note that the second call never re-runs the horizontal pass: Pillow's
    ``resize`` is a no-op copy along an axis whose size does not change.

    Args:
        image: ``'L'``-mode PIL Image (callers convert first).
        size: ``(width, height)`` target, as for ``Image.resize``.

    Returns:
        A new ``'L'``-mode image of ``size``.
    """
    width, height = size
    horizontal = image.resize((width, image.size[1]), Image.Resampling.LANCZOS)
    return horizontal.resize((width, height), Image.Resampling.LANCZOS)


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
    L = _spec_resize(image.convert("L"), (img_size, img_size))
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
    L = _spec_resize(image.convert("L"), (img_size, img_size))
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
    L = _spec_resize(image.convert("L"), (image_scale, image_scale))
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
    L = _spec_resize(image.convert("L"), (image_scale, image_scale))
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


# ─── Port-local versions of the remaining 6 upstream algorithms ─────────────
#
# Each of these applies :func:`_composite_over_black_truncated` first. The
# upstream functions otherwise call ``image.convert('L')`` (or
# ``convert('HSV')``) directly on RGBA inputs, which would strip the alpha
# channel without compositing. The 5 native ports all composite — see the
# spec / triage report for the cross-port disagreement that motivated this.
#
# The five that resize (``average_hash``, ``dhash``, ``dhash_vertical``,
# ``whash``, ``crop_resistant_hash``) carry their own copy of the upstream
# ``imagehash`` 4.3.2 body so the resize can go through :func:`_spec_resize`
# — upstream calls ``Image.resize`` directly, whose pass order depends on
# the input's aspect ratio and the Pillow version. The bodies are otherwise verbatim
# (``imagehash`` is BSD-2-Clause). ``colorhash`` never resizes and still
# forwards to upstream.


def average_hash(image: Image.Image, hash_size: int = 8, mean=np.mean) -> imagehash.ImageHash:
    """Port-local ``average_hash`` with cross-port alpha compositing.

    Identical to ``imagehash.average_hash`` except RGBA / LA inputs are
    composited against opaque black using the truncated 8-bit formula
    (see :func:`_composite_over_black_truncated`) and the resize goes
    through :func:`_spec_resize`.
    """
    if hash_size < 2:
        raise ValueError("Hash size must be greater than or equal to 2")
    image = _composite_over_black_truncated(image)
    L = _spec_resize(image.convert("L"), (hash_size, hash_size))
    pixels = np.asarray(L)
    avg = mean(pixels)
    diff = pixels > avg
    return imagehash.ImageHash(diff)


def dhash(image: Image.Image, hash_size: int = 8) -> imagehash.ImageHash:
    """Port-local ``dhash`` with cross-port alpha compositing.

    See :func:`average_hash` for the rationale.
    """
    if hash_size < 2:
        raise ValueError("Hash size must be greater than or equal to 2")
    image = _composite_over_black_truncated(image)
    # resize(w, h), but numpy.array((h, w))
    L = _spec_resize(image.convert("L"), (hash_size + 1, hash_size))
    pixels = np.asarray(L)
    # compute differences between columns
    diff = pixels[:, 1:] > pixels[:, :-1]
    return imagehash.ImageHash(diff)


def dhash_vertical(image: Image.Image, hash_size: int = 8) -> imagehash.ImageHash:
    """Port-local ``dhash_vertical`` with cross-port alpha compositing.

    See :func:`average_hash` for the rationale.
    """
    image = _composite_over_black_truncated(image)
    # resize(w, h), but numpy.array((h, w))
    L = _spec_resize(image.convert("L"), (hash_size, hash_size + 1))
    pixels = np.asarray(L)
    # compute differences between rows
    diff = pixels[1:, :] > pixels[:-1, :]
    return imagehash.ImageHash(diff)


def whash(
    image: Image.Image,
    hash_size: int = 8,
    image_scale: int | None = None,
    mode: str = "haar",
    remove_max_haar_ll: bool = True,
) -> imagehash.ImageHash:
    """Port-local ``whash`` with cross-port alpha compositing.

    Identical to ``imagehash.whash`` except RGBA / LA inputs are composited
    against opaque black using the truncated 8-bit formula and the resize
    goes through :func:`_spec_resize`. Callers using ``mode='db4'`` should
    prefer :func:`whash_db4` (which additionally applies the snap-to-
    threshold tie-break) for cross-port stable hashes; this :func:`whash`
    is here so ``whash(mode='haar')`` callers also get correct RGBA
    semantics without needing to know the underlying machinery.
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
    L = _spec_resize(image.convert("L"), (image_scale, image_scale))
    pixels = np.asarray(L) / 255.0

    # Remove low level frequency LL(max_ll) if remove_max_haar_ll using haar filter
    if remove_max_haar_ll:
        coeffs = pywt.wavedec2(pixels, "haar", level=ll_max_level)
        coeffs = list(coeffs)
        coeffs[0] *= 0
        pixels = pywt.waverec2(coeffs, "haar")

    # Use LL(K) as freq, where K is log2(hash_size)
    coeffs = pywt.wavedec2(pixels, mode, level=dwt_level)
    dwt_low = coeffs[0]

    med = np.median(dwt_low)
    diff = dwt_low > med
    return imagehash.ImageHash(diff)


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
    :func:`dhash` (the port-local composite-applying version, not
    upstream's ``imagehash.dhash``) so per-segment hashes also resize in
    spec order — on the composited RGB input the alpha path is already a
    no-op, so there the wrapper is only belt-and-braces.

    The segmentation resize to ``segmentation_image_size`` square also goes
    through :func:`_spec_resize`; the segmentation itself is upstream's
    ``_find_all_segments`` (pure numpy).
    """
    composited = _composite_over_black_truncated(image)
    if hash_func is None:
        hash_func = dhash

    orig_image = composited.copy()
    # Convert to gray scale and resize
    seg_image = _spec_resize(
        composited.convert("L"), (segmentation_image_size, segmentation_image_size)
    )
    # Add filters
    seg_image = seg_image.filter(ImageFilter.GaussianBlur()).filter(ImageFilter.MedianFilter())
    pixels = np.array(seg_image).astype(np.float32)
    segments = imagehash._find_all_segments(pixels, segment_threshold, min_segment_size)

    # If there are no segments, have 1 segment including the whole image
    if not segments:
        full_image_segment = {(0, 0), (segmentation_image_size - 1, segmentation_image_size - 1)}
        segments.append(full_image_segment)

    # If segment limit is set, discard the smaller segments
    if limit_segments:
        segments = sorted(segments, key=lambda s: len(s), reverse=True)[:limit_segments]

    # Create bounding box for each segment
    hashes = []
    for segment in segments:
        orig_w, orig_h = orig_image.size
        scale_w = float(orig_w) / segmentation_image_size
        scale_h = float(orig_h) / segmentation_image_size
        min_y = min(coord[0] for coord in segment) * scale_h
        min_x = min(coord[1] for coord in segment) * scale_w
        max_y = (max(coord[0] for coord in segment) + 1) * scale_h
        max_x = (max(coord[1] for coord in segment) + 1) * scale_w
        # Compute robust hash for each bounding box
        bounding_box = orig_image.crop((min_x, min_y, max_x, max_y))
        hashes.append(hash_func(bounding_box))
    return imagehash.ImageMultiHash(hashes)
