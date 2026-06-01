//! HEIC decoding via the shared libheif+libde265 WASM module (decode/wasm/),
//! run in wasmtime. The same WASM artifact is used by every rosetta-squint
//! port, so HEIC output is byte-identical across languages. Replaces the
//! libheif-rs system binding (which couldn't be byte-exact across libheif
//! versions/platforms). See decode/spec/HEIC_REPRODUCIBILITY.md.

use std::sync::OnceLock;

use wasmtime::{Engine, Instance, Linker, Memory, Module, Store, TypedFunc};
use wasmtime_wasi::p1::{self, WasiP1Ctx};
use wasmtime_wasi::WasiCtxBuilder;

static WASM: &[u8] = include_bytes!("wasm/libheif_decode.wasm");

// libheif enum values (heif.h)
const CS_RGB: i32 = 1;
const CHROMA_RGB: i32 = 10;
const CHROMA_RGBA: i32 = 11;
const CHAN_INTERLEAVED: i32 = 10;

pub struct HeicResult {
    pub width: usize,
    pub height: usize,
    pub channels: u8, // 3 or 4
    pub data: Vec<u8>,
}

#[derive(Debug)]
pub enum HeicWasmError {
    TooLarge { width: i64, height: i64 },
    Corrupt(String),
}

fn engine_module_linker() -> &'static (Engine, Module, Linker<WasiP1Ctx>) {
    static EML: OnceLock<(Engine, Module, Linker<WasiP1Ctx>)> = OnceLock::new();
    EML.get_or_init(|| {
        let engine = Engine::default();
        let module = Module::from_binary(&engine, WASM).expect("compile libheif wasm");
        let mut linker = Linker::new(&engine);
        p1::add_to_linker_sync(&mut linker, |c| c).expect("add wasi to linker");
        (engine, module, linker)
    })
}

pub fn decode_heic_wasm(bytes: &[u8], max_pixels: u64) -> Result<HeicResult, HeicWasmError> {
    let (engine, module, linker) = engine_module_linker();
    let wasi = WasiCtxBuilder::new().build_p1();
    let mut store = Store::new(engine, wasi);
    let instance = linker.instantiate(&mut store, module).map_err(corrupt)?;
    let mem = instance
        .get_memory(&mut store, "memory")
        .ok_or_else(|| HeicWasmError::Corrupt("no exported memory".into()))?;

    let init: TypedFunc<(), ()> = instance.get_typed_func(&mut store, "_initialize").map_err(corrupt)?;
    init.call(&mut store, ()).map_err(corrupt)?;

    let mut d = Driver { store, instance, mem };

    let data_ptr = d.malloc(bytes.len() as i32)?;
    d.mem.write(&mut d.store, data_ptr as usize, bytes).map_err(corrupt)?;
    let err_ptr = d.malloc(16)?;
    let ctx = d.call0("heif_context_alloc")?;
    d.call_void("heif_context_set_max_decoding_threads", &[ctx, 0])?;
    d.call_void("heif_context_read_from_memory", &[err_ptr, ctx, data_ptr, bytes.len() as i32, 0])?;
    if d.read_u32(err_ptr) != 0 {
        return Err(HeicWasmError::Corrupt(format!("read_from_memory heif_error {}", d.read_u32(err_ptr))));
    }
    let handle_pp = d.malloc(4)?;
    d.call_void("heif_context_get_primary_image_handle", &[err_ptr, ctx, handle_pp])?;
    if d.read_u32(err_ptr) != 0 {
        return Err(HeicWasmError::Corrupt(format!("get_primary_image_handle heif_error {}", d.read_u32(err_ptr))));
    }
    let handle = d.read_u32(handle_pp) as i32;

    let hw = d.call1("heif_image_handle_get_width", &[handle])? as i64;
    let hh = d.call1("heif_image_handle_get_height", &[handle])? as i64;
    if hw * hh > max_pixels as i64 {
        return Err(HeicWasmError::TooLarge { width: hw, height: hh });
    }

    let has_alpha = d.call1("heif_image_handle_has_alpha_channel", &[handle])? != 0;
    let (chroma, bpp, channels) = if has_alpha {
        (CHROMA_RGBA, 4usize, 4u8)
    } else {
        (CHROMA_RGB, 3usize, 3u8)
    };

    let img_pp = d.malloc(4)?;
    d.call_void("heif_decode_image", &[err_ptr, handle, img_pp, CS_RGB, chroma, 0])?;
    if d.read_u32(err_ptr) != 0 {
        return Err(HeicWasmError::Corrupt(format!("decode_image heif_error {}", d.read_u32(err_ptr))));
    }
    let img = d.read_u32(img_pp) as i32;

    let stride_p = d.malloc(4)?;
    let plane_ptr = d.call1("heif_image_get_plane_readonly", &[img, CHAN_INTERLEAVED, stride_p])? as usize;
    let stride = d.read_u32(stride_p) as usize;
    let width = d.call1("heif_image_get_width", &[img, CHAN_INTERLEAVED])? as usize;
    let height = d.call1("heif_image_get_height", &[img, CHAN_INTERLEAVED])? as usize;

    let row = width * bpp;
    if row > stride {
        return Err(HeicWasmError::Corrupt(format!("invalid stride {stride} < row {row}")));
    }
    let mut data = vec![0u8; row * height];
    for y in 0..height {
        let start = plane_ptr + y * stride;
        let dest = y * row;
        d.mem
            .read(&d.store, start, &mut data[dest..dest + row])
            .map_err(corrupt)?;
    }
    Ok(HeicResult { width, height, channels, data })
}

struct Driver {
    store: Store<WasiP1Ctx>,
    instance: Instance,
    mem: Memory,
}

impl Driver {
    fn read_u32(&self, ptr: i32) -> u32 {
        let p = ptr as usize;
        let b = self.mem.data(&self.store);
        u32::from_le_bytes([b[p], b[p + 1], b[p + 2], b[p + 3]])
    }
    fn malloc(&mut self, n: i32) -> Result<i32, HeicWasmError> {
        let p = self.call1("malloc", &[n])?;
        if p == 0 {
            return Err(HeicWasmError::Corrupt(format!("malloc({n}) failed (out of memory)")));
        }
        Ok(p)
    }
    fn call0(&mut self, name: &str) -> Result<i32, HeicWasmError> {
        let f: TypedFunc<(), i32> = self.instance.get_typed_func(&mut self.store, name).map_err(corrupt)?;
        f.call(&mut self.store, ()).map_err(corrupt)
    }
    fn call1(&mut self, name: &str, args: &[i32]) -> Result<i32, HeicWasmError> {
        let f = self
            .instance
            .get_func(&mut self.store, name)
            .ok_or_else(|| HeicWasmError::Corrupt(format!("no export {name}")))?;
        let params: Vec<wasmtime::Val> = args.iter().map(|&a| wasmtime::Val::I32(a)).collect();
        let mut results = [wasmtime::Val::I32(0)];
        f.call(&mut self.store, &params, &mut results).map_err(corrupt)?;
        Ok(results[0].i32().unwrap_or(0))
    }
    fn call_void(&mut self, name: &str, args: &[i32]) -> Result<(), HeicWasmError> {
        let f = self
            .instance
            .get_func(&mut self.store, name)
            .ok_or_else(|| HeicWasmError::Corrupt(format!("no export {name}")))?;
        let params: Vec<wasmtime::Val> = args.iter().map(|&a| wasmtime::Val::I32(a)).collect();
        f.call(&mut self.store, &params, &mut []).map_err(corrupt)
    }
}

fn corrupt<E: std::fmt::Display>(e: E) -> HeicWasmError {
    HeicWasmError::Corrupt(e.to_string())
}
