// Package heicwasm decodes HEIC images via a bundled libheif+libde265 WASM
// module run in wazero (pure-Go, no cgo, no system libheif). The same WASM
// artifact is used by every rosetta-squint port, so all ports produce
// byte-identical HEIC pixels. See decode/wasm/ for the build.
package heicwasm

import (
	"context"
	_ "embed"
	"errors"
	"fmt"
	"sync"

	"github.com/tetratelabs/wazero"
	"github.com/tetratelabs/wazero/imports/wasi_snapshot_preview1"
)

//go:embed libheif_decode.wasm
var wasmBinary []byte

// libheif enum values (heif.h)
const (
	csRGB        = 1  // heif_colorspace_RGB
	chromaRGB    = 10 // heif_chroma_interleaved_RGB
	chromaRGBA   = 11 // heif_chroma_interleaved_RGBA
	chanInterlvd = 10 // heif_channel_interleaved
)

// Result is a decoded HEIC image: interleaved RGB (channels==3) or RGBA (==4).
type Result struct {
	Width, Height, Channels int
	Data                    []byte
}

// ErrTooLarge is returned (wrapped, with the offending dimensions) when the
// primary image's HEVC dimensions exceed maxPixels — checked before the full
// decode so a declared-huge image is never materialized.
var ErrTooLarge = errors.New("heicwasm: image dimensions exceed limit")

var (
	rt   wazero.Runtime
	code wazero.CompiledModule
	once sync.Once
	mu   sync.Mutex // module instances share one runtime; serialize decodes
	initErr error
)

func ensure(ctx context.Context) error {
	once.Do(func() {
		// Non-shared-memory WASM: standard wasi reactor, no threads feature.
		rt = wazero.NewRuntime(ctx)
		wasi_snapshot_preview1.MustInstantiate(ctx, rt)
		c, err := rt.CompileModule(ctx, wasmBinary)
		if err != nil {
			initErr = err
			return
		}
		code = c
	})
	return initErr
}

// Decode decodes a HEIC byte stream to interleaved RGB/RGBA. A non-nil error
// means libheif rejected the input (corrupt/unsupported); the caller maps it to
// the appropriate decode error. Each call uses a fresh module instance so a
// trap on malformed input cannot poison later decodes.
func Decode(b []byte, maxPixels int64) (Result, error) {
	ctx := context.Background()
	if err := ensure(ctx); err != nil {
		return Result{}, err
	}
	mu.Lock()
	defer mu.Unlock()

	mod, err := rt.InstantiateModule(ctx, code,
		wazero.NewModuleConfig().WithName("").WithStartFunctions("_initialize"))
	if err != nil {
		return Result{}, fmt.Errorf("heicwasm: instantiate: %w", err)
	}
	defer func() { _ = mod.Close(ctx) }()
	mem := mod.Memory()

	var callErr error
	call := func(name string, args ...uint64) uint64 {
		if callErr != nil {
			return 0
		}
		res, e := mod.ExportedFunction(name).Call(ctx, args...)
		if e != nil {
			callErr = fmt.Errorf("heicwasm: %s: %w", name, e)
			return 0
		}
		if len(res) > 0 {
			return res[0]
		}
		return 0
	}
	malloc := func(n uint32) uint32 {
		p := uint32(call("malloc", uint64(n)))
		if callErr == nil && p == 0 {
			callErr = fmt.Errorf("heicwasm: malloc(%d) failed (out of memory)", n)
		}
		return p
	}
	errAt := func(p uint32) uint32 { v, _ := mem.ReadUint32Le(p); return v }

	dataPtr := malloc(uint32(len(b)))
	if callErr != nil {
		return Result{}, callErr
	}
	mem.Write(dataPtr, b)
	errPtr := malloc(16)
	ctxP := uint32(call("heif_context_alloc"))
	call("heif_context_set_max_decoding_threads", uint64(ctxP), 0)
	call("heif_context_read_from_memory", uint64(errPtr), uint64(ctxP), uint64(dataPtr), uint64(len(b)), 0)
	if callErr != nil {
		return Result{}, callErr
	}
	if c := errAt(errPtr); c != 0 {
		return Result{}, fmt.Errorf("heicwasm: read_from_memory: heif_error %d", c)
	}
	handlePP := malloc(4)
	call("heif_context_get_primary_image_handle", uint64(errPtr), uint64(ctxP), uint64(handlePP))
	if c := errAt(errPtr); c != 0 {
		return Result{}, fmt.Errorf("heicwasm: get_primary_image_handle: heif_error %d", c)
	}
	handle, _ := mem.ReadUint32Le(handlePP)

	// DoS guard: reject before decoding if the HEVC dimensions are too large.
	hw := int64(int32(call("heif_image_handle_get_width", uint64(handle))))
	hh := int64(int32(call("heif_image_handle_get_height", uint64(handle))))
	if callErr != nil {
		return Result{}, callErr
	}
	if hw*hh > maxPixels {
		return Result{}, fmt.Errorf("%w: %dx%d", ErrTooLarge, hw, hh)
	}

	hasAlpha := call("heif_image_handle_has_alpha_channel", uint64(handle)) != 0
	chroma, bpp, channels := uint64(chromaRGB), 3, 3
	if hasAlpha {
		chroma, bpp, channels = chromaRGBA, 4, 4
	}
	imgPP := malloc(4)
	call("heif_decode_image", uint64(errPtr), uint64(handle), uint64(imgPP), csRGB, chroma, 0)
	if callErr != nil {
		return Result{}, callErr
	}
	if c := errAt(errPtr); c != 0 {
		return Result{}, fmt.Errorf("heicwasm: decode_image: heif_error %d", c)
	}
	img, _ := mem.ReadUint32Le(imgPP)

	strideP := malloc(4)
	planePtr := uint32(call("heif_image_get_plane_readonly", uint64(img), chanInterlvd, uint64(strideP)))
	stride, _ := mem.ReadUint32Le(strideP)
	w := uint32(call("heif_image_get_width", uint64(img), chanInterlvd))
	h := uint32(call("heif_image_get_height", uint64(img), chanInterlvd))
	if callErr != nil {
		return Result{}, callErr
	}
	row := w * uint32(bpp)
	if row > stride {
		return Result{}, fmt.Errorf("heicwasm: invalid stride %d < row %d", stride, row)
	}
	raw, ok := mem.Read(planePtr, stride*h)
	if !ok {
		return Result{}, fmt.Errorf("heicwasm: plane read out of range")
	}
	out := make([]byte, row*h)
	for y := uint32(0); y < h; y++ {
		copy(out[y*row:(y+1)*row], raw[y*stride:y*stride+row])
	}
	return Result{Width: int(w), Height: int(h), Channels: channels, Data: out}, nil
}
