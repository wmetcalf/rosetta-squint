import Foundation
import Cheif

internal enum HEICDecoder {
    static func decode(bytes: [UInt8]) throws -> DecodedImage {
        // Sniff the container's primary-item ispe dimensions BEFORE invoking
        // libheif. libheif returns dimensions from the underlying HEVC
        // bitstream rather than the container's ispe, so a patched ispe is
        // never visible via heif_image_handle_get_width/height — without
        // this pre-check the file decodes at its HEVC dimensions and the
        // imageTooLarge guard never fires. Spec §3.1.
        if let dims = DimensionSniff.sniffHeicDimensions(bytes) {
            try Limits.checkDimensions(width: dims.0, height: dims.1, format: .heic)
        }

        guard let ctx = heif_context_alloc() else {
            throw DecodeError.corruptInput(format: .heic, detail: "heif_context_alloc failed")
        }
        defer { heif_context_free(ctx) }

        // Use copying variant to avoid Swift array lifetime hazards across the C call.
        let readErr = bytes.withUnsafeBufferPointer { buf -> heif_error in
            heif_context_read_from_memory(ctx, buf.baseAddress, buf.count, nil)
        }
        if readErr.code.rawValue != heif_error_Ok.rawValue {
            let msg = readErr.message.map { String(cString: $0) } ?? "unknown error"
            throw DecodeError.corruptInput(format: .heic, detail: "heif_context_read_from_memory: \(msg)")
        }

        var handle: OpaquePointer? = nil
        let handleErr = heif_context_get_primary_image_handle(ctx, &handle)
        if handleErr.code.rawValue != heif_error_Ok.rawValue || handle == nil {
            let msg = handleErr.message.map { String(cString: $0) } ?? "unknown error"
            throw DecodeError.corruptInput(format: .heic, detail: "heif_context_get_primary_image_handle: \(msg)")
        }
        defer { heif_image_handle_release(handle) }

        let handleWidth = Int(heif_image_handle_get_width(handle))
        let handleHeight = Int(heif_image_handle_get_height(handle))
        try Limits.checkDimensions(width: handleWidth, height: handleHeight, format: .heic)

        let hasAlpha = heif_image_handle_has_alpha_channel(handle) != 0
        let chroma: heif_chroma = hasAlpha ? heif_chroma_interleaved_RGBA : heif_chroma_interleaved_RGB
        let bpp = hasAlpha ? 4 : 3

        var img: OpaquePointer? = nil
        let decodeErr = heif_decode_image(handle, &img, heif_colorspace_RGB, chroma, nil)
        if decodeErr.code.rawValue != heif_error_Ok.rawValue || img == nil {
            let msg = decodeErr.message.map { String(cString: $0) } ?? "unknown error"
            throw DecodeError.corruptInput(format: .heic, detail: "heif_decode_image: \(msg)")
        }
        defer { heif_image_release(img) }

        let width = Int(heif_image_get_width(img, heif_channel_interleaved))
        let height = Int(heif_image_get_height(img, heif_channel_interleaved))

        guard width > 0, height > 0 else {
            throw DecodeError.corruptInput(format: .heic, detail: "zero dimensions in HEIC image")
        }

        // Validate post-decode plane dimensions match the handle dimensions we
        // capacity-checked above. A mismatch (e.g. a corrupt input that causes
        // libheif to return a smaller plane than advertised) would otherwise
        // cause the row-copy below to OOB-read planePtr or under-fill out.
        guard width == handleWidth, height == handleHeight else {
            throw DecodeError.corruptInput(
                format: .heic,
                detail: "plane dimensions \(width)x\(height) do not match handle dimensions \(handleWidth)x\(handleHeight)"
            )
        }

        var stride: Int32 = 0
        guard let planePtr = heif_image_get_plane_readonly(img, heif_channel_interleaved, &stride) else {
            throw DecodeError.corruptInput(format: .heic, detail: "heif_image_get_plane_readonly returned nil")
        }

        var out = [UInt8](repeating: 0, count: width * height * bpp)
        for y in 0..<height {
            let srcRow = planePtr.advanced(by: Int(stride) * y)
            let dstStart = y * width * bpp
            _ = out.withUnsafeMutableBufferPointer { dst in
                memcpy(dst.baseAddress!.advanced(by: dstStart), srcRow, width * bpp)
            }
        }

        let channels: Channels = hasAlpha ? .rgba : .rgb
        return DecodedImage(
            width: width,
            height: height,
            data: out,
            channels: channels,
            format: .heic
        )
    }
}
