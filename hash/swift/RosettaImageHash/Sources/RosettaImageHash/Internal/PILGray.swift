/// PIL 'L' (grayscale) conversion via the fixed-point ITU-R 601 luma formula.
///
/// Matches Pillow `Image.convert('L')` exactly:
///   (R*19595 + G*38470 + B*7471 + 32768) >> 16
///
/// For uint8 RGB inputs the pre-shift accumulator max is ~16.78M, well within
/// Int (64-bit on every supported platform). Integer division `/ 65536`
/// truncates toward zero — equivalent to `>> 16` for non-negative operands.
func toGray(_ r: Int, _ g: Int, _ b: Int) -> Int {
    return (r * 19595 + g * 38470 + b * 7471 + 32768) / 65536
}
