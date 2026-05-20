import Foundation

/// colorhash: HSV-binned histogram hash.
///
/// Bins: black (L<32), gray (L>=32 and S<85), 6 faint hue bins (85<=S<170),
/// 6 bright hue bins (S>170). S==170 increments colorfulCount (denominator)
/// but lands in neither hue histogram.
public func colorhash(_ image: RGBImage, binbits: Int) throws -> Hash {
    guard binbits >= 1 else { throw ImageHashError.invalidBinbits(binbits) }
    let rgb = ImgRGB.toRGB(image)
    let data = rgb.data
    let w = rgb.width
    let h = rgb.height
    let n = w * h

    var blackCount = 0
    var grayCount = 0
    var colorfulCount = 0
    var faintBins = [0, 0, 0, 0, 0, 0]
    var brightBins = [0, 0, 0, 0, 0, 0]

    var si = 0
    for _ in 0..<n {
        let r = Int(data[si])
        let g = Int(data[si + 1])
        let b = Int(data[si + 2])
        si += 3
        let l = toGray(r, g, b)
        if l < 32 {
            blackCount += 1
            continue
        }
        let (hue, s, _) = toHSV(r, g, b)
        if s < 85 {
            grayCount += 1
            continue
        }
        colorfulCount += 1
        var hueBin = (hue * 6) / 255
        if hueBin > 5 { hueBin = 5 }
        if s < 170 {
            faintBins[hueBin] += 1
        } else if s > 170 {
            brightBins[hueBin] += 1
        }
    }

    let maxVal = 1 << binbits
    let c = max(1, colorfulCount)
    let clip: (Int) -> Int = { v in v > maxVal - 1 ? maxVal - 1 : v }

    var values = [Int](repeating: 0, count: 14)
    values[0] = clip((blackCount * maxVal) / n)
    values[1] = clip((grayCount * maxVal) / n)
    for i in 0..<6 {
        values[2 + i] = clip((faintBins[i] * maxVal) / c)
        values[8 + i] = clip((brightBins[i] * maxVal) / c)
    }

    var bits: [[Bool]] = []
    for i in 0..<14 {
        bits.append(colorhashBinEncode(values[i], binbits: binbits))
    }
    return try Hash(bits: bits)
}

/// SPEC.md §8 quirky bin encoding.
///
/// bit[i] = (v >> (B-i-1)) & ((1 << (B-i)) - 1) > 0
///
/// Worked: v=4, B=4 → [F,T,T,F] (0x6); v=8, B=4 → [T,T,F,F] (0xc). NOT standard binary.
public func colorhashBinEncode(_ v: Int, binbits: Int) -> [Bool] {
    var bits = [Bool](repeating: false, count: binbits)
    for i in 0..<binbits {
        let shifted = v >> (binbits - i - 1)
        let masked = shifted & ((1 << (binbits - i)) - 1)
        bits[i] = masked > 0
    }
    return bits
}
