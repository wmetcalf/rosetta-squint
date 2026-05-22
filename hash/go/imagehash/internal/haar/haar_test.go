package haar_test

import (
	"encoding/json"
	"math"
	"os"
	"testing"

	"github.com/wmetcalf/rosetta-image-hash/go/imagehash/internal/haar"
	"github.com/wmetcalf/rosetta-image-hash/go/imagehash/internal/testkit"
)

const haarTol = 1e-12

type haarDoc struct {
	Input       [][]float64 `json:"input"`
	SingleLevel struct {
		CA [][]float64 `json:"cA"`
		CH [][]float64 `json:"cH"`
		CV [][]float64 `json:"cV"`
		CD [][]float64 `json:"cD"`
	} `json:"single_level"`
	MultiLevel4 struct {
		CA            [][]float64 `json:"cA"`
		Reconstructed [][]float64 `json:"reconstructed"`
	} `json:"multi_level_4"`
}

func loadHaarCases(t *testing.T) haarDoc {
	t.Helper()
	path := testkit.PathFromInternal("haar_cases.json")
	data, err := os.ReadFile(path)
	if err != nil {
		t.Fatalf("read %s: %v", path, err)
	}
	var doc haarDoc
	if err := json.Unmarshal(data, &doc); err != nil {
		t.Fatalf("parse: %v", err)
	}
	return doc
}

func assertClose(t *testing.T, expected, actual [][]float64, label string) {
	t.Helper()
	if len(expected) != len(actual) {
		t.Fatalf("%s: rows %d vs %d", label, len(expected), len(actual))
	}
	for y := range expected {
		if len(expected[y]) != len(actual[y]) {
			t.Fatalf("%s: cols at row %d: %d vs %d", label, y, len(expected[y]), len(actual[y]))
		}
		for x := range expected[y] {
			if math.Abs(expected[y][x]-actual[y][x]) > haarTol {
				t.Fatalf("%s (%d,%d): expected %g, got %g", label, y, x, expected[y][x], actual[y][x])
			}
		}
	}
}

func TestSingleLevelMatchesPywt(t *testing.T) {
	c := loadHaarCases(t)
	cA, cH, cV, cD := haar.Dwt2(c.Input)
	assertClose(t, c.SingleLevel.CA, cA, "cA")
	assertClose(t, c.SingleLevel.CH, cH, "cH")
	assertClose(t, c.SingleLevel.CV, cV, "cV")
	assertClose(t, c.SingleLevel.CD, cD, "cD")
}

func TestMultiLevelLLAndReconstruction(t *testing.T) {
	c := loadHaarCases(t)
	dec := haar.Wavedec2(c.Input, 4)
	if len(dec.CA) != 1 || len(dec.CA[0]) != 1 {
		t.Fatalf("deepest LL shape: got %dx%d, want 1x1", len(dec.CA), len(dec.CA[0]))
	}
	assertClose(t, c.MultiLevel4.CA, dec.CA, "multi cA")
	recon := haar.Waverec2(dec)
	assertClose(t, c.MultiLevel4.Reconstructed, recon, "reconstructed")
	assertClose(t, c.Input, recon, "round-trip == input")
}

func TestZeroLLOfFullDecompRemovesDC(t *testing.T) {
	x := make([][]float64, 4)
	for i := range x {
		x[i] = make([]float64, 4)
		for j := range x[i] {
			x[i][j] = 7.5
		}
	}
	dec := haar.Wavedec2(x, 2)
	dec.CA[0][0] = 0
	recon := haar.Waverec2(dec)
	for y := range recon {
		for xCol := range recon[y] {
			if math.Abs(recon[y][xCol]) > haarTol {
				t.Fatalf("expected 0 at (%d,%d), got %g", y, xCol, recon[y][xCol])
			}
		}
	}
}
