package testkit

import (
	"encoding/json"
	"fmt"
	"os"
	"sort"
	"sync"
)

// Triple is a single (fixture, size_or_binbits, expected_hex) test case.
type Triple struct {
	Fixture string
	Size    int
	Hex     string
}

var (
	goldensOnce sync.Once
	goldensData map[string]interface{}
	goldensErr  error
)

func loadGoldens(specDir string) (map[string]interface{}, error) {
	goldensOnce.Do(func() {
		path := specDir + "/goldens.json"
		data, err := os.ReadFile(path)
		if err != nil {
			goldensErr = fmt.Errorf("read %s: %w", path, err)
			return
		}
		var out map[string]interface{}
		if err := json.Unmarshal(data, &out); err != nil {
			goldensErr = fmt.Errorf("parse %s: %w", path, err)
			return
		}
		goldensData = out
	})
	return goldensData, goldensErr
}

// AlgorithmCasesFromRoot returns test triples for the given algorithm, loading goldens from DirRoot().
// Null hex entries (small-image whash cases) are skipped.
func AlgorithmCasesFromRoot(algorithm string) ([]Triple, error) {
	return algorithmCases(DirRoot(), algorithm)
}

// AlgorithmCasesFromInternal returns test triples for use in internal/* tests.
func AlgorithmCasesFromInternal(algorithm string) ([]Triple, error) {
	return algorithmCases(DirInternal(), algorithm)
}

func algorithmCases(specDir, algorithm string) ([]Triple, error) {
	g, err := loadGoldens(specDir)
	if err != nil {
		return nil, err
	}
	algos, ok := g["algorithms"].(map[string]interface{})
	if !ok {
		return nil, fmt.Errorf("goldens.json missing 'algorithms' object")
	}
	algo, ok := algos[algorithm].(map[string]interface{})
	if !ok {
		return nil, fmt.Errorf("algorithm %q not in goldens", algorithm)
	}
	fixtures, ok := algo["fixtures"].(map[string]interface{})
	if !ok {
		return nil, fmt.Errorf("algorithm %q missing 'fixtures'", algorithm)
	}

	var out []Triple
	names := make([]string, 0, len(fixtures))
	for n := range fixtures {
		names = append(names, n)
	}
	sort.Strings(names)
	for _, fixture := range names {
		sizesObj, ok := fixtures[fixture].(map[string]interface{})
		if !ok {
			continue
		}
		sizeKeys := make([]string, 0, len(sizesObj))
		for k := range sizesObj {
			sizeKeys = append(sizeKeys, k)
		}
		sort.Strings(sizeKeys)
		for _, sizeStr := range sizeKeys {
			v := sizesObj[sizeStr]
			if v == nil {
				continue
			}
			hex, ok := v.(string)
			if !ok {
				continue
			}
			var size int
			if _, err := fmt.Sscanf(sizeStr, "%d", &size); err != nil {
				continue
			}
			out = append(out, Triple{Fixture: fixture, Size: size, Hex: hex})
		}
	}
	return out, nil
}

// CropResistantCase holds a single (fixture, expected_hex) test case for
// crop_resistant_hash, which uses "default" as its size key.
type CropResistantCase struct {
	Fixture string
	Hex     string
}

// CropResistantCasesFromRoot returns test cases for crop_resistant_hash.
func CropResistantCasesFromRoot() ([]CropResistantCase, error) {
	return cropResistantCases(DirRoot())
}

func cropResistantCases(specDir string) ([]CropResistantCase, error) {
	g, err := loadGoldens(specDir)
	if err != nil {
		return nil, err
	}
	algos, ok := g["algorithms"].(map[string]interface{})
	if !ok {
		return nil, fmt.Errorf("goldens.json missing 'algorithms' object")
	}
	algo, ok := algos["crop_resistant_hash"].(map[string]interface{})
	if !ok {
		return nil, fmt.Errorf("algorithm 'crop_resistant_hash' not in goldens")
	}
	fixtures, ok := algo["fixtures"].(map[string]interface{})
	if !ok {
		return nil, fmt.Errorf("algorithm 'crop_resistant_hash' missing 'fixtures'")
	}

	var out []CropResistantCase
	names := make([]string, 0, len(fixtures))
	for n := range fixtures {
		names = append(names, n)
	}
	sort.Strings(names)
	for _, fixture := range names {
		sizesObj, ok := fixtures[fixture].(map[string]interface{})
		if !ok {
			continue
		}
		v, ok := sizesObj["default"]
		if !ok || v == nil {
			continue
		}
		hex, ok := v.(string)
		if !ok {
			continue
		}
		out = append(out, CropResistantCase{Fixture: fixture, Hex: hex})
	}
	return out, nil
}
