import { PNG } from "pngjs";

import type { RgbImage } from "./hash.js";

/**
 * Decode PNG bytes into an RgbImage with channels=4 (RGBA from pngjs).
 * Internal algorithms composite alpha against opaque black per PIL convert('RGB').
 *
 * Node-friendly path: pass a Uint8Array or Node Buffer; we wrap appropriately.
 * pngjs ships only a Node-flavored sync API, so this helper is Node-only.
 * Browser callers should use `canvas.getImageData(...)` and construct RgbImage themselves.
 */
export function decodePng(bytes: Uint8Array): RgbImage {
	// PNG.sync.read needs a Node Buffer; wrap the Uint8Array
	const buf = Buffer.from(bytes.buffer, bytes.byteOffset, bytes.byteLength);
	const png = PNG.sync.read(buf);
	// png.data is a Buffer (Uint8Array view) of length width*height*4 (RGBA)
	return {
		width: png.width,
		height: png.height,
		data: new Uint8Array(png.data),
		channels: 4,
	};
}
