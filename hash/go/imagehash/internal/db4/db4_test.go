package db4_test

import (
	"encoding/json"
	"math"
	"os"
	"testing"

	"github.com/wmetcalf/rosetta-image-hash/go/imagehash/internal/db4"
	"github.com/wmetcalf/rosetta-image-hash/go/imagehash/internal/testkit"
)

const db4Tol = 1e-10

// ---- JSON schema for db4_cases.json ----

type singleLevelCase struct {
	Input       [][]float64 `json:"input"`
	CA          [][]float64 `json:"cA"`
	CH          [][]float64 `json:"cH"`
	CV          [][]float64 `json:"cV"`
	CD          [][]float64 `json:"cD"`
	IdwtRT      [][]float64 `json:"idwt_roundtrip"`
}

type multiLevelCase struct {
	Input      [][]float64 `json:"input"`
	Level      int         `json:"level"`
	CA         [][]float64 `json:"cA"`
	ReconFull  [][]float64 `json:"recon_full"`
	ReconZeroLL [][]float64 `json:"recon_zero_ll"`
}

type db4Doc struct {
	SingleLevelCases map[string]singleLevelCase `json:"single_level_cases"`
	MultiLevelCases  map[string]multiLevelCase  `json:"multi_level_cases"`
}

func loadDB4Cases(t *testing.T) db4Doc {
	t.Helper()
	path := testkit.PathFromInternal("db4_cases.json")
	data, err := os.ReadFile(path)
	if err != nil {
		t.Fatalf("read %s: %v", path, err)
	}
	var doc db4Doc
	if err := json.Unmarshal(data, &doc); err != nil {
		t.Fatalf("parse: %v", err)
	}
	return doc
}

func assertClose2D(t *testing.T, expected, actual [][]float64, label string) {
	t.Helper()
	if len(expected) != len(actual) {
		t.Fatalf("%s: row count expected %d, got %d", label, len(expected), len(actual))
	}
	for y := range expected {
		if len(expected[y]) != len(actual[y]) {
			t.Fatalf("%s row %d: col count expected %d, got %d",
				label, y, len(expected[y]), len(actual[y]))
		}
		for x := range expected[y] {
			diff := math.Abs(expected[y][x] - actual[y][x])
			if diff > db4Tol {
				t.Errorf("%s (%d,%d): expected %.15g, got %.15g (diff %.3e)",
					label, y, x, expected[y][x], actual[y][x], diff)
			}
		}
	}
}

// TestSingleLevelMatchesPywt verifies dwt2 against PyWavelets reference values.
func TestSingleLevelMatchesPywt(t *testing.T) {
	doc := loadDB4Cases(t)
	for name, c := range doc.SingleLevelCases {
		c := c
		t.Run(name, func(t *testing.T) {
			cA, cH, cV, cD := db4.Dwt2(c.Input)
			assertClose2D(t, c.CA, cA, "cA")
			assertClose2D(t, c.CH, cH, "cH")
			assertClose2D(t, c.CV, cV, "cV")
			assertClose2D(t, c.CD, cD, "cD")
		})
	}
}

// TestIdwtRoundtripMatchesPywt verifies idwt2 round-trip against PyWavelets.
func TestIdwtRoundtripMatchesPywt(t *testing.T) {
	doc := loadDB4Cases(t)
	for name, c := range doc.SingleLevelCases {
		c := c
		t.Run(name, func(t *testing.T) {
			cA, cH, cV, cD := db4.Dwt2(c.Input)
			targetH := len(c.Input)
			targetW := len(c.Input[0])
			got := db4.Idwt2(cA, cH, cV, cD, targetH, targetW)
			// Compare against pywt reference round-trip (skip if not provided).
			if len(c.IdwtRT) > 0 {
				assertClose2D(t, c.IdwtRT, got, "idwt_roundtrip")
			}
			// Also compare against original input.
			assertClose2D(t, c.Input, got, "round_trip_equals_input")
		})
	}
}

// TestMultiLevelMatchesPywt verifies wavedec2 / waverec2 against PyWavelets.
func TestMultiLevelMatchesPywt(t *testing.T) {
	doc := loadDB4Cases(t)
	for name, c := range doc.MultiLevelCases {
		c := c
		t.Run(name, func(t *testing.T) {
			res := db4.Wavedec2(c.Input, c.Level)

			// Deepest LL band.
			assertClose2D(t, c.CA, res.CA, "cA")

			// Full reconstruction.
			recon := db4.Waverec2(res)
			assertClose2D(t, c.ReconFull, recon, "recon_full")
			// Must also equal original input.
			assertClose2D(t, c.Input, recon, "recon_equals_input")

			// Zero LL then reconstruct.
			for y := range res.CA {
				for x := range res.CA[y] {
					res.CA[y][x] = 0
				}
			}
			reconZero := db4.Waverec2(res)
			assertClose2D(t, c.ReconZeroLL, reconZero, "recon_zero_ll")
		})
	}
}
