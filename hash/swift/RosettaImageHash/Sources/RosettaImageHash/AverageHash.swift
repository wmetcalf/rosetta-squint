import Foundation

/// ahash: convert to grayscale, Lanczos resize to NxN, threshold against mean.
public func averageHash(_ image: RGBImage, hashSize: Int) throws -> Hash {
    guard hashSize >= 2 else { throw ImageHashError.invalidHashSize(hashSize) }
    let rgb = ImgRGB.toRGB(image)
    let gray = rgbToGray(rgb.data, width: rgb.width, height: rgb.height)
    let resized = lanczosResize(gray, srcW: rgb.width, srcH: rgb.height, dstW: hashSize, dstH: hashSize)

    var sum = 0
    for v in resized { sum += Int(v) }
    let avg = Double(sum) / Double(hashSize * hashSize)

    var bits: [[Bool]] = []
    for y in 0..<hashSize {
        var row = [Bool](repeating: false, count: hashSize)
        for x in 0..<hashSize {
            row[x] = Double(resized[y * hashSize + x]) > avg
        }
        bits.append(row)
    }
    return try Hash(bits: bits)
}

/// Shared helper: convert a flat RGB [UInt8] (row-major triples) to a flat grayscale [UInt8].
/// Used by averageHash, dhash, phash, whashHaar (colorhash uses toGray directly per-pixel).
internal func rgbToGray(_ rgb: [UInt8], width: Int, height: Int) -> [UInt8] {
    var out = [UInt8](repeating: 0, count: width * height)
    var si = 0
    for i in 0..<(width * height) {
        out[i] = UInt8(toGray(Int(rgb[si]), Int(rgb[si + 1]), Int(rgb[si + 2])))
        si += 3
    }
    return out
}
