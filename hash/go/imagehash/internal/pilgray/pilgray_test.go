package pilgray_test

import (
	"encoding/json"
	"os"
	"testing"

	"github.com/wmetcalf/rosetta-squint/hash/go/imagehash/internal/pilgray"
	"github.com/wmetcalf/rosetta-squint/hash/go/imagehash/internal/testkit"
)

func TestAllCasesMatchSpec(t *testing.T) {
	path := testkit.PathFromInternal("grayscale_cases.json")
	data, err := os.ReadFile(path)
	if err != nil {
		t.Fatalf("read %s: %v", path, err)
	}
	var doc struct {
		Cases []struct {
			RGB [3]int `json:"rgb"`
			L   int    `json:"L"`
		} `json:"cases"`
	}
	if err := json.Unmarshal(data, &doc); err != nil {
		t.Fatalf("parse: %v", err)
	}
	if len(doc.Cases) != 30 {
		t.Fatalf("expected 30 cases, got %d", len(doc.Cases))
	}
	for _, c := range doc.Cases {
		got := int(pilgray.ToGray(uint8(c.RGB[0]), uint8(c.RGB[1]), uint8(c.RGB[2])))
		if got != c.L {
			t.Errorf("RGB(%d,%d,%d): got L=%d, want %d", c.RGB[0], c.RGB[1], c.RGB[2], got, c.L)
		}
	}
}
