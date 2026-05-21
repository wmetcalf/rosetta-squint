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
fn all_decoded_png_images_have_valid_shape() {
    for rel in testkit::list_valid_fixtures("png") {
        let bytes = testkit::read_fixture(&rel);
        let img = decode(&bytes).unwrap_or_else(|e| panic!("{}: {}", rel, e));
        assert!(img.width > 0, "{}", rel);
        assert!(img.height > 0, "{}", rel);
        assert_eq!(img.format, Format::Png, "{}", rel);
        let expected_bytes = img.width * img.height * img.channels.bytes_per_pixel();
        assert_eq!(img.data.len(), expected_bytes, "{}", rel);
    }
}

#[test]
fn all_decoded_gif_images_have_valid_shape() {
    for rel in testkit::list_valid_fixtures("gif") {
        let bytes = testkit::read_fixture(&rel);
        let img = decode(&bytes).unwrap_or_else(|e| panic!("{}: {}", rel, e));
        assert!(img.width > 0, "{}", rel);
        assert!(img.height > 0, "{}", rel);
        assert_eq!(img.format, Format::Gif, "{}", rel);
        let expected_bytes = img.width * img.height * img.channels.bytes_per_pixel();
        assert_eq!(img.data.len(), expected_bytes, "{}", rel);
    }
}

#[test]
fn all_decoded_jpeg_images_have_valid_shape() {
    for rel in testkit::list_valid_fixtures("jpeg") {
        let bytes = testkit::read_fixture(&rel);
        let img = decode(&bytes).unwrap_or_else(|e| panic!("{}: {}", rel, e));
        assert!(img.width > 0, "{}", rel);
        assert!(img.height > 0, "{}", rel);
        assert_eq!(img.format, Format::Jpeg, "{}", rel);
        assert_eq!(img.channels, Channels::Rgb, "JPEG always RGB");
        let expected = img.width * img.height * img.channels.bytes_per_pixel();
        assert_eq!(img.data.len(), expected, "{}", rel);
    }
}

#[test]
fn supported_formats_contains_bmp_png_gif_jpeg() {
    let supported = supported_formats();
    assert_eq!(supported.len(), 6);
    assert!(supported.contains(&Format::Bmp));
    assert!(supported.contains(&Format::Png));
    assert!(supported.contains(&Format::Gif));
    assert!(supported.contains(&Format::Jpeg));
    assert!(supported.contains(&Format::Webp));
    assert!(supported.contains(&Format::Tiff));
}

#[test]
fn all_decoded_webp_images_have_valid_shape() {
    for rel in testkit::list_valid_fixtures("webp") {
        let bytes = testkit::read_fixture(&rel);
        let img = decode(&bytes).unwrap_or_else(|e| panic!("{}: {}", rel, e));
        assert!(img.width > 0, "{}", rel);
        assert!(img.height > 0, "{}", rel);
        assert_eq!(img.format, Format::Webp, "{}", rel);
        let expected_bytes = img.width * img.height * img.channels.bytes_per_pixel();
        assert_eq!(img.data.len(), expected_bytes, "{}", rel);
    }
}

#[test]
fn all_decoded_tiff_images_have_valid_shape() {
    for rel in testkit::list_valid_fixtures("tiff") {
        let bytes = testkit::read_fixture(&rel);
        let img = decode(&bytes).unwrap_or_else(|e| panic!("{}: {}", rel, e));
        assert!(img.width > 0, "{}", rel);
        assert!(img.height > 0, "{}", rel);
        assert_eq!(img.format, Format::Tiff, "{}", rel);
        assert_eq!(img.channels, Channels::Rgb, "TIFF always RGB in v1: {}", rel);
        let expected_bytes = img.width * img.height * img.channels.bytes_per_pixel();
        assert_eq!(img.data.len(), expected_bytes, "{}", rel);
    }
}
