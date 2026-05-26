import Foundation

/// PIL-compatible GaussianBlur(radius=2) on UInt8 grayscale (row-major).
///
/// Pillow approximates a Gaussian via 3 separable box filters:
///   - n horizontal passes on the image
///   - n vertical passes (transpose → n horizontal passes → transpose back)
/// The box radius is derived from sigma via the Gwosdek et al. formula
/// (src/libImaging/BoxBlur.c in Pillow 10.4.0).
///
/// Boundary: edge replication (clamp to nearest valid pixel).
/// Intermediate and output pixels are rounded and clamped to [0, 255] as uint8
/// after every horizontal sweep pass.

func pilGaussianBlur(_ src: [UInt8], width: Int, height: Int, sigma: Double = 2.0, passes: Int = 3) -> [UInt8] {
    let floatRadius = gaussianBlurRadius(sigma: sigma, passes: passes)
    var current = src

    // n horizontal passes
    for _ in 0..<passes {
        var next = [UInt8](repeating: 0, count: width * height)
        for y in 0..<height {
            let rowIn  = Array(current[(y * width)..<(y * width + width)])
            let rowOut = horizontalBoxBlur(rowIn, floatRadius: floatRadius)
            for x in 0..<width {
                next[y * width + x] = rowOut[x]
            }
        }
        current = next
    }

    // n vertical passes: transpose → n horizontal passes → transpose back
    // Transposed: shape (width, height) stored row-major → transposed[x * height + y]
    var transposed = [UInt8](repeating: 0, count: width * height)
    for y in 0..<height {
        for x in 0..<width {
            transposed[x * height + y] = current[y * width + x]
        }
    }

    for _ in 0..<passes {
        var next = [UInt8](repeating: 0, count: width * height)
        // Each "row" in transposed has length = height (original height)
        for x in 0..<width {
            let rowIn  = Array(transposed[(x * height)..<(x * height + height)])
            let rowOut = horizontalBoxBlur(rowIn, floatRadius: floatRadius)
            for y in 0..<height {
                next[x * height + y] = rowOut[y]
            }
        }
        transposed = next
    }

    // Transpose back
    var result = [UInt8](repeating: 0, count: width * height)
    for y in 0..<height {
        for x in 0..<width {
            result[y * width + x] = transposed[x * height + y]
        }
    }
    return result
}

/// Converts GaussianBlur sigma + number of passes to the box filter fractional radius.
/// Implements `_gaussian_blur_radius` from Pillow's BoxBlur.c.
private func gaussianBlurRadius(sigma: Double, passes: Int) -> Double {
    let sigma2 = sigma * sigma / Double(passes)
    let L = (12.0 * sigma2 + 1.0).squareRoot()
    let l = (L - 1.0) / 2.0          // floor applied below
    let lFloor = floor(l)
    let aNum = (2.0 * lFloor + 1.0) * (lFloor * (lFloor + 1.0) - 3.0 * sigma2)
    let aDen = 6.0 * (sigma2 - (lFloor + 1.0) * (lFloor + 1.0))
    let a = aNum / aDen
    return lFloor + a
}

/// One horizontal box-blur pass over a single grayscale scanline.
/// Implements `ImagingHorizontalBoxBlur` (8-bit path) from Pillow's BoxBlur.c.
///
/// Fixed-point weights (matching the C source exactly):
///   ww = UInt32((1 << 24) / (floatRadius * 2 + 1))   // float division
///   fw = ((1 << 24) - (radius * 2 + 1) * ww) / 2      // integer division
///
/// Output per pixel: (bulk + (1 << 23)) >> 24  clamped to [0, 255].
private func horizontalBoxBlur(_ line: [UInt8], floatRadius: Double) -> [UInt8] {
    let n = line.count
    guard n > 0 else { return [] }
    let lastX = n - 1
    let radius = Int(floatRadius)  // floor / C cast-to-int

    // Fixed-point weights (UInt32 in C; use Int here to avoid overflow in acc * ww)
    let divisor = floatRadius * 2.0 + 1.0
    let ww = UInt32((Double(1 << 24)) / divisor)
    let innerPart = (1 << 24) - (radius * 2 + 1) * Int(ww)
    let fw = UInt32(innerPart / 2)

    let edgeA = min(radius + 1, n)
    let edgeB = max(n - radius - 1, 0)

    var lineOut = [UInt8](repeating: 0, count: n)

    // Initialize accumulator: pixels from -(radius+1) to +(radius-1) around x=-1.
    // Left clamped pixels (from -radius-1 to -1): all use line[0], count = radius+1
    // Right actual pixels (from 0 to edgeA-2): line[0..edgeA-2]
    // Remaining right clamped pixels (from edgeA-1 to radius-1 if edgeA <= radius):
    //   use line[lastX], count = radius - edgeA + 1
    var acc = UInt64(line[0]) * UInt64(radius + 1)
    for x in 0..<(edgeA - 1) {
        acc += UInt64(line[x])
    }
    acc += UInt64(line[lastX]) * UInt64(radius - edgeA + 1)

    func addFarSave(accVal: UInt64, left: Int, right: Int) -> UInt8 {
        let lc = max(0, min(lastX, left))
        let rc = max(0, min(lastX, right))
        let bulk = accVal &* UInt64(ww) &+ (UInt64(line[lc]) + UInt64(line[rc])) &* UInt64(fw)
        let shifted = Int64(bitPattern: (bulk &+ (1 << 23)) >> 24)
        if shifted < 0 { return 0 }
        if shifted > 255 { return 255 }
        return UInt8(shifted)
    }

    if edgeA <= edgeB {
        for x in 0..<edgeA {
            acc = acc &+ UInt64(line[min(x + radius, lastX)]) &- UInt64(line[0])
            lineOut[x] = addFarSave(accVal: acc, left: 0, right: x + radius + 1)
        }
        for x in edgeA..<edgeB {
            acc = acc &+ UInt64(line[x + radius]) &- UInt64(line[x - radius - 1])
            lineOut[x] = addFarSave(accVal: acc, left: x - radius - 1, right: x + radius + 1)
        }
        for x in edgeB..<n {
            acc = acc &+ UInt64(line[lastX]) &- UInt64(line[x - radius - 1])
            lineOut[x] = addFarSave(accVal: acc, left: x - radius - 1, right: lastX)
        }
    } else {
        for x in 0..<edgeB {
            acc = acc &+ UInt64(line[min(x + radius, lastX)]) &- UInt64(line[0])
            lineOut[x] = addFarSave(accVal: acc, left: 0, right: x + radius + 1)
        }
        for x in edgeB..<edgeA {
            acc = acc &+ UInt64(line[lastX]) &- UInt64(line[0])
            lineOut[x] = addFarSave(accVal: acc, left: 0, right: lastX)
        }
        for x in edgeA..<n {
            acc = acc &+ UInt64(line[lastX]) &- UInt64(line[x - radius - 1])
            lineOut[x] = addFarSave(accVal: acc, left: x - radius - 1, right: lastX)
        }
    }

    return lineOut
}
