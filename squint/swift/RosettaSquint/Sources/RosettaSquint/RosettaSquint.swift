import Foundation
import RosettaImageDecode
import RosettaImageHash

// Re-export the hash types so callers only need to import RosettaSquint.
public typealias Hash = RosettaImageHash.Hash
public typealias ImageMultiHash = RosettaImageHash.ImageMultiHash
public typealias ImageHashError = RosettaImageHash.ImageHashError

// Re-export hex round-trip helpers.
public let hexToHash: (String) throws -> Hash = RosettaImageHash.hexToHash
public let hexToFlathash: (String, Int) throws -> Hash = { hex, sz in
    try RosettaImageHash.hexToFlathash(hex, hashSize: sz)
}
public let hexToMultiHash: (String) throws -> ImageMultiHash = RosettaImageHash.hexToMultiHash

public enum RosettaSquint {
    public enum RSError: Error {
        case io(String)
        case decode(RosettaImageDecode.DecodeError)
        case hash(RosettaImageHash.ImageHashError)
    }

    /// Bridge a `RosettaImageDecode.DecodedImage` into the `RGBImage` shape
    /// expected by `RosettaImageHash`. Both packages define a `Channels` enum
    /// with `.rgb` and `.rgba` cases — we map by name to avoid the clash.
    public static func adapt(_ d: RosettaImageDecode.DecodedImage) -> RosettaImageHash.RGBImage {
        let hashChannels: RosettaImageHash.RGBImage.Channels =
            d.channels == RosettaImageDecode.Channels.rgba ? .rgba : .rgb
        return RosettaImageHash.RGBImage(
            width: d.width,
            height: d.height,
            data: d.data,
            channels: hashChannels
        )
    }

    /// Decode raw image bytes (any supported format) into an `RGBImage`.
    public static func decodeBytes(_ bytes: [UInt8]) throws -> RosettaImageHash.RGBImage {
        do {
            let decoded = try RosettaImageDecode.Decoder.decode(bytes)
            return adapt(decoded)
        } catch let e as RosettaImageDecode.DecodeError {
            throw RSError.decode(e)
        }
    }

    /// Read a file from `path` and decode it into an `RGBImage`.
    public static func decodeFile(at path: String) throws -> RosettaImageHash.RGBImage {
        let url = URL(fileURLWithPath: path)
        guard let data = try? Data(contentsOf: url) else {
            throw RSError.io("read failed: \(path)")
        }
        return try decodeBytes(Array(data))
    }

    // MARK: - phash

    public static func phash(at path: String, hashSize: Int = 8) throws -> Hash {
        let img = try decodeFile(at: path)
        return try RosettaImageHash.phash(img, hashSize: hashSize)
    }

    public static func phash(bytes: [UInt8], hashSize: Int = 8) throws -> Hash {
        let img = try decodeBytes(bytes)
        return try RosettaImageHash.phash(img, hashSize: hashSize)
    }

    // MARK: - phashSimple

    public static func phashSimple(at path: String, hashSize: Int = 8) throws -> Hash {
        let img = try decodeFile(at: path)
        return try RosettaImageHash.phashSimple(img, hashSize: hashSize)
    }

    public static func phashSimple(bytes: [UInt8], hashSize: Int = 8) throws -> Hash {
        let img = try decodeBytes(bytes)
        return try RosettaImageHash.phashSimple(img, hashSize: hashSize)
    }

    // MARK: - averageHash

    public static func averageHash(at path: String, hashSize: Int = 8) throws -> Hash {
        let img = try decodeFile(at: path)
        return try RosettaImageHash.averageHash(img, hashSize: hashSize)
    }

    public static func averageHash(bytes: [UInt8], hashSize: Int = 8) throws -> Hash {
        let img = try decodeBytes(bytes)
        return try RosettaImageHash.averageHash(img, hashSize: hashSize)
    }

    // MARK: - dhash

    public static func dhash(at path: String, hashSize: Int = 8) throws -> Hash {
        let img = try decodeFile(at: path)
        return try RosettaImageHash.dhash(img, hashSize: hashSize)
    }

    public static func dhash(bytes: [UInt8], hashSize: Int = 8) throws -> Hash {
        let img = try decodeBytes(bytes)
        return try RosettaImageHash.dhash(img, hashSize: hashSize)
    }

    // MARK: - dhashVertical

    public static func dhashVertical(at path: String, hashSize: Int = 8) throws -> Hash {
        let img = try decodeFile(at: path)
        return try RosettaImageHash.dhashVertical(img, hashSize: hashSize)
    }

    public static func dhashVertical(bytes: [UInt8], hashSize: Int = 8) throws -> Hash {
        let img = try decodeBytes(bytes)
        return try RosettaImageHash.dhashVertical(img, hashSize: hashSize)
    }

    // MARK: - whashHaar

    public static func whashHaar(at path: String, hashSize: Int = 8) throws -> Hash {
        let img = try decodeFile(at: path)
        return try RosettaImageHash.whashHaar(img, hashSize: hashSize)
    }

    public static func whashHaar(bytes: [UInt8], hashSize: Int = 8) throws -> Hash {
        let img = try decodeBytes(bytes)
        return try RosettaImageHash.whashHaar(img, hashSize: hashSize)
    }

    // MARK: - whashDb4

    public static func whashDb4(at path: String, hashSize: Int = 8) throws -> Hash {
        let img = try decodeFile(at: path)
        return try RosettaImageHash.whashDb4(img, hashSize: hashSize)
    }

    public static func whashDb4(bytes: [UInt8], hashSize: Int = 8) throws -> Hash {
        let img = try decodeBytes(bytes)
        return try RosettaImageHash.whashDb4(img, hashSize: hashSize)
    }

    // MARK: - whashDb4Robust

    public static func whashDb4Robust(at path: String, hashSize: Int = 8) throws -> Hash {
        let img = try decodeFile(at: path)
        return try RosettaImageHash.whashDb4Robust(img, hashSize: hashSize)
    }

    public static func whashDb4Robust(bytes: [UInt8], hashSize: Int = 8) throws -> Hash {
        let img = try decodeBytes(bytes)
        return try RosettaImageHash.whashDb4Robust(img, hashSize: hashSize)
    }

    // MARK: - colorhash

    public static func colorhash(at path: String, binbits: Int = 3) throws -> Hash {
        let img = try decodeFile(at: path)
        return try RosettaImageHash.colorhash(img, binbits: binbits)
    }

    public static func colorhash(bytes: [UInt8], binbits: Int = 3) throws -> Hash {
        let img = try decodeBytes(bytes)
        return try RosettaImageHash.colorhash(img, binbits: binbits)
    }

    // MARK: - cropResistantHash

    public static func cropResistantHash(
        at path: String,
        limitSegments: Int? = nil,
        segmentThreshold: Float = 128.0,
        minSegmentSize: Int = 500,
        segmentationImageSize: Int = 300
    ) throws -> ImageMultiHash {
        let img = try decodeFile(at: path)
        return try RosettaImageHash.cropResistantHash(
            img,
            limitSegments: limitSegments,
            segmentThreshold: segmentThreshold,
            minSegmentSize: minSegmentSize,
            segmentationImageSize: segmentationImageSize
        )
    }

    public static func cropResistantHash(
        bytes: [UInt8],
        limitSegments: Int? = nil,
        segmentThreshold: Float = 128.0,
        minSegmentSize: Int = 500,
        segmentationImageSize: Int = 300
    ) throws -> ImageMultiHash {
        let img = try decodeBytes(bytes)
        return try RosettaImageHash.cropResistantHash(
            img,
            limitSegments: limitSegments,
            segmentThreshold: segmentThreshold,
            minSegmentSize: minSegmentSize,
            segmentationImageSize: segmentationImageSize
        )
    }
}
