package testkit

import (
	"encoding/binary"
	"os"
	"testing"
)

// LanczosCase holds a single (src buffer, expected dst buffer) pair from spec/lanczos_cases/.
type LanczosCase struct {
	SrcW, SrcH, DstW, DstH int
	Src                    [][]uint8 // shape [SrcH][SrcW]
	Dst                    [][]uint8 // shape [DstH][DstW]
}

// LoadLanczosCaseFromInternal reads spec/lanczos_cases/<name>.bin.
func LoadLanczosCaseFromInternal(t *testing.T, name string) LanczosCase {
	t.Helper()
	path := DirInternal() + "/lanczos_cases/" + name + ".bin"
	data, err := os.ReadFile(path)
	if err != nil {
		t.Fatalf("read %s: %v", path, err)
	}
	if len(data) < 16 {
		t.Fatalf("lanczos case %s too short", name)
	}
	sw := int(binary.LittleEndian.Uint32(data[0:4]))
	sh := int(binary.LittleEndian.Uint32(data[4:8]))
	dw := int(binary.LittleEndian.Uint32(data[8:12]))
	dh := int(binary.LittleEndian.Uint32(data[12:16]))
	if len(data) != 16+sw*sh+dw*dh {
		t.Fatalf("lanczos %s length mismatch", name)
	}
	off := 16
	src := make([][]uint8, sh)
	for y := 0; y < sh; y++ {
		src[y] = make([]uint8, sw)
		for x := 0; x < sw; x++ {
			src[y][x] = data[off]
			off++
		}
	}
	dst := make([][]uint8, dh)
	for y := 0; y < dh; y++ {
		dst[y] = make([]uint8, dw)
		for x := 0; x < dw; x++ {
			dst[y][x] = data[off]
			off++
		}
	}
	return LanczosCase{SrcW: sw, SrcH: sh, DstW: dw, DstH: dh, Src: src, Dst: dst}
}
