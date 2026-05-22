#![no_main]
use libfuzzer_sys::fuzz_target;
use rosetta_image_decode::decode;

const PREFIXES: &[&[u8]] = &[
    &[0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A],  // PNG
    &[0xFF, 0xD8, 0xFF, 0xE0],                          // JPEG (JFIF)
    &[0x47, 0x49, 0x46, 0x38, 0x39, 0x61],              // GIF89a
    &[0x42, 0x4D],                                      // BMP
    &[0x52, 0x49, 0x46, 0x46, 0, 0, 0, 0, 0x57, 0x45, 0x42, 0x50],  // RIFF...WEBP
    &[0x49, 0x49, 0x2A, 0x00],                          // TIFF LE
    &[0x00, 0x00, 0x00, 0x18, 0x66, 0x74, 0x79, 0x70, 0x68, 0x65, 0x69, 0x63],  // HEIC
];

fuzz_target!(|data: &[u8]| {
    if data.is_empty() { return; }
    let prefix = PREFIXES[(data[0] as usize) % PREFIXES.len()];
    let mut combined = Vec::with_capacity(prefix.len() + data.len());
    combined.extend_from_slice(prefix);
    combined.extend_from_slice(&data[1..]);
    let _ = decode(&combined);
});
