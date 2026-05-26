#![no_main]
use libfuzzer_sys::fuzz_target;
use rosetta_squint_hash::hex_to_hash;

// Property: hex_to_hash never panics on arbitrary input. Valid hex returns Ok,
// invalid hex returns a typed error — but no panic, no OOM from unchecked
// length arithmetic, no integer overflow on the sqrt-based shape inference.
fuzz_target!(|data: &[u8]| {
    // Interpret the fuzzer bytes as a (possibly hostile) hex string.
    if let Ok(s) = std::str::from_utf8(data) {
        let _ = hex_to_hash(s);
    }
});
