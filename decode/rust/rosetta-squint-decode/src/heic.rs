use crate::error::{DecodeError, DecodeErrorKind};
use crate::heicwasm::{decode_heic_wasm, HeicWasmError};
use crate::limits::{check_dimensions, MAX_PIXELS};
use crate::types::{Channels, DecodedImage, Format};

/// Decode HEIC via the shared libheif+libde265 WASM module (src/wasm/), run in
/// wasmtime — no system libheif. The same WASM artifact is shared by every port
/// so HEIC output is byte-identical across languages. See decode/wasm/ and
/// decode/spec/HEIC_REPRODUCIBILITY.md.
pub(crate) fn decode_heic(bytes: &[u8]) -> Result<DecodedImage, DecodeError> {
    // Sniff the container's primary-item ispe dimensions BEFORE decoding.
    // libheif reports HEVC-bitstream dimensions, not the container's ispe, so a
    // patched ispe is only caught here. Spec §3.1.
    if let Some((w, h)) = crate::dimension_sniff::sniff_heic_dimensions(bytes) {
        check_dimensions(w, h, Format::Heic)?;
    }

    match decode_heic_wasm(bytes, MAX_PIXELS as u64) {
        Ok(res) => Ok(DecodedImage {
            width: res.width,
            height: res.height,
            data: res.data,
            channels: if res.channels == 4 {
                Channels::Rgba
            } else {
                Channels::Rgb
            },
            format: Format::Heic,
        }),
        Err(HeicWasmError::TooLarge { width, height }) => {
            // Reuse check_dimensions for the canonical imageTooLarge message.
            check_dimensions(width as usize, height as usize, Format::Heic)?;
            unreachable!("TooLarge implies check_dimensions fails")
        }
        Err(HeicWasmError::Corrupt(detail)) => Err(DecodeError::new(
            DecodeErrorKind::CorruptInput,
            Some(Format::Heic),
            detail,
        )),
    }
}
