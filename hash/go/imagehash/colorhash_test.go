package imagehash_test

import (
	"testing"

	"github.com/wmetcalf/rosetta-image-hash/go/imagehash"
)

func TestBinEncodingB4(t *testing.T) {
	cases := []struct {
		v        int
		expected [4]bool
	}{
		{0, [4]bool{false, false, false, false}},
		{1, [4]bool{false, false, false, true}},
		{2, [4]bool{false, false, true, false}},
		{4, [4]bool{false, true, true, false}},
		{7, [4]bool{false, true, true, true}},
		{8, [4]bool{true, true, false, false}},
		{15, [4]bool{true, true, true, true}},
	}
	for _, c := range cases {
		got := imagehash.ColorhashBinEncode(c.v, 4)
		if len(got) != 4 {
			t.Fatalf("v=%d len: got %d, want 4", c.v, len(got))
		}
		for i := 0; i < 4; i++ {
			if got[i] != c.expected[i] {
				t.Errorf("v=%d bit %d: got %v, want %v", c.v, i, got[i], c.expected[i])
			}
		}
	}
}

func TestBinEncodingB3(t *testing.T) {
	got := imagehash.ColorhashBinEncode(0, 3)
	if got[0] || got[1] || got[2] {
		t.Errorf("v=0,B=3: got %v, want [false,false,false]", got)
	}
	got = imagehash.ColorhashBinEncode(7, 3)
	if !got[0] || !got[1] || !got[2] {
		t.Errorf("v=7,B=3: got %v, want [true,true,true]", got)
	}
}
