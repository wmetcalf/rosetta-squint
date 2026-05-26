import Foundation
import Ctiff

/// Heap-allocated context backing the in-memory TIFFClientOpen callbacks.
/// Lifetime is managed via `Unmanaged`: a +1 retain is taken when we hand the
/// opaque pointer to libtiff, and released in the `close` callback.
private final class TiffReadContext {
	let bytes: [UInt8]
	var pos: Int = 0
	init(_ b: [UInt8]) { self.bytes = b }
}

// MARK: - C callback function pointers
//
// These must be top-level (or `static`) `@convention(c)` functions so Swift
// will produce a real C function pointer — a closure that captures context
// cannot be passed to libtiff. We thread the `TiffReadContext` through the
// opaque `thandle_t` (clientdata) argument.

private func tiffRead(handle: thandle_t?, buf: UnsafeMutableRawPointer?, count: tmsize_t) -> tmsize_t {
	guard let handle = handle, let buf = buf else { return 0 }
	let ctx = Unmanaged<TiffReadContext>.fromOpaque(handle).takeUnretainedValue()
	let remaining = ctx.bytes.count - ctx.pos
	if remaining <= 0 { return 0 }
	let n = min(Int(count), remaining)
	ctx.bytes.withUnsafeBufferPointer { src in
		buf.copyMemory(from: src.baseAddress!.advanced(by: ctx.pos), byteCount: n)
	}
	ctx.pos += n
	return tmsize_t(n)
}

private func tiffWrite(handle: thandle_t?, buf: UnsafeMutableRawPointer?, count: tmsize_t) -> tmsize_t {
	// Read-only client; libtiff should never call this when opened with "r".
	return 0
}

private func tiffSeek(handle: thandle_t?, off: toff_t, whence: Int32) -> toff_t {
	guard let handle = handle else { return toff_t(bitPattern: -1) }
	let ctx = Unmanaged<TiffReadContext>.fromOpaque(handle).takeUnretainedValue()
	let size = ctx.bytes.count
	// SEEK_SET=0, SEEK_CUR=1, SEEK_END=2
	//
	// `off` is `toff_t` (UInt64). Naive `Int(off)` traps with a precondition
	// failure if off > Int64.max — a hostile BigTIFF (with 8-byte offsets) can
	// trigger this and SIGABRT the process. Bounded-cast every arithmetic step
	// and return libtiff's standard "seek failed" sentinel (toff_t == -1) on
	// overflow. Note: libtiff seeks past EOF are intentionally allowed
	// (returns target offset); only return error sentinel on actual overflow.
	let newPos: Int
	switch whence {
	case 0:  // SEEK_SET — `off` is an absolute non-negative offset
		if off > UInt64(Int.max) { return toff_t(bitPattern: -1) }
		newPos = Int(off)
	case 1:  // SEEK_CUR — `off` interpreted as signed delta
		let signed = Int64(bitPattern: off)
		// On 64-bit Int == Int64, so the Int(signed) conversion is total.
		// On 32-bit Int < Int64, bound-check the delta before converting.
		if signed > Int64(Int.max) || signed < Int64(Int.min) {
			return toff_t(bitPattern: -1)
		}
		let (sum, overflowed) = ctx.pos.addingReportingOverflow(Int(signed))
		if overflowed { return toff_t(bitPattern: -1) }
		newPos = sum
	case 2:  // SEEK_END — `off` interpreted as signed delta from end
		let signed = Int64(bitPattern: off)
		if signed > Int64(Int.max) || signed < Int64(Int.min) {
			return toff_t(bitPattern: -1)
		}
		let (sum, overflowed) = size.addingReportingOverflow(Int(signed))
		if overflowed { return toff_t(bitPattern: -1) }
		newPos = sum
	default:
		return toff_t(bitPattern: -1)
	}
	if newPos < 0 { return toff_t(bitPattern: -1) }
	// libtiff is allowed to seek past EOF; clamp position for read but report
	// the requested offset back to libtiff.
	ctx.pos = newPos
	return toff_t(newPos)
}

private func tiffClose(handle: thandle_t?) -> Int32 {
	guard let handle = handle else { return 0 }
	// Release the +1 retain taken when we passed the context to libtiff.
	Unmanaged<TiffReadContext>.fromOpaque(handle).release()
	return 0
}

private func tiffSize(handle: thandle_t?) -> toff_t {
	guard let handle = handle else { return 0 }
	let ctx = Unmanaged<TiffReadContext>.fromOpaque(handle).takeUnretainedValue()
	return toff_t(ctx.bytes.count)
}

// mmap / munmap intentionally omitted (passed as nil) — libtiff falls back to
// read/seek when no mmap callback is provided.

internal enum TIFFDecoder {
	static func decode(bytes: [UInt8]) throws -> DecodedImage {
		// Pre-decode dimension check (spec §3.1) — libtiff rejects short or
		// structurally-suspect headers as corruptInput while reading the IFD,
		// so we sniff the declared ImageWidth/ImageLength from the first IFD
		// ourselves to surface imageTooLarge cleanly. This runs before libtiff
		// is invoked at all.
		if let (w, h) = DimensionSniff.sniffTiffDimensions(bytes) {
			try Limits.checkDimensions(width: w, height: h, format: .tiff)
		}

		// Drive libtiff against the input bytes in-memory via TIFFClientOpen.
		// No temp file is written, so this works under read-only filesystems,
		// strict sandboxes, and won't leak files if the process is SIGKILLed.
		// Mirrors the in-memory decode strategy used by the other 4 ports.
		let ctx = TiffReadContext(bytes)
		let opaque = Unmanaged.passRetained(ctx).toOpaque()

		guard let handle = TIFFClientOpen(
			"rosetta-mem", "r",
			thandle_t(opaque),
			tiffRead,
			tiffWrite,
			tiffSeek,
			tiffClose,
			tiffSize,
			nil,   // TIFFMapFileProc — unused, force read/seek path
			nil    // TIFFUnmapFileProc
		) else {
			// TIFFClientOpen failure means it never invoked our close callback,
			// so we must release the retain manually here to avoid leaking.
			Unmanaged<TiffReadContext>.fromOpaque(opaque).release()
			throw DecodeError.corruptInput(format: .tiff, detail: "TIFFClientOpen failed")
		}
		// TIFFClose will invoke our tiffClose callback, which releases ctx.
		defer { TIFFClose(handle) }

		var width: UInt32 = 0
		var height: UInt32 = 0
		TIFFGetField_UInt32(handle, UInt32(TIFFTAG_IMAGEWIDTH), &width)
		TIFFGetField_UInt32(handle, UInt32(TIFFTAG_IMAGELENGTH), &height)

		guard width > 0, height > 0 else {
			throw DecodeError.corruptInput(format: .tiff, detail: "zero dimensions in TIFF")
		}

		try Limits.checkDimensions(width: Int(width), height: Int(height), format: .tiff)

		let pixels = Int(width) * Int(height)
		// TIFFReadRGBAImageOriented with ORIENTATION_TOPLEFT delivers rows top-down,
		// so no manual flip is needed.
		var raster = [UInt32](repeating: 0, count: pixels)

		let ok = raster.withUnsafeMutableBufferPointer { buf -> Int32 in
			TIFFReadRGBAImageOriented(
				handle, width, height, buf.baseAddress,
				Int32(ORIENTATION_TOPLEFT), 0
			)
		}
		if ok == 0 {
			throw DecodeError.corruptInput(format: .tiff, detail: "TIFFReadRGBAImageOriented failed")
		}

		// Each UInt32 is packed as ABGR (little-endian byte order: R, G, B, A).
		// Extract R, G, B in order.
		let w = Int(width)
		let h = Int(height)
		var out = [UInt8](repeating: 0, count: pixels * 3)
		for i in 0..<pixels {
			let pixel = raster[i]
			out[i * 3]     = UInt8(pixel & 0xFF)          // R
			out[i * 3 + 1] = UInt8((pixel >> 8) & 0xFF)   // G
			out[i * 3 + 2] = UInt8((pixel >> 16) & 0xFF)  // B
		}

		return DecodedImage(
			width: w,
			height: h,
			data: out,
			channels: .rgb,
			format: .tiff
		)
	}
}
