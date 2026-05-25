import Foundation

/// Finds all bright ("hill") and dark ("valley") connected segments in a float32 pixel
/// array. Matches Python `imagehash._find_all_segments`.
///
/// Connectivity: 4-neighbor.
/// Iteration order: row-major (y then x), matching `np.argwhere` / `np.nonzero` order.
///
/// The valley loop uses `len(already_segmented) < img_width * img_height` as its
/// termination condition — mirroring the Python source exactly. Note that
/// `img_width` and `img_height` in the Python source map to `pixels.shape[0]` (H)
/// and `pixels.shape[1]` (W) respectively, so `img_width * img_height = H * W`.
///
/// Parameters:
/// - pixels: Float32 array in row-major order (H × W).
/// - width:  W (number of columns).
/// - height: H (number of rows).
/// - threshold: pixels > threshold are "hills". Default 128.
/// - minSegmentSize: minimum pixel count for a surviving segment. Default 500.
///
/// Returns: list of segments; each segment is a list of (y, x) coordinates sorted
/// in row-major order (matching argwhere order).

struct Pixel: Hashable {
    let y: Int
    let x: Int
}

func findAllSegments(
    _ pixels: [Float],
    width: Int,
    height: Int,
    threshold: Float = 128.0,
    minSegmentSize: Int = 500
) -> [[Pixel]] {
    let imgWidth = height  // pixels.shape[0] in Python = H
    let imgHeight = width  // pixels.shape[1] in Python = W

    // Precompute threshold booleans
    var threshPixels = [Bool](repeating: false, count: height * width)
    for i in 0..<(height * width) {
        threshPixels[i] = pixels[i] > threshold
    }

    var unassigned = [Bool](repeating: true, count: height * width)

    var segments: [[Pixel]] = []

    // Already-segmented set: uses a flat representation for image pixels,
    // plus a separate set for the border pseudo-pixels.
    // We need to track border pixels too since the Python code uses a single set.
    // Border pixels: (-1, z), (H, z), (z, -1), (z, W) — encode as special values.
    // For efficiency, track image pixels in a flat Bool array + border count separately.
    var assignedInImage = [Bool](repeating: false, count: height * width)

    // Count of border pixels: 2*H + 2*W (all unique since corners differ)
    let borderCount = 2 * imgWidth + 2 * imgHeight

    // Assigned count starts with all border pixels
    var assignedCount = borderCount

    func markAssigned(_ y: Int, _ x: Int) {
        let idx = y * width + x
        if !assignedInImage[idx] {
            assignedInImage[idx] = true
            assignedCount += 1
        }
    }

    func isAssigned(_ y: Int, _ x: Int) -> Bool {
        // Out-of-bounds pixels are always "assigned" (border)
        if y < 0 || y >= height || x < 0 || x >= width { return true }
        return assignedInImage[y * width + x]
    }

    // Python's `_find_region` uses two sets — `in_region` and `not_in_region`.
    // `not_in_region` pixels are skipped in future iterations but NOT marked
    // assigned. `findRegionExact` reproduces this exactly.
    func findRegionExact(mask: [Bool]) -> [Pixel] {
        var startY = -1, startX = -1
        outer: for y in 0..<height {
            for x in 0..<width {
                if mask[y * width + x] {
                    startY = y; startX = x
                    break outer
                }
            }
        }
        guard startY >= 0 else { return [] }

        var inRegion = Set<Pixel>()
        var notInRegion = Set<Pixel>()
        var newPixels = Set<Pixel>()

        let start = Pixel(y: startY, x: startX)
        inRegion.insert(start)
        newPixels.insert(start)
        markAssigned(startY, startX)

        while !newPixels.isEmpty {
            var tryNext = Set<Pixel>()
            for p in newPixels {
                for (ny, nx) in [(p.y-1,p.x),(p.y+1,p.x),(p.y,p.x-1),(p.y,p.x+1)] {
                    tryNext.insert(Pixel(y: ny, x: nx))
                }
            }
            // Remove already-segmented (border or image-assigned) and not-in-region
            tryNext = tryNext.filter { p in
                !isAssigned(p.y, p.x) && !notInRegion.contains(p)
            }

            if tryNext.isEmpty { break }

            newPixels = Set<Pixel>()
            for p in tryNext {
                // In-bounds check
                if p.y < 0 || p.y >= height || p.x < 0 || p.x >= width {
                    notInRegion.insert(p)
                    continue
                }
                if mask[p.y * width + p.x] {
                    inRegion.insert(p)
                    newPixels.insert(p)
                    markAssigned(p.y, p.x)
                } else {
                    notInRegion.insert(p)
                }
            }
        }

        // Mark unassigned for pixels in region
        for p in inRegion {
            unassigned[p.y * width + p.x] = false
        }

        return Array(inRegion)
    }

    // ----- Hills -----
    func hillMask() -> [Bool]? {
        var mask = [Bool](repeating: false, count: height * width)
        var any = false
        for y in 0..<height {
            for x in 0..<width {
                let idx = y * width + x
                if threshPixels[idx] && unassigned[idx] {
                    mask[idx] = true
                    any = true
                }
            }
        }
        return any ? mask : nil
    }

    while let mask = hillMask() {
        let seg = findRegionExact(mask: mask)
        if seg.count > minSegmentSize {
            segments.append(seg.sorted { $0.y < $1.y || ($0.y == $1.y && $0.x < $1.x) })
        }
    }

    // ----- Valleys -----
    while assignedCount < imgWidth * imgHeight {
        // Build valley mask: !threshold && unassigned
        var mask = [Bool](repeating: false, count: height * width)
        for y in 0..<height {
            for x in 0..<width {
                let idx = y * width + x
                if !threshPixels[idx] && unassigned[idx] {
                    mask[idx] = true
                }
            }
        }
        let seg = findRegionExact(mask: mask)
        if seg.count > minSegmentSize {
            segments.append(seg.sorted { $0.y < $1.y || ($0.y == $1.y && $0.x < $1.x) })
        }
    }

    return segments
}
