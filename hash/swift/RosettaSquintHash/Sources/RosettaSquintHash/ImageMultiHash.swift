import Foundation

/// Multi-segment image hash for crop-resistant hashing.
///
/// Matches Python `imagehash.ImageMultiHash`. The distance metric returns a Double
/// (not Int) — callers must not assume an integer result.
///
/// ## WARNING: `==` is NOT reflexive / symmetric / transitive — DO NOT use as a Dictionary key
///
/// `ImageMultiHash` mirrors Python's `__eq__`, which calls `matches(other, regionCutoff=1)`.
/// That is a *similarity* check (Hamming cutoff based), not value equality:
///
/// - `a == b` may differ from `b == a` (asymmetric: `hashDiff` loops over `self`).
/// - `(a == b) && (b == c)` does NOT imply `a == c` (similarity is not transitive).
///
/// Because Swift's `Hashable` contract requires that `a == b` implies
/// `a.hashValue == b.hashValue`, and this contract cannot be satisfied with
/// similarity-based equality, **`ImageMultiHash` deliberately does NOT conform to
/// `Hashable`**. Do not place it in a `Set<ImageMultiHash>` or use it as a
/// `Dictionary` key — neither would behave correctly.
///
/// For exact, segment-by-segment equality (which DOES obey contracts), compare
/// `segmentHashes` arrays directly:
///
///     let exactlyEqual = a.segmentHashes == b.segmentHashes
///
/// For similarity, use `matches(_:regionCutoff:)` or `subtract(_:)` directly.
///
/// This behaviour is preserved (rather than corrected to value-equality) to
/// maintain byte-exact parity with the Python `imagehash.ImageMultiHash` reference.
public struct ImageMultiHash: Equatable, CustomStringConvertible {
    public let segmentHashes: [Hash]

    /// Throws `ImageHashError.shapeMismatch` if `segmentHashes` is empty —
    /// matches Java's existing guard so all 5 ports reject empty multi-hashes
    /// at construction time rather than failing later inside `hashDiff`
    /// (which would dereference `segmentHashes[0]`).
    public init(segmentHashes: [Hash]) throws {
        guard !segmentHashes.isEmpty else {
            throw ImageHashError.shapeMismatch(
                lhs: ImageHashError.ShapeKey(0, 0),
                rhs: ImageHashError.ShapeKey(0, 0)
            )
        }
        self.segmentHashes = segmentHashes
    }

    // MARK: - Stringification

    /// Comma-separated hex strings of each segment hash.
    public var description: String {
        segmentHashes.map { String(describing: $0) }.joined(separator: ",")
    }

    // MARK: - Equatable

    /// Similarity-based equality — **NOT reflexive/symmetric/transitive**.
    ///
    /// Mirrors Python `ImageMultiHash.__eq__`: returns
    /// `lhs.matches(rhs, regionCutoff: 1)`.
    ///
    /// Because Hamming-cutoff matching cannot satisfy the `Hashable` contract,
    /// this type intentionally does NOT conform to `Hashable`. See type-level
    /// documentation for details and alternatives.
    public static func == (lhs: ImageMultiHash, rhs: ImageMultiHash) -> Bool {
        lhs.matches(rhs)
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
    /// Throws `ImageHashError.shapeMismatch` if `others` is empty (rather than
    /// hitting a `precondition` failure, which is unrecoverable).
    ///
    /// Matches Python `ImageMultiHash.best_match` semantically; the throw on empty
    /// input is a deliberate signature change from earlier versions of this port.
    public func bestMatch(
        _ others: [ImageMultiHash],
        hammingCutoff: Double? = nil,
        bitErrorRate: Double? = nil
    ) throws -> ImageMultiHash {
        guard !others.isEmpty else {
            // Re-use the existing shapeMismatch error case (0,0 lhs encodes "empty").
            throw ImageHashError.shapeMismatch(
                lhs: ImageHashError.ShapeKey(0, 0),
                rhs: ImageHashError.ShapeKey(others.count, 0)
            )
        }
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
    return try ImageMultiHash(segmentHashes: hashes)
}
