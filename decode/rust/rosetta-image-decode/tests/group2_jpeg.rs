use rosetta_image_decode::*;

#[path = "testkit.rs"]
mod testkit;

#[test]
fn byte_exact_all_jpeg() {
    let fixtures = testkit::list_valid_fixtures("jpeg");
    assert!(!fixtures.is_empty(), "should have JPEG fixtures");

    let mut failures: Vec<String> = Vec::new();
    for rel in &fixtures {
        let input = testkit::read_fixture(rel);
        match decode(&input) {
            Ok(got) => {
                let want = testkit::read_golden(rel);
                if got.width != want.width || got.height != want.height || got.channels.bytes_per_pixel() != want.channels {
                    failures.push(format!("{}: shape {}x{}c{} != {}x{}c{}",
                        rel, got.width, got.height, got.channels.bytes_per_pixel(),
                        want.width, want.height, want.channels));
                    continue;
                }
                if got.data.len() != want.pixels.len() {
                    failures.push(format!("{}: pixel byte count {} != {}", rel, got.data.len(), want.pixels.len()));
                    continue;
                }
                for i in 0..got.data.len() {
                    if got.data[i] != want.pixels[i] {
                        failures.push(format!("{}: pixel byte {} got={} want={}", rel, i, got.data[i], want.pixels[i]));
                        break;
                    }
                }
            }
            Err(e) => failures.push(format!("{}: threw {:?}: {}", rel, e.kind, e.detail)),
        }
    }
    if !failures.is_empty() {
        panic!("{} JPEG byte-exact failures:\n  {}", failures.len(), failures.join("\n  "));
    }
}
