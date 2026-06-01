import Foundation

/// Decodes HEIC via the shared libheif+libde265 WASM module (decode/wasm/), run
/// in WasmKit — no system libheif. The same WASM artifact is shared by every
/// port so HEIC output is byte-identical across languages. See decode/wasm/ and
/// decode/spec/HEIC_REPRODUCIBILITY.md.
internal enum HEICDecoder {
    static func decode(bytes: [UInt8]) throws -> DecodedImage {
        // Sniff the container's primary-item ispe dimensions BEFORE decoding.
        // libheif reports HEVC-bitstream dimensions, not the container's ispe,
        // so a patched ispe is only caught here. Spec §3.1.
        if let dims = DimensionSniff.sniffHeicDimensions(bytes) {
            try Limits.checkDimensions(width: dims.0, height: dims.1, format: .heic)
        }

        do {
            let res = try HeicWasm.decode(bytes: bytes, maxPixels: MAX_PIXELS)
            let channels: Channels = res.channels == 4 ? .rgba : .rgb
            return DecodedImage(
                width: res.width,
                height: res.height,
                data: res.data,
                channels: channels,
                format: .heic
            )
        } catch let HeicWasm.HeicWasmError.tooLarge(w, h) {
            // Produce the canonical imageTooLarge error (always throws).
            try Limits.checkDimensions(width: w, height: h, format: .heic)
            throw DecodeError.imageTooLarge(format: .heic, detail: "\(w)x\(h)")
        } catch let HeicWasm.HeicWasmError.corrupt(detail) {
            throw DecodeError.corruptInput(format: .heic, detail: detail)
        }
    }
}
