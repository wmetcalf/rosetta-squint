package io.rosetta.imagehash;

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
                int d = seg.subtract(other_seg);
                if (d < lowest) lowest = d;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ImageMultiHash other)) return false;
        return matches(other, 1, null, null);
    }

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
