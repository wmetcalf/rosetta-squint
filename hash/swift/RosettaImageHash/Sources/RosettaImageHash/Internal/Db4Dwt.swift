import Foundation

/// Forward-only 2-D Daubechies-4 DWT with pywt 'symmetric' (half-sample symmetric) boundary mode.
///
/// This implements pywt.wavedec2(x, 'db4', mode='symmetric', level=N) — forward transform only.
/// No IDWT is needed for whashDb4 because the Haar LL-removal step uses the existing Haar IDWT.
///
/// Filter coefficients are pywt's db4 (8-tap), NOT the 4-tap 'db2'.
/// Verified against pywt output; see spec/db4_cases.json.
///
/// Boundary extension:
///   pad the signal by filt_len=8 samples on each side using numpy-style 'symmetric' extension:
///   left:  x[7], x[6], ..., x[0]  (mirrors outward, including x[0])
///   right: x[n-1], x[n-2], ..., x[n-8]  (mirrors inward from the right edge)
///
/// Convolution: output[k] = sum_{j=0}^{7} h_lo[7-j] * x_padded[start + 2*k + j]
///   where start = 2 (empirically verified to match pywt's output positioning).
///
/// Output length: (n + filt_len - 1) / 2 = (n + 7) / 2.
///
/// Axis order: column-pass BEFORE row-pass (matching pywt.dwt2 and the existing Haar code).

// pywt db4 decomposition filter coefficients (8-tap).
// Values are the exact IEEE-754 doubles stored in pywt; do not reformat.
private let DB4_LO: [Double] = [
    -0.010597401785069032,
     0.0328830116668852,
     0.030841381835560764,
    -0.18703481171909309,
    -0.027983769416859854,
     0.6308807679298589,
     0.7148465705529157,
     0.2303778133088965
]
private let DB4_HI: [Double] = [
    -0.2303778133088965,
     0.7148465705529157,
    -0.6308807679298589,
    -0.027983769416859854,
     0.18703481171909309,
     0.030841381835560764,
    -0.0328830116668852,
    -0.010597401785069032
]
private let DB4_FILT_LEN = 8
private let DB4_PAD = 8         // pad by filt_len on each side
private let DB4_START = 2       // starting offset in the padded array

/// Half-sample symmetric (periodic) index access for a signal of length n.
/// Handles signals shorter than the padding by wrapping with period 2n.
/// Equivalent to numpy's np.pad(x, pad, mode='symmetric').
@inline(__always)
private func symSample(_ x: [Double], _ i: Int) -> Double {
    let n = x.count
    var idx = i % (2 * n)
    if idx < 0 { idx += 2 * n }
    return idx < n ? x[idx] : x[2 * n - 1 - idx]
}

/// 1-D db4 DWT with symmetric extension. Returns (cA, cD).
func db4Dwt1d(_ x: [Double]) -> (low: [Double], high: [Double]) {
    let n = x.count
    let outLen = (n + DB4_FILT_LEN - 1) / 2

    // Build padded array using periodic half-sample symmetric extension.
    // This matches numpy.pad(x, DB4_PAD, mode='symmetric') for all n >= 1,
    // including n < DB4_PAD where simple reflection would go out of bounds.
    var xp = [Double](repeating: 0.0, count: DB4_PAD + n + DB4_PAD)
    for i in 0..<DB4_PAD {
        xp[i] = symSample(x, i - DB4_PAD)
    }
    for i in 0..<n {
        xp[DB4_PAD + i] = x[i]
    }
    for i in 0..<DB4_PAD {
        xp[DB4_PAD + n + i] = symSample(x, n + i)
    }

    var low  = [Double](repeating: 0.0, count: outLen)
    var high = [Double](repeating: 0.0, count: outLen)
    for k in 0..<outLen {
        var sumLo = 0.0
        var sumHi = 0.0
        let base = DB4_START + 2 * k
        // Accumulate in reverse-j order to match pywt's C inner-loop direction.
        // This eliminates ~1e-14 ULP divergence on near-zero values that would
        // otherwise flip bits at the median boundary.
        var j = DB4_FILT_LEN - 1
        while j >= 0 {
            let v = xp[base + j]
            sumLo += DB4_LO[DB4_FILT_LEN - 1 - j] * v
            sumHi += DB4_HI[DB4_FILT_LEN - 1 - j] * v
            j -= 1
        }
        low[k]  = sumLo
        high[k] = sumHi
    }
    return (low, high)
}

/// 2-D db4 DWT: column-pass first, then row-pass. Returns (cA, cH, cV, cD).
private func db4Dwt2d(_ x: [[Double]]) -> (cA: [[Double]], cH: [[Double]], cV: [[Double]], cD: [[Double]]) {
    let h = x.count
    let w = x[0].count
    let outH = (h + DB4_FILT_LEN - 1) / 2
    let outW = (w + DB4_FILT_LEN - 1) / 2

    // Column pass
    var colLow  = [[Double]](repeating: [Double](repeating: 0.0, count: outH), count: w)
    var colHigh = [[Double]](repeating: [Double](repeating: 0.0, count: outH), count: w)
    for col in 0..<w {
        var column = [Double](repeating: 0.0, count: h)
        for row in 0..<h { column[row] = x[row][col] }
        let (lo, hi) = db4Dwt1d(column)
        colLow[col]  = lo
        colHigh[col] = hi
    }

    // Row pass
    var cA = [[Double]](repeating: [Double](repeating: 0.0, count: outW), count: outH)
    var cH = [[Double]](repeating: [Double](repeating: 0.0, count: outW), count: outH)
    var cV = [[Double]](repeating: [Double](repeating: 0.0, count: outW), count: outH)
    var cD = [[Double]](repeating: [Double](repeating: 0.0, count: outW), count: outH)

    for row in 0..<outH {
        var rowLo  = [Double](repeating: 0.0, count: w)
        var rowHi  = [Double](repeating: 0.0, count: w)
        for col in 0..<w {
            rowLo[col]  = colLow[col][row]
            rowHi[col]  = colHigh[col][row]
        }
        let (aL, aH) = db4Dwt1d(rowLo)
        let (bL, bH) = db4Dwt1d(rowHi)
        for col in 0..<outW {
            cA[row][col] = aL[col]   // LL: col-low + row-low
            cV[row][col] = aH[col]   // LH: col-low + row-high (vertical detail)
            cH[row][col] = bL[col]   // HL: col-high + row-low (horizontal detail)
            cD[row][col] = bH[col]   // HH: col-high + row-high (diagonal detail)
        }
    }
    return (cA, cH, cV, cD)
}

/// Forward db4 wavelet decomposition to `level` levels, returning the LL subband.
/// Equivalent to pywt.wavedec2(x, 'db4', mode='symmetric', level=level)[0].
func db4Wavedec2(_ x: [[Double]], level: Int) -> [[Double]] {
    var current = x
    for _ in 0..<level {
        let (cA, _, _, _) = db4Dwt2d(current)
        current = cA
    }
    return current
}
