package pilmedianfilter_test

import (
	"encoding/json"
	"os"
	"testing"

	"github.com/wmetcalf/rosetta-squint/hash/go/imagehash/internal/pilmedianfilter"
	"github.com/wmetcalf/rosetta-squint/hash/go/imagehash/internal/testkit"
)

type medianCase struct {
	Name   string  `json:"name"`
	Shape  [2]int  `json:"shape"`
	Input  [][]int `json:"input"`
	Output [][]int `json:"output"`
}

type medianCases struct {
	Cases []medianCase `json:"cases"`
}

func TestMedianFilterCases(t *testing.T) {
	path := testkit.PathFromInternal("median_filter_cases.json")
	data, err := os.ReadFile(path)
	if err != nil {
		t.Fatalf("read %s: %v", path, err)
	}
	var mc medianCases
	if err := json.Unmarshal(data, &mc); err != nil {
		t.Fatalf("parse median_filter_cases.json: %v", err)
	}

	for _, c := range mc.Cases {
		c := c
		t.Run(c.Name, func(t *testing.T) {
			h := c.Shape[0]
			w := c.Shape[1]

			src := make([][]uint8, h)
			for y := 0; y < h; y++ {
				row := make([]uint8, w)
				for x := 0; x < w; x++ {
					row[x] = uint8(c.Input[y][x])
				}
				src[y] = row
			}

			result := pilmedianfilter.Filter(src)

			for y := 0; y < h; y++ {
				for x := 0; x < w; x++ {
					got := result[y][x]
					want := uint8(c.Output[y][x])
					if got != want {
						t.Errorf("case=%s y=%d x=%d: got %d, want %d", c.Name, y, x, got, want)
					}
				}
			}
		})
	}
}
