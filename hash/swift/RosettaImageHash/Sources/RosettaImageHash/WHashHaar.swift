import Foundation

private func isPowerOfTwo(_ n: Int) -> Bool {
    return n > 0 && (n & (n - 1)) == 0
}

/// whash with mode='haar', remove_max_haar_ll=true, image_scale=None.
public func whashHaar(_ image: RGBImage, hashSize: Int) throws -> Hash {
    guard hashSize >= 2 else { throw ImageHashError.invalidHashSize(hashSize) }
    guard isPowerOfTwo(hashSize) else { throw ImageHashError.notPowerOfTwo(hashSize) }

    let rgb = ImgRGB.toRGB(image)
    let gray = rgbToGray(rgb.data, width: rgb.width, height: rgb.height)

    let minSide = min(rgb.width, rgb.height)
    let imageNaturalScale = 1 << Int(log2(Double(minSide)))
    let imageScale = max(imageNaturalScale, hashSize)

    let llMaxLevel = Int(log2(Double(imageScale)))
    let level = Int(log2(Double(hashSize)))
    guard level <= llMaxLevel else {
        throw ImageHashError.hashSizeTooLarge(level: level, maxLevel: llMaxLevel)
    }
    let dwtLevel = llMaxLevel - level

    let resized = lanczosResize(gray, srcW: rgb.width, srcH: rgb.height, dstW: imageScale, dstH: imageScale)
    var pixels: [[Double]] = []
    for y in 0..<imageScale {
        var row = [Double](repeating: 0, count: imageScale)
        for x in 0..<imageScale {
            row[x] = Double(resized[y * imageScale + x]) / 255.0
        }
        pixels.append(row)
    }

    // remove_max_haar_ll: full decomp at llMaxLevel, zero LL band, reconstruct.
    var fullDec = wavedec2(pixels, level: llMaxLevel)
    for y in 0..<fullDec.cA.count {
        for x in 0..<fullDec.cA[y].count {
            fullDec.cA[y][x] = 0
        }
    }
    let modified = waverec2(fullDec)

    let dec = wavedec2(modified, level: dwtLevel)
    let ll = dec.cA

    var flat: [Double] = []
    for row in ll { flat.append(contentsOf: row) }
    let sorted = flat.sorted()
    let n = sorted.count
    let median: Double = n % 2 == 1 ? sorted[(n - 1) / 2] : (sorted[n / 2 - 1] + sorted[n / 2]) / 2.0

    var bits: [[Bool]] = []
    for row in ll {
        bits.append(row.map { $0 > median })
    }
    return try Hash(bits: bits)
}
