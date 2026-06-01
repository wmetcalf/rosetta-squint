import Foundation
import WasmKit
import WasmKitWASI

/// Decodes HEIC via the shared libheif+libde265 WASM module (decode/wasm/), run
/// in WasmKit (pure-Swift, no system libheif). The same WASM artifact is used by
/// every rosetta-squint port, so HEIC output is byte-identical across languages.
/// See decode/spec/HEIC_REPRODUCIBILITY.md.
internal enum HeicWasm {

    // libheif enum values (heif.h)
    private static let csRGB: UInt32 = 1
    private static let chromaRGB: UInt32 = 10
    private static let chromaRGBA: UInt32 = 11
    private static let chanInterleaved: UInt32 = 10

    struct Result {
        let width: Int
        let height: Int
        let channels: Int  // 3 or 4
        let data: [UInt8]
    }

    enum HeicWasmError: Error {
        case tooLarge(width: Int, height: Int)
        case corrupt(String)
    }

    private static let module: Module = {
        guard let url = Bundle.module.url(forResource: "libheif_decode", withExtension: "wasm"),
              let data = try? Data(contentsOf: url) else {
            fatalError("libheif_decode.wasm resource not found")
        }
        do {
            return try parseWasm(bytes: [UInt8](data))
        } catch {
            fatalError("failed to parse libheif_decode.wasm: \(error)")
        }
    }()

    static func decode(bytes: [UInt8], maxPixels: Int) throws -> Result {
        let engine = Engine()
        let store = Store(engine: engine)
        var imports = Imports()
        let wasi = try WASIBridgeToHost()
        wasi.link(to: &imports, store: store)
        let instance = try module.instantiate(store: store, imports: imports)
        _ = try instance.exports[function: "_initialize"]?()
        guard let mem = instance.exports[memory: "memory"] else {
            throw HeicWasmError.corrupt("no exported memory")
        }

        func call(_ name: String, _ args: [UInt32] = []) throws -> UInt32 {
            guard let f = instance.exports[function: name] else {
                throw HeicWasmError.corrupt("no export \(name)")
            }
            let r = try f(args.map { Value.i32($0) })
            return r.first?.i32 ?? 0
        }
        func readU32(_ ptr: UInt32) -> UInt32 {
            let d = mem.data
            let p = Int(ptr)
            var v = UInt32(d[p])
            v |= UInt32(d[p + 1]) << 8
            v |= UInt32(d[p + 2]) << 16
            v |= UInt32(d[p + 3]) << 24
            return v
        }

        let dataPtr = try call("malloc", [UInt32(bytes.count)])
        mem.withUnsafeMutableBufferPointer(offset: UInt(dataPtr), count: bytes.count) { buf in
            buf.copyBytes(from: bytes)
        }
        let errPtr = try call("malloc", [16])
        let ctx = try call("heif_context_alloc")
        _ = try call("heif_context_set_max_decoding_threads", [ctx, 0])
        _ = try call("heif_context_read_from_memory", [errPtr, ctx, dataPtr, UInt32(bytes.count), 0])
        if readU32(errPtr) != 0 {
            throw HeicWasmError.corrupt("read_from_memory heif_error \(readU32(errPtr))")
        }
        let handlePP = try call("malloc", [4])
        _ = try call("heif_context_get_primary_image_handle", [errPtr, ctx, handlePP])
        if readU32(errPtr) != 0 {
            throw HeicWasmError.corrupt("get_primary_image_handle heif_error \(readU32(errPtr))")
        }
        let handle = readU32(handlePP)

        let hw = Int(try call("heif_image_handle_get_width", [handle]))
        let hh = Int(try call("heif_image_handle_get_height", [handle]))
        if hw * hh > maxPixels {
            throw HeicWasmError.tooLarge(width: hw, height: hh)
        }

        let hasAlpha = try call("heif_image_handle_has_alpha_channel", [handle]) != 0
        let chroma = hasAlpha ? chromaRGBA : chromaRGB
        let bpp = hasAlpha ? 4 : 3
        let channels = hasAlpha ? 4 : 3

        let imgPP = try call("malloc", [4])
        _ = try call("heif_decode_image", [errPtr, handle, imgPP, csRGB, chroma, 0])
        if readU32(errPtr) != 0 {
            throw HeicWasmError.corrupt("decode_image heif_error \(readU32(errPtr))")
        }
        let img = readU32(imgPP)

        let strideP = try call("malloc", [4])
        let planePtr = Int(try call("heif_image_get_plane_readonly", [img, chanInterleaved, strideP]))
        let stride = Int(readU32(strideP))
        let w = Int(try call("heif_image_get_width", [img, chanInterleaved]))
        let h = Int(try call("heif_image_get_height", [img, chanInterleaved]))

        let row = w * bpp
        let heap = mem.data
        var out = [UInt8](repeating: 0, count: row * h)
        out.withUnsafeMutableBufferPointer { dst in
            for y in 0..<h {
                let src = planePtr + y * stride
                for x in 0..<row {
                    dst[y * row + x] = heap[src + x]
                }
            }
        }
        return Result(width: w, height: h, channels: channels, data: out)
    }
}
