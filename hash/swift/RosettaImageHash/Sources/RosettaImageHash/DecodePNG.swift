import Foundation
import PNG

/// Decode PNG bytes into an RGBImage with channels = .rgba.
/// Internal algorithms composite alpha against opaque black per PIL `convert('RGB')`.
public func decodePNG(_ bytes: [UInt8]) throws -> RGBImage {
	var blob = PNGBlob(data: bytes)
	let image: PNG.Image = try .decompress(stream: &blob)
	let rgba: [PNG.RGBA<UInt8>] = image.unpack(as: PNG.RGBA<UInt8>.self)
	let width = image.size.x
	let height = image.size.y

	// Flatten [PNG.RGBA<UInt8>] -> [UInt8] in RGBA order
	var data = [UInt8](repeating: 0, count: rgba.count * 4)
	for i in 0..<rgba.count {
		data[i * 4]     = rgba[i].r
		data[i * 4 + 1] = rgba[i].g
		data[i * 4 + 2] = rgba[i].b
		data[i * 4 + 3] = rgba[i].a
	}
	return RGBImage(width: width, height: height, data: data, channels: .rgba)
}

/// In-memory byte stream conforming to swift-png's bytestream protocol.
internal struct PNGBlob: PNG.BytestreamSource {
	let data: [UInt8]
	var position: Int

	init(data: [UInt8]) {
		self.data = data
		self.position = 0
	}

	mutating func read(count: Int) -> [UInt8]? {
		guard position + count <= data.count else { return nil }
		defer { position += count }
		return Array(data[position..<(position + count)])
	}
}
