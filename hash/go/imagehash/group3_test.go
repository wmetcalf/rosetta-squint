package imagehash_test

import (
	"bufio"
	"fmt"
	"image"
	_ "image/png"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"github.com/wmetcalf/rosetta-image-hash/go/imagehash"
	"github.com/wmetcalf/rosetta-image-hash/go/imagehash/internal/testkit"
)

func loadExemptions(t *testing.T) map[string]struct{} {
	t.Helper()
	exempt := map[string]struct{}{}
	f, err := os.Open("DECODER_NOTES.md")
	if err != nil {
		return exempt
	}
	defer f.Close()
	s := bufio.NewScanner(f)
	for s.Scan() {
		line := s.Text()
		if idx := strings.Index(line, "—"); idx > 0 {
			name := strings.TrimSpace(line[:idx])
			if strings.HasSuffix(name, ".png") {
				exempt[name] = struct{}{}
			}
		}
	}
	return exempt
}

func decodePNG(t *testing.T, fixture string) image.Image {
	t.Helper()
	path := filepath.Join(testkit.DirRoot(), "fixtures", fixture)
	f, err := os.Open(path)
	if err != nil {
		t.Fatalf("open %s: %v", path, err)
	}
	defer f.Close()
	img, _, err := image.Decode(f)
	if err != nil {
		t.Fatalf("decode %s: %v", path, err)
	}
	return img
}

func runEndToEnd(t *testing.T, algoName string, exempt map[string]struct{}, compute func(image.Image, int) (imagehash.Hash, error)) {
	cases, err := testkit.AlgorithmCasesFromRoot(algoName)
	if err != nil {
		t.Fatalf("load goldens: %v", err)
	}
	for _, c := range cases {
		c := c
		t.Run(fmt.Sprintf("%s/%s-size-%d", algoName, c.Fixture, c.Size), func(t *testing.T) {
			if _, ok := exempt[c.Fixture]; ok {
				t.Skipf("Group-3 exempt per DECODER_NOTES.md: %s", c.Fixture)
			}
			img := decodePNG(t, c.Fixture)
			h, err := compute(img, c.Size)
			if err != nil {
				t.Fatalf("%s: %v", algoName, err)
			}
			if got := h.ToHex(); got != c.Hex {
				t.Errorf("%s end-to-end PNG: fixture=%s size=%d: got %q, want %q", algoName, c.Fixture, c.Size, got, c.Hex)
			}
		})
	}
}

func TestPNGEndToEnd(t *testing.T) {
	exempt := loadExemptions(t)
	runEndToEnd(t, "average_hash", exempt, imagehash.AverageHash)
	runEndToEnd(t, "dhash", exempt, imagehash.DHash)
	runEndToEnd(t, "phash", exempt, imagehash.PHash)
	runEndToEnd(t, "whash_haar", exempt, imagehash.WHashHaar)
	runEndToEnd(t, "colorhash", exempt, imagehash.ColorHash)
}
