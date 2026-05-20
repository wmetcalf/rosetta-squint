package imagehash_test

import (
	"fmt"
	"testing"

	"github.com/wmetcalf/rosetta-image-hash/go/imagehash"
	"github.com/wmetcalf/rosetta-image-hash/go/imagehash/internal/testkit"
)

func TestAverageHashGoldens(t *testing.T) {
	cases, err := testkit.AlgorithmCasesFromRoot("average_hash")
	if err != nil {
		t.Fatalf("load goldens: %v", err)
	}
	for _, c := range cases {
		c := c
		t.Run(fmt.Sprintf("%s-size-%d", c.Fixture, c.Size), func(t *testing.T) {
			img := testkit.LoadPreDecodedFromRoot(t, c.Fixture)
			h, err := imagehash.AverageHash(img, c.Size)
			if err != nil {
				t.Fatalf("AverageHash: %v", err)
			}
			if got := h.ToHex(); got != c.Hex {
				t.Errorf("fixture=%s size=%d: got %q, want %q", c.Fixture, c.Size, got, c.Hex)
			}
		})
	}
}

func TestDHashGoldens(t *testing.T) {
	cases, err := testkit.AlgorithmCasesFromRoot("dhash")
	if err != nil {
		t.Fatalf("load goldens: %v", err)
	}
	for _, c := range cases {
		c := c
		t.Run(fmt.Sprintf("%s-size-%d", c.Fixture, c.Size), func(t *testing.T) {
			img := testkit.LoadPreDecodedFromRoot(t, c.Fixture)
			h, err := imagehash.DHash(img, c.Size)
			if err != nil {
				t.Fatalf("DHash: %v", err)
			}
			if got := h.ToHex(); got != c.Hex {
				t.Errorf("fixture=%s size=%d: got %q, want %q", c.Fixture, c.Size, got, c.Hex)
			}
		})
	}
}

func TestPHashGoldens(t *testing.T) {
	cases, err := testkit.AlgorithmCasesFromRoot("phash")
	if err != nil {
		t.Fatalf("load goldens: %v", err)
	}
	for _, c := range cases {
		c := c
		t.Run(fmt.Sprintf("%s-size-%d", c.Fixture, c.Size), func(t *testing.T) {
			img := testkit.LoadPreDecodedFromRoot(t, c.Fixture)
			h, err := imagehash.PHash(img, c.Size)
			if err != nil {
				t.Fatalf("PHash: %v", err)
			}
			if got := h.ToHex(); got != c.Hex {
				t.Errorf("fixture=%s size=%d: got %q, want %q", c.Fixture, c.Size, got, c.Hex)
			}
		})
	}
}

func TestWHashHaarGoldens(t *testing.T) {
	cases, err := testkit.AlgorithmCasesFromRoot("whash_haar")
	if err != nil {
		t.Fatalf("load goldens: %v", err)
	}
	for _, c := range cases {
		c := c
		t.Run(fmt.Sprintf("%s-size-%d", c.Fixture, c.Size), func(t *testing.T) {
			img := testkit.LoadPreDecodedFromRoot(t, c.Fixture)
			h, err := imagehash.WHashHaar(img, c.Size)
			if err != nil {
				t.Fatalf("WHashHaar: %v", err)
			}
			if got := h.ToHex(); got != c.Hex {
				t.Errorf("fixture=%s size=%d: got %q, want %q", c.Fixture, c.Size, got, c.Hex)
			}
		})
	}
}
