import Foundation

/// Normalize an RGBImage (3 or 4 channels) to a flat [UInt8] of RGB triples
/// (row-major, length = width * height * 3).
///
/// For .rgba, alpha is composited against opaque black via
/// `out_c = floor(src_c * alpha / 255)`, matching PIL `convert('RGB')`.
/// Fully transparent → (0,0,0); fully opaque → src RGB unchanged.
enum ImgRGB {
	struct RGB3 {
		let width: Int
		let height: Int
		let data: [UInt8]
	}

	static func toRGB(_ image: RGBImage) -> RGB3 {
		if image.channels == .rgb {
			return RGB3(width: image.width, height: image.height, data: image.data)
		}
		// .rgba
		let n = image.width * image.height
		var out = [UInt8](repeating: 0, count: n * 3)
		var si = 0
		var di = 0
		for _ in 0..<n {
			let r = Int(image.data[si])
			let g = Int(image.data[si + 1])
			let b = Int(image.data[si + 2])
			let a = Int(image.data[si + 3])
			out[di] = UInt8((r * a) / 255)
			out[di + 1] = UInt8((g * a) / 255)
			out[di + 2] = UInt8((b * a) / 255)
			si += 4
			di += 3
		}
		return RGB3(width: image.width, height: image.height, data: out)
	}
}
