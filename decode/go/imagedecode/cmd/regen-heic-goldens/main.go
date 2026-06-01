// Command regen-heic-goldens regenerates the HEIC pixel goldens from the
// canonical bundled WASM decoder (internal/heicwasm). It writes the
// decoded/heic/valid/*.bin files and prints per-fixture metadata as JSON so
// goldens.json can be updated. This replaces the old system-libheif ctypes
// path: the goldens are now produced by the same artifact every port uses, so
// they are repo-controlled and OS-independent. See decode/wasm/.
package main

import (
	"crypto/sha256"
	"encoding/binary"
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"runtime"
	"sort"
	"strings"

	"github.com/wmetcalf/rosetta-squint/decode/go/imagedecode/internal/heicwasm"
)

const maxPixels = 256 * 1024 * 1024

func main() {
	_, filename, _, ok := runtime.Caller(0)
	if !ok {
		panic("cannot resolve source path")
	}
	// .../decode/go/imagedecode/cmd/regen-heic-goldens/main.go -> .../decode/spec
	specDir := filepath.Join(filepath.Dir(filename), "../../../../spec")
	fixDir := filepath.Join(specDir, "fixtures/heic/valid")
	outDir := filepath.Join(specDir, "decoded/heic/valid")

	entries, err := os.ReadDir(fixDir)
	if err != nil {
		panic(err)
	}
	var names []string
	for _, e := range entries {
		if strings.HasSuffix(e.Name(), ".heic") {
			names = append(names, e.Name())
		}
	}
	sort.Strings(names)

	meta := map[string]any{}
	for _, name := range names {
		b, err := os.ReadFile(filepath.Join(fixDir, name))
		if err != nil {
			panic(err)
		}
		res, err := heicwasm.Decode(b, maxPixels)
		if err != nil {
			panic(fmt.Sprintf("%s: %v", name, err))
		}
		// .bin = 12-byte header (w,h LE u32, channels byte, 3 pad) + pixels
		hdr := make([]byte, 12)
		binary.LittleEndian.PutUint32(hdr[0:], uint32(res.Width))
		binary.LittleEndian.PutUint32(hdr[4:], uint32(res.Height))
		hdr[8] = byte(res.Channels)
		blob := append(hdr, res.Data...)
		if err := os.WriteFile(filepath.Join(outDir, name+".bin"), blob, 0o600); err != nil {
			panic(err)
		}
		sum := sha256.Sum256(res.Data)
		rel := "heic/valid/" + name
		meta[rel] = map[string]any{
			"format":   "heic",
			"width":    res.Width,
			"height":   res.Height,
			"channels": res.Channels,
			"sha256":   fmt.Sprintf("%x", sum),
		}
		fmt.Fprintf(os.Stderr, "regenerated %s: %dx%d ch=%d\n", name, res.Width, res.Height, res.Channels)
	}
	out, _ := json.MarshalIndent(meta, "", "  ")
	if _, err := os.Stdout.Write(out); err != nil {
		panic(err)
	}
}
