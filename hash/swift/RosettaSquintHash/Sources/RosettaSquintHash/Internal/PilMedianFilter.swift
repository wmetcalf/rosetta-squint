import Foundation

/// PIL-compatible MedianFilter(size=3) on UInt8 grayscale (row-major).
///
/// 3×3 windowed median with edge-replication (clamp) boundary.
/// For each pixel, gathers the 9 values in the window (clamping out-of-bounds
/// coordinates to the nearest valid pixel), sorts them, and returns index 4.
/// This matches Pillow's `ImageFilter.MedianFilter()` with size=3.

func pilMedianFilter(_ src: [UInt8], width: Int, height: Int) -> [UInt8] {
    var result = [UInt8](repeating: 0, count: width * height)

    for y in 0..<height {
        for x in 0..<width {
            var window = [UInt8](repeating: 0, count: 9)
            var idx = 0
            for dy in -1...1 {
                for dx in -1...1 {
                    let yy = max(0, min(height - 1, y + dy))
                    let xx = max(0, min(width  - 1, x + dx))
                    window[idx] = src[yy * width + xx]
                    idx += 1
                }
            }
            // Partial insertion sort to find median (index 4 of 9)
            window.sort()
            result[y * width + x] = window[4]
        }
    }

    return result
}
