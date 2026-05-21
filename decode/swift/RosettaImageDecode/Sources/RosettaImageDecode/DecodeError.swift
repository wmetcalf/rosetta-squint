public enum DecodeError: Error, Equatable {
    case unsupportedFormat(magic: [UInt8])
    case corruptInput(format: Format?, detail: String)
    case truncated(format: Format?, detail: String)
    case unsupportedFeature(format: Format, feature: String)

    public var kindString: String {
        switch self {
        case .unsupportedFormat: return "unsupportedFormat"
        case .corruptInput: return "corruptInput"
        case .truncated: return "truncated"
        case .unsupportedFeature: return "unsupportedFeature"
        }
    }

    public var detail: String {
        switch self {
        case .unsupportedFormat: return ""
        case .corruptInput(_, let detail): return detail
        case .truncated(_, let detail): return detail
        case .unsupportedFeature(_, let feature): return feature
        }
    }
}
