#![no_main]
use libfuzzer_sys::fuzz_target;
use rosetta_image_decode::decode;

fuzz_target!(|data: &[u8]| {
    // Should never panic, even on totally random input.
    let _ = decode(data);
});
