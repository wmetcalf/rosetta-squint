use rosetta_image_decode::*;

#[path = "testkit.rs"]
mod testkit;

#[test]
fn all_decoded_images_have_valid_shape() {
    for rel in testkit::list_valid_fixtures("bmp") {
        let bytes = testkit::read_fixture(&rel);
        let img = decode(&bytes).unwrap_or_else(|e| panic!("{}: {}", rel, e));
        assert!(img.width > 0, "{}", rel);
        assert!(img.height > 0, "{}", rel);
        assert_eq!(img.format, Format::Bmp, "{}", rel);
        let expected_bytes = img.width * img.height * img.channels.bytes_per_pixel();
        assert_eq!(img.data.len(), expected_bytes, "{}", rel);
    }
}

#[test]
fn supported_formats_contains_only_bmp() {
    let supported = supported_formats();
    assert_eq!(supported.len(), 1);
    assert!(supported.contains(&Format::Bmp));
}
