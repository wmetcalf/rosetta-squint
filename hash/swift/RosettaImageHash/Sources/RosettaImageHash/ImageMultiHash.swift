import Foundation

/// Multi-segment image hash for crop-resistant hashing.
///
/// Matches Python `imagehash.ImageMultiHash`. The distance metric returns a Double
/// (not Int) — callers must not assume an integer result.
public struct ImageMultiHash: Equatable, Hashable, CustomStringConvertible {
    public let segmentHashes: [Hash]

    public init(segmentHashes: [Hash]) {
        self.segmentHashes = segmentHashes
    }

    // MARK: - Stringification

    /// Comma-separated hex strings of each segment hash.
    public var description: String {
        segmentHashes.map { String(describing: $0) }.joined(separator: ",")
    }

    // MARK: - Equatable / Hashable

    public static func == (lhs: ImageMultiHash, rhs: ImageMultiHash) -> Bool {
        lhs.matches(rhs)
    }

    public func hash(into hasher: inout Hasher) {
        for h in segmentHashes {
            hasher.combine(h)
        }
    }

    // MARK: - Distance

    /// Returns a float distance between two multi-hashes.
    ///
    /// Matches Python `ImageMultiHash.__sub__`. Returns `Double` (not Int).
    ///
    /// - Parameters:
    ///   - other: The other multi-hash.
    ///   - hammingCutoff: Maximum Hamming distance for a match. Defaults to
    ///     `bitErrorRate * len(segment_hashes[0])` if not set.
    ///   - bitErrorRate: Fraction of bits allowed to differ. Default 0.25.
    public func subtract(
        _ other: ImageMultiHash,
        hammingCutoff: Double? = nil,
        bitErrorRate: Double? = nil
    ) -> Double {
        let (matches, sumDistance) = hashDiff(
            other,
            hammingCutoff: hammingCutoff,
            bitErrorRate: bitErrorRate
        )
        let maxDifference = Double(segmentHashes.count)
        guard matches > 0 else { return maxDifference }
        let maxDistance = Double(matches * (segmentHashes[0].bitCount))
        let tieBreaker = -(Double(sumDistance) / maxDistance)
        let matchScore = Double(matches) + tieBreaker
        return maxDifference - matchScore
    }

    /// Returns `(matches, sumDistance)` — the number of matching segment pairs
    /// within the cutoff and the sum of their Hamming distances.
    ///
    /// Matches Python `ImageMultiHash.hash_diff`.
    public func hashDiff(
        _ other: ImageMultiHash,
        hammingCutoff: Double? = nil,
        bitErrorRate: Double? = nil
    ) -> (matches: Int, sumDistance: Int) {
        let cutoff: Double
        if let hc = hammingCutoff {
            cutoff = hc
        } else {
            let ber = bitErrorRate ?? 0.25
            cutoff = Double(segmentHashes[0].bitCount) * ber
        }

        var distances: [Int] = []
        for segHash in segmentHashes {
            // Find the lowest Hamming distance to any segment in `other`.
            var lowestDist = Int.max
            for otherSeg in other.segmentHashes {
                if let d = try? segHash.subtract(otherSeg), d < lowestDist {
                    lowestDist = d
                }
            }
            guard lowestDist != Int.max else { continue }
            if Double(lowestDist) > cutoff { continue }
            distances.append(lowestDist)
        }
        return (distances.count, distances.reduce(0, +))
    }

    /// Returns `true` if at least `regionCutoff` segments match within the cutoff.
    ///
    /// Matches Python `ImageMultiHash.matches`.
    public func matches(
        _ other: ImageMultiHash,
        regionCutoff: Int = 1,
        hammingCutoff: Double? = nil,
        bitErrorRate: Double? = nil
    ) -> Bool {
        let (m, _) = hashDiff(other, hammingCutoff: hammingCutoff, bitErrorRate: bitErrorRate)
        return m >= regionCutoff
    }

    /// Returns the element of `others` with the smallest `subtract` distance to `self`.
    ///
    /// Matches Python `ImageMultiHash.best_match`.
    public func bestMatch(
        _ others: [ImageMultiHash],
        hammingCutoff: Double? = nil,
        bitErrorRate: Double? = nil
    ) -> ImageMultiHash {
        precondition(!others.isEmpty, "others must not be empty")
        return others.min { a, b in
            subtract(a, hammingCutoff: hammingCutoff, bitErrorRate: bitErrorRate)
            < subtract(b, hammingCutoff: hammingCutoff, bitErrorRate: bitErrorRate)
        }!
    }
}

/// Parses a comma-separated list of hex strings into an ImageMultiHash.
/// Each part is parsed via `hexToHash`.
public func hexToMultiHash(_ s: String) throws -> ImageMultiHash {
    let parts = s.split(separator: ",", omittingEmptySubsequences: false).map { String($0) }
    let hashes = try parts.map { try hexToHash($0) }
    return ImageMultiHash(segmentHashes: hashes)
}
