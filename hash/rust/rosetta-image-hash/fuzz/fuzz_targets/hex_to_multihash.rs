#![no_main]
use libfuzzer_sys::fuzz_target;
use rosetta_image_hash::hex_to_multihash;

// Property: hex_to_multihash never panics on arbitrary (possibly malformed)
// comma-separated hex segments. Exercises the comma-split path, per-segment
// hex parsing, and the empty / mixed-valid-and-invalid edge cases that the
// spec test corpus doesn't explicitly cover.
fuzz_target!(|data: &[u8]| {
    if let Ok(s) = std::str::from_utf8(data) {
        let _ = hex_to_multihash(s);
    }
});
