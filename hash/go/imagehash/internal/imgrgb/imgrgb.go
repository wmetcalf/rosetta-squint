// Package imgrgb normalizes any image.Image to a canonical [H][W][3]uint8
// row-major RGB buffer. Non-opaque sources are composited on opaque black
// using the truncated 8-bit alpha-premultiplication formula
//
//	out_c = (src_c * alpha) / 255   // integer truncation, NOT rounded
//
// matching the formula used by the Rust, Java (post-fix), JS, and Swift
// ports. Previously this package delegated to `draw.Draw(... draw.Over)`,
// which composites in 16-bit-per-channel space (dividing by 0xffff) and
// can drift by ±1 LSB versus the truncated 8-bit formula on partial-alpha
// pixels. That drift cascades through Lanczos / DCT / wavelet pipelines
// into hash bit disagreements between Go and the other ports.
package imgrgb

import (
	"image"
)

// ToRGB normalizes any image.Image to a [H][W][3]uint8 RGB buffer with the
// alpha channel (if any) composited against opaque black via the truncated
// 8-bit formula. Concrete fast paths cover *image.NRGBA, *image.NRGBA64
// (which down-converts to 8-bit straight), *image.RGBA (premultiplied —
// we un-premultiply, then re-premultiply against black, which collapses
// to dst = src.Pix[:3] verbatim since the premultiplied bytes already are
// "src.rgb * alpha / 255" — see the *image.RGBA branch below), and
// *image.Gray (replicate Y across all three channels). All other types
// fall through to a generic path that reads via At().RGBA() and converts
// back to straight 8-bit before applying the truncated formula.
func ToRGB(img image.Image) [][][3]uint8 {
	bounds := img.Bounds()
	w := bounds.Dx()
	h := bounds.Dy()

	out := make([][][3]uint8, h)
	for y := 0; y < h; y++ {
		out[y] = make([][3]uint8, w)
	}

	switch src := img.(type) {
	case *image.NRGBA:
		// Straight (non-premultiplied) 8-bit alpha. Most common case for
		// the squint chain (decoders coerce to NRGBA). Read raw bytes and
		// apply the truncated multiply directly.
		stride := src.Stride
		base := src.PixOffset(bounds.Min.X, bounds.Min.Y)
		for y := 0; y < h; y++ {
			rowOff := base + y*stride
			for x := 0; x < w; x++ {
				i := rowOff + x*4
				r := uint16(src.Pix[i])
				g := uint16(src.Pix[i+1])
				b := uint16(src.Pix[i+2])
				a := uint16(src.Pix[i+3])
				if a == 0xFF {
					out[y][x] = [3]uint8{uint8(r), uint8(g), uint8(b)}
				} else {
					out[y][x] = [3]uint8{
						uint8((r * a) / 255),
						uint8((g * a) / 255),
						uint8((b * a) / 255),
					}
				}
			}
		}
		return out

	case *image.RGBA:
		// Go's *image.RGBA is already alpha-premultiplied: each byte in
		// src.Pix[:3] represents `floor(straight_c * alpha / 255)` (this
		// is the standard Go convention; e.g. image/png writes RGBA with
		// premultiplied bytes for sources that have non-opaque alpha).
		// Compositing those bytes against opaque black using the same
		// truncated formula gives:
		//   out_c = floor(premul_c * alpha / 255)
		// which is NOT the same as the straight-alpha formula. To match
		// the Rust / JS / Swift / Python ports we need to recover the
		// straight value then re-multiply:
		//   straight_c = floor((premul_c * 255) / alpha)   if alpha > 0
		//   out_c      = floor((straight_c * alpha) / 255)
		// These two operations cancel each other (modulo rounding) so we
		// can just use src.Pix[:3] verbatim — that's what we want. For
		// alpha == 0 the premul bytes are already 0. For alpha == 255 the
		// premul bytes equal the straight bytes. So the fast path is just
		// "copy src.Pix[:3]". This branch is correct iff the input really
		// followed Go's premul convention; decoders that put straight
		// bytes into *image.RGBA would violate Go's convention and break
		// here, but they also break stdlib draw.Draw, so this is no worse.
		stride := src.Stride
		base := src.PixOffset(bounds.Min.X, bounds.Min.Y)
		for y := 0; y < h; y++ {
			rowOff := base + y*stride
			for x := 0; x < w; x++ {
				i := rowOff + x*4
				out[y][x] = [3]uint8{src.Pix[i], src.Pix[i+1], src.Pix[i+2]}
			}
		}
		return out

	case *image.NRGBA64:
		// Straight 16-bit. Truncate to 8-bit by taking the high byte
		// (same as PIL's tobytes() when stripping a 16-bit channel to
		// 8-bit), then apply the truncated multiply.
		stride := src.Stride
		base := src.PixOffset(bounds.Min.X, bounds.Min.Y)
		for y := 0; y < h; y++ {
			rowOff := base + y*stride
			for x := 0; x < w; x++ {
				i := rowOff + x*8
				r := uint16(src.Pix[i])
				g := uint16(src.Pix[i+2])
				b := uint16(src.Pix[i+4])
				a := uint16(src.Pix[i+6])
				if a == 0xFF {
					out[y][x] = [3]uint8{uint8(r), uint8(g), uint8(b)}
				} else {
					out[y][x] = [3]uint8{
						uint8((r * a) / 255),
						uint8((g * a) / 255),
						uint8((b * a) / 255),
					}
				}
			}
		}
		return out

	case *image.Gray:
		// Opaque grayscale — replicate Y across all 3 channels.
		stride := src.Stride
		base := src.PixOffset(bounds.Min.X, bounds.Min.Y)
		for y := 0; y < h; y++ {
			rowOff := base + y*stride
			for x := 0; x < w; x++ {
				v := src.Pix[rowOff+x]
				out[y][x] = [3]uint8{v, v, v}
			}
		}
		return out
	}

	// Generic fallback: paletted, YCbCr, Gray16, CMYK, custom types, etc.
	// We pull each pixel via At().RGBA() — the standard Go conversion that
	// returns 16-bit *premultiplied* values. Convert back to 8-bit
	// straight, then re-apply the truncated multiply against black. The
	// uint32 → uint16 truncation here is the same as taking the high byte;
	// for paletted images the palette entries are typically opaque RGBA
	// with alpha=0xFFFF so the multiply collapses to identity.
	for y := 0; y < h; y++ {
		for x := 0; x < w; x++ {
			pr, pg, pb, pa := img.At(bounds.Min.X+x, bounds.Min.Y+y).RGBA()
			// 16-bit premultiplied → 8-bit straight.
			var r, g, b uint16
			if pa == 0 {
				// Avoid divide-by-zero; transparent → opaque black.
				out[y][x] = [3]uint8{0, 0, 0}
				continue
			}
			// Recover straight values, then truncate from 16-bit to 8-bit
			// by dividing by 0x101 (the same ratio PIL uses when
			// downconverting 16-bit straight values). For alpha=0xFFFF
			// (the common opaque case) (pr * 0xFFFF / 0xFFFF) / 0x101 ==
			// pr / 0x101 == high byte, exactly matching the .RGBA8 path.
			r = uint16(((uint32(pr)*0xFFFF + uint32(pa)/2) / uint32(pa)) / 0x101)
			g = uint16(((uint32(pg)*0xFFFF + uint32(pa)/2) / uint32(pa)) / 0x101)
			b = uint16(((uint32(pb)*0xFFFF + uint32(pa)/2) / uint32(pa)) / 0x101)
			a8 := uint16(pa / 0x101)
			if a8 == 0xFF {
				out[y][x] = [3]uint8{uint8(r), uint8(g), uint8(b)}
			} else {
				out[y][x] = [3]uint8{
					uint8((r * a8) / 255),
					uint8((g * a8) / 255),
					uint8((b * a8) / 255),
				}
			}
		}
	}
	return out
}
