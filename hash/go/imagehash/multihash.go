package imagehash

import (
	"fmt"
	"strings"
)

// ImageMultiHash represents a crop-resistant hash containing a list of
// per-segment Hash values. Implements the distance semantics from
// "Efficient Cropping-Resistant Robust Image Hashing" (DOI 10.1109/ARES.2014.85).
type ImageMultiHash struct {
	SegmentHashes []Hash
}

// Subtract returns the distance between two ImageMultiHash values as a float64.
// Distance is computed as: max_difference - match_score, where match_score
// accounts for matched segments and their Hamming distances.
func (m ImageMultiHash) Subtract(other ImageMultiHash) float64 {
	matches, sumDistance := m.HashDiff(other, nil, nil)
	maxDifference := float64(len(m.SegmentHashes))
	if matches == 0 {
		return maxDifference
	}
	maxDistance := matches * m.SegmentHashes[0].BitCount()
	tieBreaker := -float64(sumDistance) / float64(maxDistance)
	matchScore := float64(matches) + tieBreaker
	return maxDifference - matchScore
}

// HashDiff computes the number of matching segments and the sum of Hamming
// distances for matching segments. A segment matches if its best Hamming
// distance to any segment in other is <= hammingCutoff.
//
// If hammingCutoff is nil: uses bitErrorRate (default 0.25) * len(segment).
// If bitErrorRate is also nil: defaults to 0.25.
func (m ImageMultiHash) HashDiff(other ImageMultiHash, hammingCutoff *float64, bitErrorRate *float64) (matches int, sumDistance int) {
	if len(m.SegmentHashes) == 0 {
		return 0, 0
	}

	// Compute hamming cutoff.
	var cutoff float64
	if hammingCutoff != nil {
		cutoff = *hammingCutoff
	} else {
		ber := 0.25
		if bitErrorRate != nil {
			ber = *bitErrorRate
		}
		cutoff = float64(m.SegmentHashes[0].BitCount()) * ber
	}

	for _, sh := range m.SegmentHashes {
		lowest := -1
		for _, oh := range other.SegmentHashes {
			d, err := sh.Subtract(oh)
			if err != nil {
				continue
			}
			if lowest < 0 || d < lowest {
				lowest = d
			}
		}
		if lowest < 0 {
			continue
		}
		if float64(lowest) <= cutoff {
			matches++
			sumDistance += lowest
		}
	}
	return matches, sumDistance
}

// Matches returns true if at least regionCutoff segments match between m and other.
func (m ImageMultiHash) Matches(other ImageMultiHash, regionCutoff int) bool {
	matches, _ := m.HashDiff(other, nil, nil)
	return matches >= regionCutoff
}

// BestMatch returns the ImageMultiHash from others that minimizes Subtract
// distance to m. Returns an error (rather than panicking) if others is empty.
//
// Signature note: this returns (ImageMultiHash, error) rather than just
// ImageMultiHash so callers can handle empty-input as a recoverable error
// instead of a process-aborting panic.
func (m ImageMultiHash) BestMatch(others []ImageMultiHash) (ImageMultiHash, error) {
	if len(others) == 0 {
		return ImageMultiHash{}, fmt.Errorf("BestMatch: others must be non-empty")
	}
	best := others[0]
	bestDist := m.Subtract(others[0])
	for _, o := range others[1:] {
		d := m.Subtract(o)
		if d < bestDist {
			bestDist = d
			best = o
		}
	}
	return best, nil
}

// ToHex returns the comma-separated hex string of all segment hashes.
func (m ImageMultiHash) ToHex() string {
	parts := make([]string, len(m.SegmentHashes))
	for i, h := range m.SegmentHashes {
		parts[i] = h.ToHex()
	}
	return strings.Join(parts, ",")
}

// String returns the same as ToHex.
func (m ImageMultiHash) String() string {
	return m.ToHex()
}

// HexToMultiHash parses a comma-separated hex string into an ImageMultiHash.
// Each comma-separated part is parsed via HexToHash (square hash_size × hash_size).
func HexToMultiHash(s string) (ImageMultiHash, error) {
	parts := strings.Split(s, ",")
	out := make([]Hash, len(parts))
	for i, p := range parts {
		h, err := HexToHash(p)
		if err != nil {
			return ImageMultiHash{}, fmt.Errorf("part %d: %w", i, err)
		}
		out[i] = h
	}
	return ImageMultiHash{SegmentHashes: out}, nil
}
