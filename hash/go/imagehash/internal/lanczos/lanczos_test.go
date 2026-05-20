package lanczos_test

import (
	"testing"

	"github.com/wmetcalf/rosetta-image-hash/go/imagehash/internal/lanczos"
	"github.com/wmetcalf/rosetta-image-hash/go/imagehash/internal/testkit"
)

func TestLanczosFixed(t *testing.T) {
	cases := []string{
		"downsample_64_to_32_gradient",
		"upsample_16_to_32_gradient",
		"identity_32_to_32_random",
		"asymmetric_64x48_to_32x24",
	}
	for _, name := range cases {
		name := name
		t.Run(name, func(t *testing.T) {
			c := testkit.LoadLanczosCaseFromInternal(t, name)
			got := lanczos.Resize(c.Src, c.DstW, c.DstH)
			if len(got) != c.DstH {
				t.Fatalf("row count: got %d, want %d", len(got), c.DstH)
			}
			if len(got[0]) != c.DstW {
				t.Fatalf("col count: got %d, want %d", len(got[0]), c.DstW)
			}
			for y := 0; y < c.DstH; y++ {
				for x := 0; x < c.DstW; x++ {
					if got[y][x] != c.Dst[y][x] {
						t.Fatalf("%s pixel (%d,%d): got %d, want %d", name, y, x, got[y][x], c.Dst[y][x])
					}
				}
			}
		})
	}
}
