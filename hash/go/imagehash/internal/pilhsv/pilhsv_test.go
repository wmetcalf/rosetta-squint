package pilhsv_test

import (
	"encoding/json"
	"os"
	"testing"

	"github.com/wmetcalf/rosetta-image-hash/go/imagehash/internal/pilhsv"
	"github.com/wmetcalf/rosetta-image-hash/go/imagehash/internal/testkit"
)

func TestAllCasesMatchSpec(t *testing.T) {
	path := testkit.PathFromInternal("hsv_cases.json")
	data, err := os.ReadFile(path)
	if err != nil {
		t.Fatalf("read %s: %v", path, err)
	}
	var doc struct {
		Cases []struct {
			RGB [3]int `json:"rgb"`
			HSV [3]int `json:"hsv"`
		} `json:"cases"`
	}
	if err := json.Unmarshal(data, &doc); err != nil {
		t.Fatalf("parse: %v", err)
	}
	if len(doc.Cases) != 31 {
		t.Fatalf("expected 31 cases, got %d", len(doc.Cases))
	}
	for _, c := range doc.Cases {
		h, s, v := pilhsv.ToHSV(uint8(c.RGB[0]), uint8(c.RGB[1]), uint8(c.RGB[2]))
		if int(h) != c.HSV[0] || int(s) != c.HSV[1] || int(v) != c.HSV[2] {
			t.Errorf("RGB(%d,%d,%d): got (%d,%d,%d), want (%d,%d,%d)",
				c.RGB[0], c.RGB[1], c.RGB[2], h, s, v, c.HSV[0], c.HSV[1], c.HSV[2])
		}
	}
}

func TestNegativeHPreWrap(t *testing.T) {
	h, s, v := pilhsv.ToHSV(200, 100, 150)
	if h != 233 || s != 127 || v != 200 {
		t.Fatalf("RGB(200,100,150): got (%d,%d,%d), want (233,127,200)", h, s, v)
	}
}

func TestHalfBoundaryFloorNotRound(t *testing.T) {
	h, s, v := pilhsv.ToHSV(100, 150, 200)
	if h != 148 || s != 127 || v != 200 {
		t.Fatalf("RGB(100,150,200): got (%d,%d,%d), want (148,127,200)", h, s, v)
	}
}

func TestSaturation170Boundary(t *testing.T) {
	_, s, _ := pilhsv.ToHSV(255, 85, 85)
	if s != 170 {
		t.Fatalf("RGB(255,85,85): got S=%d, want 170", s)
	}
}
