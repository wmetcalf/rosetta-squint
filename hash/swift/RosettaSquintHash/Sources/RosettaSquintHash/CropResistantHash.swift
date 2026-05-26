import Foundation

/// Crop-resistant hash: segments the image via flood fill on a smoothed grayscale
/// copy, then dhash each segment. Returns an `ImageMultiHash`.
///
/// Matches Python `imagehash.crop_resistant_hash` with default parameters:
///   hash_func=dhash, limit_segments=None, segment_threshold=128,
///   min_segment_size=500, segmentation_image_size=300.
///
/// Parity hazards reproduced:
///   - Gaussian blur: exact Pillow BoxBlur.c 3-pass formula with fractional radius.
///   - Median filter: 3×3 windowed median with edge replication.
///   - Segmentation: row-major BFS, valley loop terminates when
///     len(already_segmented) >= img_width * img_height.
///   - Bounding-box float→int: `Int(d)` (truncate toward zero, matching Python `int()`).
public func cropResistantHash(
    _ image: RGBImage,
    limitSegments: Int? = nil,
    segmentThreshold: Float = 128.0,
    minSegmentSize: Int = 500,
    segmentationImageSize: Int = 300
) throws -> ImageMultiHash {
    try image.validate()
    // Step 1: keep the unmodified original for per-segment cropping.
    let origImage = image

    // Step 2: convert to grayscale, Lanczos resize to 300×300.
    let rgb = ImgRGB.toRGB(origImage)
    let grayFull = rgbToGray(rgb.data, width: rgb.width, height: rgb.height)
    let grayResized = lanczosResize(
        grayFull,
        srcW: rgb.width, srcH: rgb.height,
        dstW: segmentationImageSize, dstH: segmentationImageSize
    )

    // Step 3: PIL GaussianBlur(radius=2) — 3-pass separable box blur.
    let blurred = pilGaussianBlur(
        grayResized,
        width: segmentationImageSize, height: segmentationImageSize
    )

    // Step 4: PIL MedianFilter(size=3) — 3×3 windowed median, edge-replication.
    let filtered = pilMedianFilter(
        blurred,
        width: segmentationImageSize, height: segmentationImageSize
    )

    // Step 5: convert uint8 → float32.
    let pixels = filtered.map { Float($0) }

    // Step 6: segment via flood fill.
    var segments = findAllSegments(
        pixels,
        width: segmentationImageSize,
        height: segmentationImageSize,
        threshold: segmentThreshold,
        minSegmentSize: minSegmentSize
    )

    // Step 7: fallback to whole-image segment if none survive.
    if segments.isEmpty {
        let tl = Pixel(y: 0, x: 0)
        let br = Pixel(y: segmentationImageSize - 1, x: segmentationImageSize - 1)
        segments = [[tl, br]]
    }

    // Step 8: if limit set, keep the M largest segments.
    if let limit = limitSegments {
        segments = Array(segments
            .sorted { $0.count > $1.count }
            .prefix(limit))
    }

    // Step 9: dhash each segment's bounding-box crop from the original image.
    var hashes: [Hash] = []
    let origW = origImage.width
    let origH = origImage.height
    let scaleW = Double(origW) / Double(segmentationImageSize)
    let scaleH = Double(origH) / Double(segmentationImageSize)

    for segment in segments {
        // Compute axis-aligned bounding box (in segmentation coordinates).
        var minY = Int.max, minX = Int.max, maxY = Int.min, maxX = Int.min
        for p in segment {
            if p.y < minY { minY = p.y }
            if p.x < minX { minX = p.x }
            if p.y > maxY { maxY = p.y }
            if p.x > maxX { maxX = p.x }
        }

        // Scale to original image coordinates (float), then round to nearest integer.
        // Matches PIL Image.py `_crop`: `x0, y0, x1, y1 = map(int, map(round, box))`.
        // Pillow 10.4.0 uses round() (not int/truncate) for crop coordinates.
        let cropLeft   = Int((Double(minX)     * scaleW).rounded())
        let cropTop    = Int((Double(minY)     * scaleH).rounded())
        let cropRight  = Int((Double(maxX + 1) * scaleW).rounded())
        let cropBottom = Int((Double(maxY + 1) * scaleH).rounded())

        // Clamp to valid image bounds.
        let left   = max(0, min(origW, cropLeft))
        let top    = max(0, min(origH, cropTop))
        let right  = max(left, min(origW, cropRight))
        let bottom = max(top, min(origH, cropBottom))

        let cropW = right - left
        let cropH = bottom - top

        // Extract the cropped RGB region from the original image.
        let cropImage = cropRGBImage(origImage, left: left, top: top, width: cropW, height: cropH)

        // Compute dhash with default hash_size=8.
        let h = try dhash(cropImage, hashSize: 8)
        hashes.append(h)
    }

    return try ImageMultiHash(segmentHashes: hashes)
}

/// Extracts a rectangular sub-image from `src` without copying channels.
private func cropRGBImage(_ src: RGBImage, left: Int, top: Int, width: Int, height: Int) -> RGBImage {
    let bytesPerPixel = src.channels == .rgb ? 3 : 4
    let srcW = src.width
    var data = [UInt8](repeating: 0, count: width * height * bytesPerPixel)
    for y in 0..<height {
        let srcRow = (top + y) * srcW + left
        let dstRow = y * width
        for x in 0..<width {
            let si = (srcRow + x) * bytesPerPixel
            let di = (dstRow + x) * bytesPerPixel
            for c in 0..<bytesPerPixel {
                data[di + c] = src.data[si + c]
            }
        }
    }
    return RGBImage(width: width, height: height, data: data, channels: src.channels)
}
