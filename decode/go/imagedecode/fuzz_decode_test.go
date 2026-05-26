package imagedecode

import (
	"testing"
)

// Native Go fuzz harness (Go 1.18+). Mirrors the Rust cargo-fuzz target at
// rosetta-squint-decode/fuzz/fuzz_targets/decode_any.rs.
//
// Run:
//
//	go test -run=. -fuzz=FuzzDecodeAny -fuzztime=60s
//	go test -run=. -fuzz=FuzzDecodeWithPrefix -fuzztime=60s
//
// Property under test: decode() MUST NOT panic on any input bytes. A
// well-formed image returns a DecodedImage; a malformed image returns a
// typed *DecodeError. Anything else (panic, segfault via cgo, OOM from
// unchecked size arithmetic) is a bug.
//
// Why this complements the Rust fuzz target: the Go decode paths are
// independent reimplementations (BMP/GIF) or independent cgo bindings
// (JPEG via TurboJPEG, HEIC via cgo, WebP via chai2010/webp's bundled
// libwebp). Bugs caught in Rust's mozjpeg-sys binding don't necessarily
// surface here, and vice versa.

func FuzzDecodeAny(f *testing.F) {
	// Seed with a few minimal valid headers so the fuzzer starts from
	// known-interesting parser entrypoints.
	f.Add([]byte{0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A}) // PNG signature
	f.Add([]byte{0xFF, 0xD8, 0xFF, 0xE0, 0x00, 0x10, 'J', 'F', 'I', 'F', 0})
	f.Add([]byte("GIF89a"))
	f.Add([]byte("BM"))
	f.Add([]byte{0x52, 0x49, 0x46, 0x46, 0, 0, 0, 0, 0x57, 0x45, 0x42, 0x50})
	f.Add([]byte{0x49, 0x49, 0x2A, 0x00, 0x08, 0x00, 0x00, 0x00})
	f.Add([]byte{0x00, 0x00, 0x00, 0x18, 0x66, 0x74, 0x79, 0x70, 0x68, 0x65, 0x69, 0x63})

	f.Fuzz(func(t *testing.T, data []byte) {
		// We don't care about the result — only that no panic occurs.
		// All "this is bad input" cases should return a *DecodeError.
		_, _ = Decode(data)
	})
}

func FuzzDecodeWithPrefix(f *testing.F) {
	// Mirror of Rust's decode_with_prefix.rs target. The fuzzer's first
	// byte deterministically selects one of seven magic-byte prefixes
	// and the rest of the input becomes the fake bitstream. Forces the
	// fuzzer to exercise each format's parsing logic instead of bouncing
	// off the format-detection step.
	prefixes := [][]byte{
		{0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A},
		{0xFF, 0xD8, 0xFF, 0xE0},
		{0x47, 0x49, 0x46, 0x38, 0x39, 0x61},
		{0x42, 0x4D},
		{0x52, 0x49, 0x46, 0x46, 0, 0, 0, 0, 0x57, 0x45, 0x42, 0x50},
		{0x49, 0x49, 0x2A, 0x00},
		{0x00, 0x00, 0x00, 0x18, 0x66, 0x74, 0x79, 0x70, 0x68, 0x65, 0x69, 0x63},
	}

	// Seed with one example of each prefix selector.
	for i := byte(0); i < byte(len(prefixes)); i++ {
		f.Add([]byte{i, 0x00, 0x00, 0x00, 0x00})
	}

	f.Fuzz(func(t *testing.T, data []byte) {
		if len(data) == 0 {
			return
		}
		prefix := prefixes[int(data[0])%len(prefixes)]
		combined := make([]byte, 0, len(prefix)+len(data)-1)
		combined = append(combined, prefix...)
		combined = append(combined, data[1:]...)
		_, _ = Decode(combined)
	})
}
