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
    /// Maximum allowed size for path-based decode inputs. Refuse anything
    /// larger BEFORE reading bytes. Callers that genuinely need to process
    /// images larger than this should decode via rosetta-image-decode
    /// directly after explicit validation.
    public static let maxFileSize: Int = 256 * 1024 * 1024 // 256 MiB

    public enum RSError: Error, CustomStringConvertible {
        case io(String)
        case decode(RosettaImageDecode.DecodeError)
        case hash(RosettaImageHash.ImageHashError)
        case notRegularFile(String)
        case symlinkNotAllowed(String)
        case fileTooLarge(size: Int, max: Int)

        public var description: String {
            switch self {
            case .io(let s): return "io: \(s)"
            case .decode(let e): return "decode: \(e)"
            case .hash(let e): return "hash: \(e)"
            case .notRegularFile(let path): return "not a regular file: \(path)"
            case .symlinkNotAllowed(let path): return "symlink not allowed: \(path)"
            case .fileTooLarge(let size, let max):
                return "input file too large: \(size) bytes (max \(max) bytes / 256 MiB). "
                    + "For images above this threshold, decode via "
                    + "rosetta-image-decode directly after explicit validation."
            }
        }
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
    ///
    /// Refuses symlinks (via POSIX `open(2)` with `O_NOFOLLOW`),
    /// non-regular files (FIFOs, `/dev/zero`, character devices, etc.)
    /// and files larger than `maxFileSize` BEFORE reading bytes — without
    /// these guards `Data(contentsOf:)` on `/dev/zero` would loop until
    /// OOM and a 300 MiB sparse file would allocate 300 MiB even though
    /// it contains no image. Callers who genuinely want symlink
    /// resolution must do it explicitly (for example via
    /// `URL(fileURLWithPath:).resolvingSymlinksInPath()`) before calling
    /// this method.
    ///
    /// We use POSIX `open(2)` with `O_NOFOLLOW` directly rather than
    /// `FileHandle(forReadingAtPath:)` because Foundation's path-based
    /// open follows symlinks unconditionally. The descriptor returned
    /// from `open` is wrapped in a `FileHandle` so the rest of the read
    /// can use the existing code. The regular-file and size checks then
    /// run against `fstat` on the open fd, not against the path, closing
    /// the TOCTOU window between the size check and the read. The read
    /// is bounded by `maxFileSize + 1` so a concurrent writer that grows
    /// the file post-stat is still rejected.
    public static func decodeFile(at path: String) throws -> RosettaImageHash.RGBImage {
        // POSIX `open(2)` with O_NOFOLLOW: fails with ELOOP if the final
        // path component is a symlink.
        let fd = path.withCString { cpath -> Int32 in
            open(cpath, O_RDONLY | O_NOFOLLOW)
        }
        if fd < 0 {
            let err = errno
            if err == ELOOP {
                throw RSError.symlinkNotAllowed(path)
            }
            throw RSError.io("open failed: \(path): errno=\(err)")
        }
        // Wrap the descriptor in a FileHandle so we can reuse the existing
        // read loop. `closeOnDealloc: true` ensures the fd is released
        // when the handle goes away (or when we explicitly close below).
        let handle = FileHandle(fileDescriptor: fd, closeOnDealloc: true)
        defer { try? handle.close() }

        // fstat the open fd to defeat TOCTOU. POSIX `stat` struct lives in
        // Darwin/Glibc — Foundation doesn't surface fstat directly, but the
        // raw fd is exposed on FileHandle.
        var st = stat()
        guard fstat(handle.fileDescriptor, &st) == 0 else {
            throw RSError.io("fstat failed: \(path): errno=\(errno)")
        }
        // S_IFREG check: regular file
        let isRegular = (st.st_mode & S_IFMT) == S_IFREG
        if !isRegular {
            throw RSError.notRegularFile(path)
        }
        // st_size is off_t (Int64 on Darwin/Linux). Bound to Int with
        // overflow check.
        let rawSize = Int64(st.st_size)
        let size: Int
        if rawSize > Int64(Int.max) {
            size = Int.max
        } else if rawSize < 0 {
            size = 0
        } else {
            size = Int(rawSize)
        }
        if size > Self.maxFileSize {
            throw RSError.fileTooLarge(size: size, max: Self.maxFileSize)
        }
        // Read up to maxFileSize+1 bytes total. `readData(ofLength:)` may
        // return fewer bytes than requested in a single call (e.g. for
        // sockets, pipes, or partial reads on regular files); loop until
        // we hit EOF or the limit. If we get the (maxFileSize+1)th byte
        // it means the file grew after fstat → reject.
        let limit = Self.maxFileSize + 1
        var data = Data()
        data.reserveCapacity(min(size, Self.maxFileSize) + 1)
        while data.count < limit {
            let chunk = handle.readData(ofLength: limit - data.count)
            if chunk.isEmpty { break }
            data.append(chunk)
        }
        if data.count > Self.maxFileSize {
            throw RSError.fileTooLarge(size: data.count, max: Self.maxFileSize)
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
