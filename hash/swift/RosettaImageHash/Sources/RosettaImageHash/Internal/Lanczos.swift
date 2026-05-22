import Foundation

/// Pillow-compatible Lanczos3 resize on UInt8 grayscale (row-major).
///
/// Reproduces libImaging/Resample.c precompute_coeffs precisely:
///   - center = (idx + 0.5) * scale
///   - filter_scale = max(1.0, scale)
///   - support = 3.0 * filter_scale
///   - kernel = sinc(x) * sinc(x/3) for |x| < 3
///   - xmin = Int(center - support + 0.5), clamped to [0, srcSize)
///   - xmax = Int(center + support + 0.5), clamped to (xmin, srcSize] EXCLUSIVE
///   - weights normalized per output pixel
///   - PRECISION_BITS = 32 - 8 - 2 = 22 for fixed-point coefficients (NOT 32)
///
/// CRITICAL: the accumulator must be Int64 (a single tap can be up to 255 * 2^22
/// ≈ 1.07e9, and ~6 taps sum to ~6.4e9 which exceeds Int32.max). The clip8
/// step uses `(acc + bias) >> PRECISION_BITS` — Swift's `>>` on signed Int64
/// is arithmetic right shift, matching Pillow/Java behavior on negative
/// accumulators (which arise from Lanczos's oscillating sinc kernel).

private let PRECISION_BITS: Int = 32 - 8 - 2     // = 22
private let SUPPORT: Double = 3.0
private let PRECISION_SCALE: Int64 = Int64(1) << PRECISION_BITS

func lanczosResize(_ src: [UInt8], srcW: Int, srcH: Int, dstW: Int, dstH: Int) -> [UInt8] {
    let coeffsH = precomputeCoeffs(srcSize: srcW, dstSize: dstW)
    var mid = [UInt8](repeating: 0, count: srcH * dstW)
    for y in 0..<srcH {
        let rowOff = y * srcW
        for xd in 0..<dstW {
            var acc: Int64 = 0
            let w = coeffsH.weights[xd]
            let off = coeffsH.offsets[xd]
            let len = coeffsH.lengths[xd]
            for i in 0..<len {
                acc += Int64(w[i]) * Int64(src[rowOff + off + i])
            }
            mid[y * dstW + xd] = clip8(acc)
        }
    }
    let coeffsV = precomputeCoeffs(srcSize: srcH, dstSize: dstH)
    var result = [UInt8](repeating: 0, count: dstH * dstW)
    for yd in 0..<dstH {
        let w = coeffsV.weights[yd]
        let off = coeffsV.offsets[yd]
        let len = coeffsV.lengths[yd]
        for x in 0..<dstW {
            var acc: Int64 = 0
            for i in 0..<len {
                acc += Int64(w[i]) * Int64(mid[(off + i) * dstW + x])
            }
            result[yd * dstW + x] = clip8(acc)
        }
    }
    return result
}

private func clip8(_ acc: Int64) -> UInt8 {
    let bias = Int64(1) << (PRECISION_BITS - 1)
    let rounded = (acc + bias) >> PRECISION_BITS
    if rounded < 0 { return 0 }
    if rounded > 255 { return 255 }
    return UInt8(rounded)
}

private struct CoeffTable {
    let offsets: [Int]
    let lengths: [Int]
    let weights: [[Int32]]
}

private func precomputeCoeffs(srcSize: Int, dstSize: Int) -> CoeffTable {
    let scale = Double(srcSize) / Double(dstSize)
    let filterScale = max(1.0, scale)
    let support = SUPPORT * filterScale

    var offsets = [Int](repeating: 0, count: dstSize)
    var lengths = [Int](repeating: 0, count: dstSize)
    var weights = [[Int32]](repeating: [], count: dstSize)

    for xd in 0..<dstSize {
        let center = (Double(xd) + 0.5) * scale
        var xmin = Int(center - support + 0.5)
        if xmin < 0 { xmin = 0 }
        var xmax = Int(center + support + 0.5)
        if xmax > srcSize { xmax = srcSize }
        let n = max(0, xmax - xmin)

        var tmp = [Double](repeating: 0, count: n)
        var wsum: Double = 0
        for i in 0..<n {
            let dx = ((Double(xmin + i)) + 0.5 - center) / filterScale
            let w = lanczosKernel(dx)
            tmp[i] = w
            wsum += w
        }
        if wsum != 0 {
            for i in 0..<n { tmp[i] /= wsum }
        }
        var q = [Int32](repeating: 0, count: n)
        for i in 0..<n {
            q[i] = Int32((tmp[i] * Double(PRECISION_SCALE)).rounded())
        }
        offsets[xd] = xmin
        lengths[xd] = n
        weights[xd] = q
    }
    return CoeffTable(offsets: offsets, lengths: lengths, weights: weights)
}

private func lanczosKernel(_ x: Double) -> Double {
    if x == 0 { return 1 }
    let ax = abs(x)
    if ax >= SUPPORT { return 0 }
    let px = Double.pi * x
    return (sin(px) / px) * (sin(px / SUPPORT) / (px / SUPPORT))
}
