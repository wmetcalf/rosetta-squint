package imagedecode

import (
	"errors"
	"fmt"
	"strings"
	"testing"
)

func TestGroup4InvalidPngFixtures(t *testing.T) {
	errs := readErrors(t)
	var failures []string
	for key, expected := range errs {
		if !strings.HasPrefix(key, "png/") {
			continue
		}
		input := readFixture(t, key)
		_, err := Decode(input)
		if err == nil {
			failures = append(failures, fmt.Sprintf("%s: decode succeeded, expected %s", key, expected.ExpectedKind))
			continue
		}
		var de *DecodeError
		if !errors.As(err, &de) {
			failures = append(failures, fmt.Sprintf("%s: unexpected error type %T: %v", key, err, err))
			continue
		}
		if de.Kind.String() != expected.ExpectedKind {
			failures = append(failures, fmt.Sprintf("%s: kind %s != %s", key, de.Kind, expected.ExpectedKind))
			continue
		}
		if expected.ExpectedDetailSubstring != "" && !strings.Contains(de.Detail, expected.ExpectedDetailSubstring) {
			failures = append(failures, fmt.Sprintf("%s: detail '%s' does not contain '%s'", key, de.Detail, expected.ExpectedDetailSubstring))
		}
	}
	if len(failures) > 0 {
		t.Fatalf("%d Group-4 PNG failures:\n  %s", len(failures), strings.Join(failures, "\n  "))
	}
}

func TestGroup4InvalidGifFixtures(t *testing.T) {
	errs := readErrors(t)
	var failures []string
	for key, expected := range errs {
		if !strings.HasPrefix(key, "gif/") {
			continue
		}
		input := readFixture(t, key)
		_, err := Decode(input)
		if err == nil {
			failures = append(failures, fmt.Sprintf("%s: decode succeeded, expected %s", key, expected.ExpectedKind))
			continue
		}
		var de *DecodeError
		if !errors.As(err, &de) {
			failures = append(failures, fmt.Sprintf("%s: unexpected error type %T: %v", key, err, err))
			continue
		}
		if de.Kind.String() != expected.ExpectedKind {
			failures = append(failures, fmt.Sprintf("%s: kind %s != %s", key, de.Kind, expected.ExpectedKind))
			continue
		}
		if expected.ExpectedDetailSubstring != "" && !strings.Contains(de.Detail, expected.ExpectedDetailSubstring) {
			failures = append(failures, fmt.Sprintf("%s: detail '%s' does not contain '%s'", key, de.Detail, expected.ExpectedDetailSubstring))
		}
	}
	if len(failures) > 0 {
		t.Fatalf("%d Group-4 GIF failures:\n  %s", len(failures), strings.Join(failures, "\n  "))
	}
}

func TestGroup4InvalidBmpFixtures(t *testing.T) {
	errs := readErrors(t)
	var failures []string
	for key, expected := range errs {
		if !strings.HasPrefix(key, "bmp/") {
			continue
		}
		input := readFixture(t, key)
		_, err := Decode(input)
		if err == nil {
			failures = append(failures, fmt.Sprintf("%s: decode succeeded, expected %s", key, expected.ExpectedKind))
			continue
		}
		var de *DecodeError
		if !errors.As(err, &de) {
			failures = append(failures, fmt.Sprintf("%s: unexpected error type %T: %v", key, err, err))
			continue
		}
		if de.Kind.String() != expected.ExpectedKind {
			failures = append(failures, fmt.Sprintf("%s: kind %s != %s", key, de.Kind, expected.ExpectedKind))
			continue
		}
		if expected.ExpectedDetailSubstring != "" && !strings.Contains(de.Detail, expected.ExpectedDetailSubstring) {
			failures = append(failures, fmt.Sprintf("%s: detail '%s' does not contain '%s'", key, de.Detail, expected.ExpectedDetailSubstring))
		}
	}
	if len(failures) > 0 {
		t.Fatalf("%d Group-4 failures:\n  %s", len(failures), strings.Join(failures, "\n  "))
	}
}

func TestGroup4InvalidJpegFixtures(t *testing.T) {
	errs := readErrors(t)
	var failures []string
	for key, expected := range errs {
		if !strings.HasPrefix(key, "jpeg/") {
			continue
		}
		input := readFixture(t, key)
		_, err := Decode(input)
		if err == nil {
			failures = append(failures, fmt.Sprintf("%s: decode succeeded, expected %s", key, expected.ExpectedKind))
			continue
		}
		var de *DecodeError
		if !errors.As(err, &de) {
			failures = append(failures, fmt.Sprintf("%s: unexpected error type %T: %v", key, err, err))
			continue
		}
		if de.Kind.String() != expected.ExpectedKind {
			failures = append(failures, fmt.Sprintf("%s: kind %s != %s", key, de.Kind, expected.ExpectedKind))
			continue
		}
		if expected.ExpectedDetailSubstring != "" && !strings.Contains(de.Detail, expected.ExpectedDetailSubstring) {
			failures = append(failures, fmt.Sprintf("%s: detail '%s' does not contain '%s'", key, de.Detail, expected.ExpectedDetailSubstring))
		}
	}
	if len(failures) > 0 {
		t.Fatalf("%d Group-4 JPEG failures:\n  %s", len(failures), strings.Join(failures, "\n  "))
	}
}
