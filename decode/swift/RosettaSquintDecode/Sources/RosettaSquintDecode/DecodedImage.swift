public struct DecodedImage: Equatable {
    public let width: Int
    public let height: Int
    public let data: [UInt8]
    public let channels: Channels
    public let format: Format

    public init(width: Int, height: Int, data: [UInt8], channels: Channels, format: Format) {
        precondition(width > 0 && height > 0, "width/height must be positive")
        precondition(data.count == width * height * channels.bytesPerPixel,
                     "data count \(data.count) != \(width)*\(height)*\(channels.bytesPerPixel)")
        self.width = width
        self.height = height
        self.data = data
        self.channels = channels
        self.format = format
    }
}
