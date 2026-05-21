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

#[test]
fn detects_all_valid_gif() {
    for rel in testkit::list_valid_fixtures("gif") {
        let bytes = testkit::read_fixture(&rel);
        assert_eq!(Some(Format::Gif), detect_format(&bytes), "fixture {}", rel);
    }
}

#[test]
fn rejects_bad_magic_gif() {
    let bytes = testkit::read_fixture("gif/invalid/bad-magic.gif");
    assert!(detect_format(&bytes).is_none());
}

#[test]
fn supported_formats_contains_gif() {
    assert!(supported_formats().contains(&Format::Gif));
}

#[test]
fn detects_all_valid_jpeg() {
    for rel in testkit::list_valid_fixtures("jpeg") {
        let bytes = testkit::read_fixture(&rel);
        assert_eq!(Some(Format::Jpeg), detect_format(&bytes), "fixture {}", rel);
    }
}

#[test]
fn supported_formats_contains_jpeg() {
    assert!(supported_formats().contains(&Format::Jpeg));
}

#[test]
fn detects_all_valid_webp() {
    for rel in testkit::list_valid_fixtures("webp") {
        let bytes = testkit::read_fixture(&rel);
        assert_eq!(Some(Format::Webp), detect_format(&bytes), "fixture {}", rel);
    }
}

#[test]
fn rejects_bad_magic_webp() {
    let bytes = testkit::read_fixture("webp/invalid/bad-magic.webp");
    assert!(detect_format(&bytes).is_none());
}

#[test]
fn supported_formats_contains_webp() {
    assert!(supported_formats().contains(&Format::Webp));
}

#[test]
fn detects_all_valid_tiff() {
    for rel in testkit::list_valid_fixtures("tiff") {
        let bytes = testkit::read_fixture(&rel);
        assert_eq!(Some(Format::Tiff), detect_format(&bytes), "fixture {}", rel);
    }
}

#[test]
fn rejects_bad_magic_tiff() {
    let bytes = testkit::read_fixture("tiff/invalid/bad-magic.tif");
    assert!(detect_format(&bytes).is_none());
}

#[test]
fn supported_formats_contains_tiff() {
    assert!(supported_formats().contains(&Format::Tiff));
}
