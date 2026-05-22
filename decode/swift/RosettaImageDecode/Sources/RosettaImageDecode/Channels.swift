public enum Channels: Int, Equatable, Sendable {
    case rgb = 3
    case rgba = 4

    public var bytesPerPixel: Int { rawValue }
}
