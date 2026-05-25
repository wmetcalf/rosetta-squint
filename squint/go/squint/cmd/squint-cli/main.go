// squint-cli — compute a perceptual image hash and print the hex string to stdout.
//
// Used by tools/cross-squint-diff for live cross-port equivalence checking.
//
// Usage: squint-cli <algo> <size> <path>
//
//	algo  — one of: phash, phash_simple, dhash, dhash_vertical, average_hash,
//	        whash_haar, whash_db4, whash_db4_robust, colorhash, crop_resistant_hash
//	size  — hash_size (or binbits for colorhash); pass "-" for crop_resistant_hash
//	path  — path to the image file
//
// Exit: 0 on success, 1 on hash/decode error, 2 on usage error.
package main

import (
	"fmt"
	"os"
	"strconv"

	"github.com/wmetcalf/rosetta-squint/squint/go/squint"
)

func main() {
	if len(os.Args) != 4 {
		fmt.Fprintln(os.Stderr, "usage: squint-cli <algo> <size> <path>")
		os.Exit(2)
	}
	algo := os.Args[1]
	sizeStr := os.Args[2]
	path := os.Args[3]
	size, _ := strconv.Atoi(sizeStr)

	var hex string
	var err error

	switch algo {
	case "phash":
		h, e := squint.PHash(path, size)
		err = e
		if e == nil {
			hex = h.ToHex()
		}
	case "phash_simple":
		h, e := squint.PHashSimple(path, size)
		err = e
		if e == nil {
			hex = h.ToHex()
		}
	case "dhash":
		h, e := squint.DHash(path, size)
		err = e
		if e == nil {
			hex = h.ToHex()
		}
	case "dhash_vertical":
		h, e := squint.DHashVertical(path, size)
		err = e
		if e == nil {
			hex = h.ToHex()
		}
	case "average_hash":
		h, e := squint.AverageHash(path, size)
		err = e
		if e == nil {
			hex = h.ToHex()
		}
	case "whash_haar":
		h, e := squint.WHashHaar(path, size)
		err = e
		if e == nil {
			hex = h.ToHex()
		}
	case "whash_db4":
		h, e := squint.WHashDb4(path, size)
		err = e
		if e == nil {
			hex = h.ToHex()
		}
	case "whash_db4_robust":
		h, e := squint.WHashDb4Robust(path, size)
		err = e
		if e == nil {
			hex = h.ToHex()
		}
	case "colorhash":
		h, e := squint.ColorHash(path, size)
		err = e
		if e == nil {
			hex = h.ToHex()
		}
	case "crop_resistant_hash":
		mh, e := squint.CropResistantHash(path, nil)
		err = e
		if e == nil {
			hex = mh.ToHex()
		}
	default:
		fmt.Fprintf(os.Stderr, "unknown algo: %s\n", algo)
		os.Exit(2)
	}

	if err != nil {
		fmt.Fprintf(os.Stderr, "error: %v\n", err)
		os.Exit(1)
	}
	fmt.Println(hex)
}
