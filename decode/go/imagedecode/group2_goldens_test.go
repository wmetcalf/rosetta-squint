package imagedecode

import (
	"errors"
	"fmt"
	"strings"
	"testing"
)

func TestGroup2ByteExactAllBmp(t *testing.T) {
	fixtures := listValidFixtures(t, "bmp")
	if len(fixtures) != 30 {
		t.Fatalf("expected 30 BMP fixtures, got %d", len(fixtures))
	}
	var failures []string
	for _, rel := range fixtures {
		input := readFixture(t, rel)
		got, err := Decode(input)
		if err != nil {
			var de *DecodeError
			if errors.As(err, &de) {
				failures = append(failures, fmt.Sprintf("%s: threw %s: %s", rel, de.Kind, de.Detail))
			} else {
				failures = append(failures, fmt.Sprintf("%s: threw %v", rel, err))
			}
			continue
		}
		want := readGolden(t, rel)
		if got.Width != want.Width || got.Height != want.Height || got.Channels.BytesPerPixel() != want.Channels {
			failures = append(failures, fmt.Sprintf("%s: shape %dx%dc%d != %dx%dc%d",
				rel, got.Width, got.Height, got.Channels.BytesPerPixel(), want.Width, want.Height, want.Channels))
			continue
		}
		if len(got.Data) != len(want.Pixels) {
			failures = append(failures, fmt.Sprintf("%s: pixel byte count %d != %d", rel, len(got.Data), len(want.Pixels)))
			continue
		}
		for i := range got.Data {
			if got.Data[i] != want.Pixels[i] {
				failures = append(failures, fmt.Sprintf("%s: pixel byte %d got=%d want=%d", rel, i, got.Data[i], want.Pixels[i]))
				break
			}
		}
	}
	if len(failures) > 0 {
		t.Fatalf("%d failures:\n  %s", len(failures), strings.Join(failures, "\n  "))
	}
}
