package dct_test

import (
	"encoding/json"
	"math"
	"os"
	"testing"

	"github.com/wmetcalf/rosetta-image-hash/go/imagehash/internal/dct"
	"github.com/wmetcalf/rosetta-image-hash/go/imagehash/internal/testkit"
)

const tol = 1e-9

func TestDCT1DMatchesScipy(t *testing.T) {
	path := testkit.PathFromInternal("dct_cases.json")
	data, err := os.ReadFile(path)
	if err != nil {
		t.Fatalf("read %s: %v", path, err)
	}
	var doc struct {
		N     int `json:"n"`
		Cases map[string]struct {
			Input  []float64 `json:"input"`
			Output []float64 `json:"output"`
		} `json:"cases"`
	}
	if err := json.Unmarshal(data, &doc); err != nil {
		t.Fatalf("parse: %v", err)
	}
	for name, c := range doc.Cases {
		name, c := name, c
		t.Run(name, func(t *testing.T) {
			got := dct.DCT1D(c.Input)
			if len(got) != doc.N {
				t.Fatalf("length: got %d, want %d", len(got), doc.N)
			}
			for k := range got {
				if math.Abs(got[k]-c.Output[k]) > tol {
					t.Fatalf("%s k=%d: got %g, want %g", name, k, got[k], c.Output[k])
				}
			}
		})
	}
}

func TestArangeFirstOutputIs992(t *testing.T) {
	x := make([]float64, 32)
	for i := range x {
		x[i] = float64(i)
	}
	y := dct.DCT1D(x)
	if math.Abs(y[0]-992.0) > 1e-9 {
		t.Fatalf("y[0]: got %g, want 992.0", y[0])
	}
}
