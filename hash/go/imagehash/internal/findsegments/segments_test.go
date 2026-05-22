package findsegments_test

import (
	"encoding/json"
	"os"
	"sort"
	"testing"

	"github.com/wmetcalf/rosetta-image-hash/go/imagehash/internal/findsegments"
	"github.com/wmetcalf/rosetta-image-hash/go/imagehash/internal/testkit"
)

type segCase struct {
	Name           string      `json:"name"`
	Shape          [2]int      `json:"shape"`
	Input          [][]float64 `json:"input"`
	SegThreshold   float64     `json:"segment_threshold"`
	MinSegmentSize int         `json:"min_segment_size"`
	NumSegments    int         `json:"num_segments"`
	Segments       [][][2]int  `json:"segments"`
}

type segCases struct {
	Cases []segCase `json:"cases"`
}

func TestFindAllSegmentsCases(t *testing.T) {
	path := testkit.PathFromInternal("segmentation_cases.json")
	data, err := os.ReadFile(path)
	if err != nil {
		t.Fatalf("read %s: %v", path, err)
	}
	var sc segCases
	if err := json.Unmarshal(data, &sc); err != nil {
		t.Fatalf("parse segmentation_cases.json: %v", err)
	}

	for _, c := range sc.Cases {
		c := c
		t.Run(c.Name, func(t *testing.T) {
			h := c.Shape[0]
			w := c.Shape[1]

			pixels := make([][]float32, h)
			for y := 0; y < h; y++ {
				row := make([]float32, w)
				for x := 0; x < w; x++ {
					row[x] = float32(c.Input[y][x])
				}
				pixels[y] = row
			}

			segs := findsegments.FindAllSegments(pixels, float32(c.SegThreshold), c.MinSegmentSize)

			if len(segs) != c.NumSegments {
				t.Errorf("case=%s: got %d segments, want %d", c.Name, len(segs), c.NumSegments)
				return
			}

			for i, seg := range segs {
				expected := c.Segments[i]
				if len(seg) != len(expected) {
					t.Errorf("case=%s seg=%d: got %d pixels, want %d", c.Name, i, len(seg), len(expected))
					continue
				}

				// Convert to sorted set for comparison.
				type yx struct{ y, x int }
				gotSet := make(map[yx]bool, len(seg))
				for _, p := range seg {
					gotSet[yx{p.Y, p.X}] = true
				}
				wantSet := make(map[yx]bool, len(expected))
				for _, p := range expected {
					wantSet[yx{p[0], p[1]}] = true
				}

				if len(gotSet) != len(wantSet) {
					t.Errorf("case=%s seg=%d: pixel set size mismatch", c.Name, i)
					continue
				}
				for p := range wantSet {
					if !gotSet[p] {
						t.Errorf("case=%s seg=%d: missing pixel (%d,%d)", c.Name, i, p.y, p.x)
					}
				}
			}

			// Also verify segment ordering: each segment's first pixel (row-major min)
			// should match the expected ordering.
			for i, seg := range segs {
				// Find min pixel (row-major) in segment.
				minP := findsegments.Pixel{Y: seg[0].Y, X: seg[0].X}
				for _, p := range seg {
					if p.Y < minP.Y || (p.Y == minP.Y && p.X < minP.X) {
						minP = p
					}
				}
				expected := c.Segments[i]
				_ = sort.Search // suppress import
				// Expected min pixel is the first entry in the sorted expected list.
				wantMin := [2]int{expected[0][0], expected[0][1]}
				// Find actual min of expected.
				for _, p := range expected {
					if p[0] < wantMin[0] || (p[0] == wantMin[0] && p[1] < wantMin[1]) {
						wantMin = [2]int{p[0], p[1]}
					}
				}
				if minP.Y != wantMin[0] || minP.X != wantMin[1] {
					t.Errorf("case=%s seg=%d: min pixel (%d,%d), want (%d,%d)",
						c.Name, i, minP.Y, minP.X, wantMin[0], wantMin[1])
				}
			}
		})
	}
}
