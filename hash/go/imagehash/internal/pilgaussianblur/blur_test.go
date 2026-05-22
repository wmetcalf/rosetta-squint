package pilgaussianblur_test

import (
	"encoding/json"
	"os"
	"testing"

	"github.com/wmetcalf/rosetta-image-hash/go/imagehash/internal/pilgaussianblur"
	"github.com/wmetcalf/rosetta-image-hash/go/imagehash/internal/testkit"
)

type gaussianCase struct {
	Name   string    `json:"name"`
	Shape  [2]int    `json:"shape"`
	Input  [][]int   `json:"input"`
	Output [][]int   `json:"output"`
}

type gaussianCases struct {
	Cases []gaussianCase `json:"cases"`
}

func TestGaussianBlurCases(t *testing.T) {
	path := testkit.PathFromInternal("gaussian_blur_cases.json")
	data, err := os.ReadFile(path)
	if err != nil {
		t.Fatalf("read %s: %v", path, err)
	}
	var gc gaussianCases
	if err := json.Unmarshal(data, &gc); err != nil {
		t.Fatalf("parse gaussian_blur_cases.json: %v", err)
	}

	for _, c := range gc.Cases {
		c := c
		t.Run(c.Name, func(t *testing.T) {
			h := c.Shape[0]
			w := c.Shape[1]
			if len(c.Input) != h || len(c.Output) != h {
				t.Fatalf("case %s: shape mismatch", c.Name)
			}

			src := make([][]uint8, h)
			for y := 0; y < h; y++ {
				row := make([]uint8, w)
				for x := 0; x < w; x++ {
					row[x] = uint8(c.Input[y][x])
				}
				src[y] = row
			}

			result := pilgaussianblur.Blur(src)

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
