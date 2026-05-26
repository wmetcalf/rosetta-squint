package io.github.wmetcalf.rosettasquint.hash;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Container for a crop-resistant image hash: an ordered list of ImageHash segments.
 *
 * Mirrors Python imagehash.ImageMultiHash semantics exactly:
 * - {@link #subtract} returns a float (not int).
 * - {@link #hashDiff} returns (matches, sumDistance).
 * - {@link #matches} uses the default bit_error_rate=0.25 if no hamming cutoff given.
 * - {@link #toString} is comma-separated hex of each segment hash.
 *
 * <h2>WARNING: equals() is NOT reflexive/symmetric/transitive — DO NOT use as a hash key</h2>
 *
 * <p>This class inherits Python's ImageMultiHash semantics where {@code equals(other)}
 * is defined as a <em>similarity</em> check: {@code matches(other, regionCutoff=1)}.
 * That means:
 *
 * <ul>
 *   <li>{@code a.equals(b)} can be true while {@code b.equals(a)} is false
 *       (the underlying {@link #hashDiff} loops over self's segments, not other's).</li>
 *   <li>{@code a.equals(b) && b.equals(c)} does NOT imply {@code a.equals(c)}
 *       (transitivity is broken by a Hamming-cutoff matching algorithm).</li>
 *   <li>The {@link Object#hashCode} / {@link Object#equals} contract is therefore
 *       violated — two "equal" instances may produce different hashCodes, and the
 *       same instance compared to two near-matches may produce inconsistent results.</li>
 * </ul>
 *
 * <p>Consequences:
 *
 * <ul>
 *   <li><b>DO NOT</b> use {@code ImageMultiHash} as a key in {@link java.util.HashMap},
 *       {@link java.util.HashSet}, {@link java.util.LinkedHashMap}, or any other hash-based
 *       collection. Lookups will silently miss matches.</li>
 *   <li>For exact-segment-by-segment equality (which DOES obey the equals contract),
 *       compare {@link #segmentHashes()} lists directly:
 *       {@code a.segmentHashes().equals(b.segmentHashes())}.</li>
 *   <li>For similarity, prefer {@link #matches(ImageMultiHash, int)} or
 *       {@link #subtract(ImageMultiHash)} directly — they are explicit about the
 *       intent and don't pretend to satisfy the equality contract.</li>
 * </ul>
 *
 * <p>This is preserved (rather than corrected to value-equality) to maintain
 * byte-exact behavioral parity with the Python {@code imagehash.ImageMultiHash}
 * reference implementation.
 */
public final class ImageMultiHash {

    private final List<ImageHash> segmentHashes;

    public ImageMultiHash(List<ImageHash> hashes) {
        Objects.requireNonNull(hashes, "hashes");
        if (hashes.isEmpty())
            throw new IllegalArgumentException("hashes must be non-empty");
        this.segmentHashes = List.copyOf(hashes);
    }

    public List<ImageHash> segmentHashes() {
        return segmentHashes;
    }

    /**
     * Float distance between two multi-hashes.
     * Formula from Python __sub__ (hamming_cutoff=null, bit_error_rate=null):
     *   matches, sumDistance = hash_diff(other)
     *   maxDifference = len(self.segment_hashes)
     *   if matches == 0: return maxDifference
     *   maxDistance = matches * len(self.segment_hashes[0])
     *   tieBreaker = -(sumDistance / maxDistance)
     *   matchScore = matches + tieBreaker
     *   return maxDifference - matchScore
     */
    public double subtract(ImageMultiHash other) {
        return subtract(other, null, null);
    }

    /** subtract with explicit hamming/bit-error-rate cutoff (mirrors Python __sub__ signature). */
    public double subtract(ImageMultiHash other, Double hammingCutoff, Double bitErrorRate) {
        Objects.requireNonNull(other, "other");
        int[] diff = hashDiff(other, hammingCutoff, bitErrorRate);
        int matches = diff[0];
        int sumDistance = diff[1];
        int maxDifference = segmentHashes.size();
        if (matches == 0) return maxDifference;
        int maxDistance = matches * segmentHashes.get(0).bitCount();
        double tieBreaker = 0.0 - ((double) sumDistance / maxDistance);
        double matchScore = matches + tieBreaker;
        return maxDifference - matchScore;
    }

    /**
     * Returns int[]{matches, sumDistance}.
     * For each segment hash in self, find the minimum hamming distance to any segment
     * in other; if that minimum is within the cutoff, count it as a match.
     *
     * Default behavior (both null): bitErrorRate = 0.25, hammingCutoff = bitCount * 0.25.
     */
    public int[] hashDiff(ImageMultiHash other, Double hammingCutoff, Double bitErrorRate) {
        Objects.requireNonNull(other, "other");
        double cutoff;
        if (hammingCutoff != null) {
            cutoff = hammingCutoff;
        } else {
            double ber = (bitErrorRate != null) ? bitErrorRate : 0.25;
            cutoff = segmentHashes.get(0).bitCount() * ber;
        }

        int matches = 0;
        int sumDistance = 0;
        for (ImageHash seg : segmentHashes) {
            int lowest = Integer.MAX_VALUE;
            for (ImageHash other_seg : other.segmentHashes) {
                // Mirrors Go's filter_map / Swift's try? semantics: shape-mismatch
                // segment pairs are silently skipped rather than aborting the entire
                // comparison mid-iteration. This keeps two multi-hashes that share a
                // few matching segments comparable even when their segment shapes
                // diverge (e.g. one was loaded from a hex string and the other built
                // from a fresh crop_resistant_hash call with a different inner size).
                try {
                    int d = seg.subtract(other_seg);
                    if (d < lowest) lowest = d;
                } catch (IllegalArgumentException e) {
                    continue;
                }
            }
            if (lowest <= cutoff) {
                matches++;
                sumDistance += lowest;
            }
        }
        return new int[]{matches, sumDistance};
    }

    /** Convenience overload with null cutoffs (uses default 0.25 BER). */
    public int[] hashDiff(ImageMultiHash other) {
        return hashDiff(other, null, null);
    }

    /**
     * Returns true if at least regionCutoff segments match within the hamming cutoff.
     */
    public boolean matches(ImageMultiHash other, int regionCutoff) {
        return matches(other, regionCutoff, null, null);
    }

    public boolean matches(ImageMultiHash other, int regionCutoff,
                           Double hammingCutoff, Double bitErrorRate) {
        Objects.requireNonNull(other, "other");
        int[] diff = hashDiff(other, hammingCutoff, bitErrorRate);
        return diff[0] >= regionCutoff;
    }

    /**
     * Returns the element of others with the smallest subtract() distance from this.
     */
    public ImageMultiHash bestMatch(List<ImageMultiHash> others) {
        return bestMatch(others, null, null);
    }

    public ImageMultiHash bestMatch(List<ImageMultiHash> others,
                                    Double hammingCutoff, Double bitErrorRate) {
        Objects.requireNonNull(others, "others");
        if (others.isEmpty()) throw new IllegalArgumentException("others must be non-empty");
        ImageMultiHash best = null;
        double bestScore = Double.MAX_VALUE;
        for (ImageMultiHash o : others) {
            double score = subtract(o, hammingCutoff, bitErrorRate);
            if (score < bestScore) {
                bestScore = score;
                best = o;
            }
        }
        return best;
    }

    /**
     * Comma-separated hex of each segment hash, e.g. "aabb,ccdd,eeff".
     */
    @Override
    public String toString() {
        return segmentHashes.stream()
                .map(ImageHash::toString)
                .collect(Collectors.joining(","));
    }

    /**
     * Similarity-based equality (NON-SYMMETRIC, NON-TRANSITIVE).
     *
     * <p>Mirrors Python {@code ImageMultiHash.__eq__}: returns
     * {@code matches(other, regionCutoff=1, hammingCutoff=null, bitErrorRate=null)}.
     *
     * <p><b>This violates the {@link Object#equals(Object)} contract</b>:
     *
     * <ul>
     *   <li><b>Not symmetric:</b> {@code a.equals(b)} may differ from {@code b.equals(a)}
     *       because {@link #hashDiff} loops over {@code this.segmentHashes}, not other's.</li>
     *   <li><b>Not transitive:</b> Hamming-cutoff matching cannot satisfy transitivity.</li>
     *   <li><b>Inconsistent with {@link #hashCode()}:</b> two "equal" multi-hashes may
     *       produce different hashCodes (since hashCode is per-segment exact).</li>
     * </ul>
     *
     * <p><b>DO NOT use ImageMultiHash as a key in HashMap/HashSet</b> — see class-level
     * Javadoc. Use {@link #matches(ImageMultiHash, int)} or {@link #subtract(ImageMultiHash)}
     * directly for similarity, or compare {@link #segmentHashes()} lists for exact equality.
     *
     * <p>Preserved as-is to match Python {@code imagehash.ImageMultiHash} semantics
     * byte-for-byte.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ImageMultiHash other)) return false;
        return matches(other, 1, null, null);
    }

    /**
     * Hash code based on the exact per-segment hashCodes of {@link #segmentHashes()}.
     *
     * <p><b>Inconsistent with {@link #equals(Object)}:</b> {@code equals} is a similarity
     * check, but {@code hashCode} is exact-per-segment. Two instances that satisfy
     * {@code a.equals(b)} may produce different {@code hashCode} values.
     *
     * <p>This is why {@code ImageMultiHash} <b>must not</b> be used as a key in
     * hash-based collections — see class-level Javadoc.
     */
    @Override
    public int hashCode() {
        return segmentHashes.stream()
                .mapToInt(ImageHash::hashCode)
                .reduce(1, (acc, h) -> 31 * acc + h);
    }

    /**
     * Parse a comma-separated hex string into an ImageMultiHash.
     * Each token is parsed via Hex.hexToHash (square hash).
     */
    public static ImageMultiHash fromHex(String hex) {
        Objects.requireNonNull(hex, "hex");
        String[] tokens = hex.split(",", -1);
        List<ImageHash> hashes = new java.util.ArrayList<>(tokens.length);
        for (String token : tokens) {
            hashes.add(Hex.hexToHash(token));
        }
        return new ImageMultiHash(hashes);
    }
}
