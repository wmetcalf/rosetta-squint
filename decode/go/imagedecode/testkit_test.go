package imagedecode

import (
	"encoding/binary"
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"sort"
	"strings"
	"testing"
)

const specDir = "../../spec"

type decodedGolden struct {
	Width, Height int
	Channels      int
	Pixels        []byte
}

func readFixture(t *testing.T, relPath string) []byte {
	t.Helper()
	data, err := os.ReadFile(filepath.Join(specDir, "fixtures", relPath))
	if err != nil {
		t.Fatalf("readFixture %s: %v", relPath, err)
	}
	return data
}

func readGolden(t *testing.T, fixtureRel string) decodedGolden {
	t.Helper()
	blob, err := os.ReadFile(filepath.Join(specDir, "decoded", fixtureRel+".bin"))
	if err != nil {
		t.Fatalf("readGolden %s: %v", fixtureRel, err)
	}
	if len(blob) < 12 {
		t.Fatalf("golden %s too short: %d", fixtureRel, len(blob))
	}
	w := binary.LittleEndian.Uint32(blob[0:4])
	h := binary.LittleEndian.Uint32(blob[4:8])
	return decodedGolden{
		Width:    int(w),
		Height:   int(h),
		Channels: int(blob[8]),
		Pixels:   blob[12:],
	}
}

func listValidFixtures(t *testing.T, format string) []string {
	t.Helper()
	dir := filepath.Join(specDir, "fixtures", format, "valid")
	entries, err := os.ReadDir(dir)
	if err != nil {
		t.Fatalf("listValidFixtures %s: %v", format, err)
	}
	// JPEG fixtures use the .jpg extension even though the format name is "jpeg".
	var validExts []string
	if format == "jpeg" {
		validExts = []string{".jpg", ".jpeg"}
	} else {
		validExts = []string{"." + format}
	}

	var out []string
	for _, e := range entries {
		if !e.Type().IsRegular() {
			continue
		}
		matched := false
		for _, ext := range validExts {
			if strings.HasSuffix(e.Name(), ext) {
				matched = true
				break
			}
		}
		if !matched {
			continue
		}
		out = append(out, fmt.Sprintf("%s/valid/%s", format, e.Name()))
	}
	sort.Strings(out)
	return out
}

type expectedError struct {
	Format                  string `json:"format"`
	ExpectedKind            string `json:"expected_kind"`
	ExpectedDetailSubstring string `json:"expected_detail_substring"`
}

func readErrors(t *testing.T) map[string]expectedError {
	t.Helper()
	data, err := os.ReadFile(filepath.Join(specDir, "errors.json"))
	if err != nil {
		t.Fatalf("readErrors: %v", err)
	}
	var doc struct {
		Fixtures map[string]expectedError `json:"fixtures"`
	}
	if err := json.Unmarshal(data, &doc); err != nil {
		t.Fatalf("readErrors unmarshal: %v", err)
	}
	return doc.Fixtures
}
