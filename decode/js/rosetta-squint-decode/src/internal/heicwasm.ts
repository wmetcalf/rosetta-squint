// HEIC decoding via the shared libheif+libde265 WASM module (decode/wasm/),
// the same artifact every rosetta-squint port uses, so HEIC output is
// byte-identical across languages. Replaces libheif-js, whose emscripten build
// disables the HEVC deblocking + SAO filters (diverging ±1-2 px). See
// decode/spec/HEIC_REPRODUCIBILITY.md.
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { DecodeError } from "../errors.js";

// libheif enum values (heif.h)
const CS_RGB = 1;
const CHROMA_RGB = 10;
const CHROMA_RGBA = 11;
const CHAN_INTERLEAVED = 10;

const EBADF = 8;

let modulePromise: Promise<WebAssembly.Module> | null = null;
function compileModule(): Promise<WebAssembly.Module> {
  if (!modulePromise) {
    const wasmPath = fileURLToPath(new URL("./libheif_decode.wasm", import.meta.url));
    modulePromise = WebAssembly.compile(readFileSync(wasmPath));
  }
  return modulePromise;
}

export interface HeicResult {
  width: number;
  height: number;
  channels: 3 | 4;
  data: Uint8Array;
}

// Minimal WASI host for an in-memory decode (no real file/clock/env use).
function wasiImports(getMem: () => WebAssembly.Memory) {
  const dv = () => new DataView(getMem().buffer);
  const stubs: Record<string, (...a: number[]) => number> = {
    environ_get: () => 0,
    environ_sizes_get: (countPtr: number, sizePtr: number) => {
      const d = dv(); d.setUint32(countPtr, 0, true); d.setUint32(sizePtr, 0, true); return 0;
    },
    clock_time_get: (_id: number, _prec: number, outPtr: number) => {
      dv().setBigUint64(outPtr, 0n, true); return 0;
    },
    fd_close: () => 0,
    fd_fdstat_get: () => EBADF,
    fd_prestat_get: () => EBADF,
    fd_prestat_dir_name: () => EBADF,
    fd_read: (_fd: number, _iovs: number, _n: number, nreadPtr: number) => {
      dv().setUint32(nreadPtr, 0, true); return 0;
    },
    fd_seek: () => 0,
    fd_write: (_fd: number, iovsPtr: number, iovsLen: number, nwrittenPtr: number) => {
      const d = dv(); let total = 0;
      for (let i = 0; i < iovsLen; i++) total += d.getUint32(iovsPtr + i * 8 + 4, true);
      d.setUint32(nwrittenPtr, total, true); return 0;
    },
    path_unlink_file: () => 0,
    proc_exit: (code: number) => { throw new Error(`wasm proc_exit ${code}`); },
  };
  // Any wasi import we didn't enumerate returns success (0).
  return new Proxy(stubs, { get: (t, p: string) => (p in t ? t[p] : () => 0) });
}

export async function decodeHeicWasm(bytes: Uint8Array, maxPixels: number): Promise<HeicResult> {
  const module = await compileModule();
  let memory!: WebAssembly.Memory;
  const instance = await WebAssembly.instantiate(module, {
    wasi_snapshot_preview1: wasiImports(() => memory) as unknown as WebAssembly.ModuleImports,
  });
  const ex = instance.exports as Record<string, CallableFunction> & { memory: WebAssembly.Memory };
  memory = ex.memory;
  (ex._initialize as () => void)();

  const dv = () => new DataView(memory.buffer);
  const u8 = () => new Uint8Array(memory.buffer);
  const call = (name: string, ...args: number[]): number => (ex[name] as CallableFunction)(...args) >>> 0;
  const malloc = (n: number) => {
    const p = call("malloc", n);
    if (p === 0) throw new DecodeError("corruptInput", "heic", `malloc(${n}) failed (out of memory)`);
    return p;
  };
  const errAt = (p: number) => dv().getUint32(p, true);

  const dataPtr = malloc(bytes.length);
  u8().set(bytes, dataPtr);
  const errPtr = malloc(16);
  const ctx = call("heif_context_alloc");
  call("heif_context_set_max_decoding_threads", ctx, 0);
  call("heif_context_read_from_memory", errPtr, ctx, dataPtr, bytes.length, 0);
  if (errAt(errPtr) !== 0) {
    throw new DecodeError("corruptInput", "heic", `read_from_memory heif_error ${errAt(errPtr)}`);
  }
  const handlePP = malloc(4);
  call("heif_context_get_primary_image_handle", errPtr, ctx, handlePP);
  if (errAt(errPtr) !== 0) {
    throw new DecodeError("corruptInput", "heic", `get_primary_image_handle heif_error ${errAt(errPtr)}`);
  }
  const handle = dv().getUint32(handlePP, true) >>> 0;

  // DoS guard on HEVC dimensions before the full decode.
  const hw = (ex.heif_image_handle_get_width as CallableFunction)(handle) | 0;
  const hh = (ex.heif_image_handle_get_height as CallableFunction)(handle) | 0;
  if (hw * hh > maxPixels) {
    throw new DecodeError("imageTooLarge", "heic",
      `declared dimensions ${hw}x${hh} = ${hw * hh} pixels exceeds MAX_PIXELS = ${maxPixels}`);
  }

  const hasAlpha = call("heif_image_handle_has_alpha_channel", handle) !== 0;
  const chroma = hasAlpha ? CHROMA_RGBA : CHROMA_RGB;
  const bpp = hasAlpha ? 4 : 3;
  const channels: 3 | 4 = hasAlpha ? 4 : 3;

  const imgPP = malloc(4);
  call("heif_decode_image", errPtr, handle, imgPP, CS_RGB, chroma, 0);
  if (errAt(errPtr) !== 0) {
    throw new DecodeError("corruptInput", "heic", `decode_image heif_error ${errAt(errPtr)}`);
  }
  const img = dv().getUint32(imgPP, true) >>> 0;

  const strideP = malloc(4);
  const planePtr = call("heif_image_get_plane_readonly", img, CHAN_INTERLEAVED, strideP);
  const stride = dv().getUint32(strideP, true);
  const w = (ex.heif_image_get_width as CallableFunction)(img, CHAN_INTERLEAVED) | 0;
  const h = (ex.heif_image_get_height as CallableFunction)(img, CHAN_INTERLEAVED) | 0;
  const row = w * bpp;
  if (row > stride) {
    throw new DecodeError("corruptInput", "heic", `invalid stride ${stride} < row ${row}`);
  }
  const heap = u8();
  if (planePtr + (h - 1) * stride + row > heap.length) {
    throw new DecodeError("corruptInput", "heic", "plane read out of range");
  }
  const out = new Uint8Array(row * h);
  for (let y = 0; y < h; y++) {
    out.set(heap.subarray(planePtr + y * stride, planePtr + y * stride + row), y * row);
  }
  return { width: w, height: h, channels, data: out };
}
