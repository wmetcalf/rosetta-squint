import Foundation

/// 2-D Haar DWT/IDWT with pywt's 'symmetric' boundary mode.
///
/// CRITICAL: SQRT2_INV = (0.5).squareRoot() (NOT 1.0 / 2.0.squareRoot()).
/// The latter is one ULP lower (0.7071067811865475 vs 0.7071067811865476)
/// and accumulates errors through 8-10 wavedec levels, flipping bits at
/// the whash median boundary. Confirmed across Java/Go/Rust/JS ports.
///
/// dwt2 evaluates column-pass BEFORE row-pass (required for float addition order).
/// idwt2 reverses axis order: row-pass first, then column-pass.

private let SQRT2_INV: Double = (0.5).squareRoot()

struct DWT2Result {
    let cA: [[Double]]
    let cH: [[Double]]
    let cV: [[Double]]
    let cD: [[Double]]
}

struct WavedecResult {
    var cA: [[Double]]
    /// Each entry is [cH, cV, cD] for one level. Outer-to-inner = deepest-first.
    var details: [[[[Double]]]]
}

func dwt2(_ x: [[Double]]) -> DWT2Result {
    let h = x.count
    let w = x[0].count

    // Column pass first
    var colLow = [[Double]](repeating: [], count: w)
    var colHigh = [[Double]](repeating: [], count: w)
    for xCol in 0..<w {
        var col = [Double](repeating: 0, count: h)
        for y in 0..<h { col[y] = x[y][xCol] }
        let (low, high) = dwt1d(col)
        colLow[xCol] = low
        colHigh[xCol] = high
    }
    let outH = (h + 1) >> 1
    let outW = (w + 1) >> 1

    var cA = [[Double]](repeating: [Double](repeating: 0, count: outW), count: outH)
    var cH = [[Double]](repeating: [Double](repeating: 0, count: outW), count: outH)
    var cV = [[Double]](repeating: [Double](repeating: 0, count: outW), count: outH)
    var cD = [[Double]](repeating: [Double](repeating: 0, count: outW), count: outH)

    for y in 0..<outH {
        var rowLow = [Double](repeating: 0, count: w)
        var rowHigh = [Double](repeating: 0, count: w)
        for xCol in 0..<w {
            rowLow[xCol] = colLow[xCol][y]
            rowHigh[xCol] = colHigh[xCol][y]
        }
        let (lowL, highL) = dwt1d(rowLow)
        let (lowH, highH) = dwt1d(rowHigh)
        for xd in 0..<outW {
            cA[y][xd] = lowL[xd]
            cV[y][xd] = highL[xd]
            cH[y][xd] = lowH[xd]
            cD[y][xd] = highH[xd]
        }
    }
    return DWT2Result(cA: cA, cH: cH, cV: cV, cD: cD)
}

func idwt2(cA: [[Double]], cH: [[Double]], cV: [[Double]], cD: [[Double]]) -> [[Double]] {
    let sh = cA.count
    let sw = cA[0].count
    let outW = sw * 2
    let outH = sh * 2

    // Row-pass first (inverse of column-pass-first in dwt2):
    //   cA and cV share the column-low-pass origin -> combine along rows: idwt1d(cA[y], cV[y])
    //   cH and cD share the column-high-pass origin -> combine along rows: idwt1d(cH[y], cD[y])
    var rowLow = [[Double]](repeating: [], count: sh)
    var rowHigh = [[Double]](repeating: [], count: sh)
    for y in 0..<sh {
        rowLow[y] = idwt1d(low: cA[y], high: cV[y])
        rowHigh[y] = idwt1d(low: cH[y], high: cD[y])
    }

    // Column-pass second: for each output column, combine rowLow's column with rowHigh's column.
    var out = [[Double]](repeating: [Double](repeating: 0, count: outW), count: outH)
    for xCol in 0..<outW {
        var low = [Double](repeating: 0, count: sh)
        var high = [Double](repeating: 0, count: sh)
        for y in 0..<sh {
            low[y] = rowLow[y][xCol]
            high[y] = rowHigh[y][xCol]
        }
        let col = idwt1d(low: low, high: high)
        for y2 in 0..<outH { out[y2][xCol] = col[y2] }
    }
    return out
}

func wavedec2(_ x: [[Double]], level: Int) -> WavedecResult {
    var current = x
    var details: [[[[Double]]]] = []
    for _ in 0..<level {
        let r = dwt2(current)
        details.append([r.cH, r.cV, r.cD])
        current = r.cA
    }
    details.reverse()
    return WavedecResult(cA: current, details: details)
}

func waverec2(_ d: WavedecResult) -> [[Double]] {
    var current = d.cA
    for level in d.details {
        current = idwt2(cA: current, cH: level[0], cV: level[1], cD: level[2])
    }
    return current
}

private func dwt1d(_ x: [Double]) -> (low: [Double], high: [Double]) {
    var xx = x
    var n = xx.count
    if (n & 1) != 0 {
        xx.append(xx[n - 1])
        n += 1
    }
    let half = n >> 1
    var low = [Double](repeating: 0, count: half)
    var high = [Double](repeating: 0, count: half)
    for i in 0..<half {
        let a = xx[2 * i]
        let b = xx[2 * i + 1]
        low[i] = SQRT2_INV * a + SQRT2_INV * b
        high[i] = SQRT2_INV * a - SQRT2_INV * b
    }
    return (low, high)
}

private func idwt1d(low: [Double], high: [Double]) -> [Double] {
    let half = low.count
    let n = half * 2
    var out = [Double](repeating: 0, count: n)
    for i in 0..<half {
        out[2 * i] = SQRT2_INV * low[i] + SQRT2_INV * high[i]
        out[2 * i + 1] = SQRT2_INV * low[i] - SQRT2_INV * high[i]
    }
    return out
}
