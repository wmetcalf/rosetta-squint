use rosetta_squint_hash::{hex_to_flathash, hex_to_hash, ImageHashError};

#[test]
fn hex_to_hash_and_back() {
    let hex = "ffd7918181c9ffff";
    let h = hex_to_hash(hex).expect("parse");
    assert_eq!(h.bit_count(), 64);
    assert_eq!(h.to_hex(), hex);
}

#[test]
fn hex_to_flathash_and_back() {
    let hex = "0123456789abcd";
    let h = hex_to_flathash(hex, 4).expect("parse");
    assert_eq!(h.bit_count(), 14 * 4);
    assert_eq!(h.to_hex(), hex);
}

#[test]
fn hex_to_hash_non_square_errors() {
    match hex_to_hash("12345") {
        Err(ImageHashError::InvalidHex(_)) => (),
        other => panic!("expected InvalidHex, got {other:?}"),
    }
}

#[test]
fn hex_to_hash_invalid_chars_errors() {
    match hex_to_hash("xyz!") {
        Err(ImageHashError::InvalidHex(_)) => (),
        other => panic!("expected InvalidHex, got {other:?}"),
    }
}

#[test]
fn round_trip_all_zeros() {
    let hex = "0000000000000000";
    let h = hex_to_hash(hex).expect("parse");
    assert_eq!(h.to_hex(), hex);
}
