#![no_main]
use libfuzzer_sys::fuzz_target;
use rosetta_squint_hash::hex_to_flathash;

// Property: hex_to_flathash never panics on arbitrary (hex, hash_size).
// Splits the fuzzer input into a hash_size byte and the remainder as the hex
// string. Catches integer overflow on `14 * secondAxis`, length-mismatch
// crashes, and any other unchecked arithmetic.
fuzz_target!(|data: &[u8]| {
    if data.is_empty() { return; }
    // Use the first byte as hash_size in 0..=255 (we want to exercise both
    // valid small values and edge zeros / large values).
    let hash_size = data[0] as usize;
    if let Ok(s) = std::str::from_utf8(&data[1..]) {
        let _ = hex_to_flathash(s, hash_size);
    }
});
