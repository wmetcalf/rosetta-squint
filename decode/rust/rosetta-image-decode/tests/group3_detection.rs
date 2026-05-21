use rosetta_image_decode::*;

#[path = "testkit.rs"]
mod testkit;

#[test]
fn detects_all_valid_bmp() {
    for rel in testkit::list_valid_fixtures("bmp") {
        let bytes = testkit::read_fixture(&rel);
        assert_eq!(Some(Format::Bmp), detect_format(&bytes), "fixture {}", rel);
    }
}

#[test]
fn rejects_bad_signature() {
    let bytes = testkit::read_fixture("bmp/invalid/bad-signature.bmp");
    assert!(detect_format(&bytes).is_none());
}

#[test]
fn supported_formats_contains_bmp() {
    assert!(supported_formats().contains(&Format::Bmp));
}

#[test]
fn detects_all_valid_png() {
    for rel in testkit::list_valid_fixtures("png") {
        let bytes = testkit::read_fixture(&rel);
        assert_eq!(Some(Format::Png), detect_format(&bytes), "fixture {}", rel);
    }
}

#[test]
fn supported_formats_contains_png() {
    assert!(supported_formats().contains(&Format::Png));
}
