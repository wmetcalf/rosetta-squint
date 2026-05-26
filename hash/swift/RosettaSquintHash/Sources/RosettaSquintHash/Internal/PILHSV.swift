/// PIL 'HSV' conversion using the integer formula from libImaging/Convert.c rgb2hsv_row.
///
/// IMPORTANT: PIL uses INTEGER arithmetic on uint8 RGB directly, not a float HSV
/// scaled by 255. Naive Math.round(float_hsv * 255) diverges on .5 boundaries.
/// Example: RGB(100,150,200) → PIL (148,127,200); naive round produces (149,128,200).
///
/// Negative `hPre` (when r is max and bc < gc) is wrapped via `+= 6 * 255` so the
/// final division is on a non-negative integer. All integer divisions use Swift's
/// `Int /` which truncates toward zero — equivalent to Python `//` for non-negative
/// operands. The wrap step ensures non-negative input to the final `/ 6`.
func toHSV(_ r: Int, _ g: Int, _ b: Int) -> (Int, Int, Int) {
    let maxc = max(r, g, b)
    let minc = min(r, g, b)
    let v = maxc
    if maxc == 0 { return (0, 0, v) }
    let s = (255 * (maxc - minc)) / maxc
    if minc == maxc { return (0, s, v) }
    let delta = maxc - minc
    let rc = ((maxc - r) * 255) / delta
    let gc = ((maxc - g) * 255) / delta
    let bc = ((maxc - b) * 255) / delta
    var hPre: Int
    if r == maxc {
        hPre = bc - gc
    } else if g == maxc {
        hPre = 2 * 255 + rc - bc
    } else {
        hPre = 4 * 255 + gc - rc
    }
    if hPre < 0 { hPre += 6 * 255 }
    return (hPre / 6, s, v)
}
