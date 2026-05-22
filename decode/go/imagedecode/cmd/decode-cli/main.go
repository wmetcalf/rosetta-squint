// decode-cli — decode an image and emit raw bytes (spec/SPEC.md §2 wire
// format: 12-byte header + row-major pixels) to stdout.
//
// Used by tools/cross-port-diff/diff_all.py for live cross-port equivalence
// checking.
//
// Usage: decode-cli <fixture.path>
// Exit:  0 on success, 1 on decode error, 2 on harness error.
package main

import (
	"encoding/binary"
	"errors"
	"fmt"
	"os"

	"github.com/wmetcalf/rosetta-image-decode/go/imagedecode"
)

func main() {
	if len(os.Args) != 2 {
		fmt.Fprintln(os.Stderr, "usage: decode-cli <fixture>")
		os.Exit(2)
	}
	data, err := os.ReadFile(os.Args[1])
	if err != nil {
		fmt.Fprintf(os.Stderr, "read %s: %v\n", os.Args[1], err)
		os.Exit(2)
	}
	img, err := imagedecode.Decode(data)
	if err != nil {
		var de *imagedecode.DecodeError
		if errors.As(err, &de) {
			fmt.Fprintf(os.Stderr, "decode error: %s: %s\n", de.Kind, de.Detail)
		} else {
			fmt.Fprintf(os.Stderr, "decode error: %v\n", err)
		}
		os.Exit(1)
	}
	var ch byte
	switch img.Channels {
	case imagedecode.RGB:
		ch = 3
	case imagedecode.RGBA:
		ch = 4
	default:
		fmt.Fprintf(os.Stderr, "unknown channels: %v\n", img.Channels)
		os.Exit(1)
	}
	var hdr [12]byte
	binary.LittleEndian.PutUint32(hdr[0:4], uint32(img.Width))
	binary.LittleEndian.PutUint32(hdr[4:8], uint32(img.Height))
	hdr[8] = ch
	if _, err := os.Stdout.Write(hdr[:]); err != nil {
		os.Exit(2)
	}
	if _, err := os.Stdout.Write(img.Data); err != nil {
		os.Exit(2)
	}
}
