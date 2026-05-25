import Foundation

/// dhash: grayscale → Lanczos to (W=N+1, H=N) → row-wise adjacent-column diff (strict >).
public func dhash(_ image: RGBImage, hashSize: Int) throws -> Hash {
    try image.validate()
    guard hashSize >= 2 else { throw ImageHashError.invalidHashSize(hashSize) }
    let rgb = ImgRGB.toRGB(image)
    let gray = rgbToGray(rgb.data, width: rgb.width, height: rgb.height)
    let resized = lanczosResize(gray, srcW: rgb.width, srcH: rgb.height, dstW: hashSize + 1, dstH: hashSize)

    var bits: [[Bool]] = []
    for y in 0..<hashSize {
        let rowOff = y * (hashSize + 1)
        var row = [Bool](repeating: false, count: hashSize)
        for x in 0..<hashSize {
            row[x] = resized[rowOff + x + 1] > resized[rowOff + x]
        }
        bits.append(row)
    }
    return try Hash(bits: bits)
}
