use rosetta_image_decode::*;

#[path = "testkit.rs"]
mod testkit;

#[test]
fn invalid_bmp_fixtures() {
    let errors = testkit::read_errors();
    let mut failures: Vec<String> = Vec::new();
    for (key, expected) in errors.iter() {
        if !key.starts_with("bmp/") {
            continue;
        }
        let input = testkit::read_fixture(key);
        match decode(&input) {
            Ok(_) => failures.push(format!(
                "{}: decode succeeded, expected {}",
                key, expected.expected_kind
            )),
            Err(e) => {
                if e.kind.as_str() != expected.expected_kind {
                    failures.push(format!(
                        "{}: kind {} != {}",
                        key, e.kind, expected.expected_kind
                    ));
                    continue;
                }
                if !expected.expected_detail_substring.is_empty()
                    && !e.detail.contains(&expected.expected_detail_substring)
                {
                    failures.push(format!(
                        "{}: detail '{}' does not contain '{}'",
                        key, e.detail, expected.expected_detail_substring
                    ));
                }
            }
        }
    }
    if !failures.is_empty() {
        panic!(
            "{} Group-4 failures:\n  {}",
            failures.len(),
            failures.join("\n  ")
        );
    }
}

#[test]
fn invalid_png_fixtures() {
    let errors = testkit::read_errors();
    let mut failures: Vec<String> = Vec::new();
    for (key, expected) in errors.iter() {
        if !key.starts_with("png/") {
            continue;
        }
        let input = testkit::read_fixture(key);
        match decode(&input) {
            Ok(_) => failures.push(format!(
                "{}: decode succeeded, expected {}",
                key, expected.expected_kind
            )),
            Err(e) => {
                if e.kind.as_str() != expected.expected_kind {
                    failures.push(format!(
                        "{}: kind {} != {}",
                        key, e.kind, expected.expected_kind
                    ));
                    continue;
                }
                if !expected.expected_detail_substring.is_empty()
                    && !e.detail.contains(&expected.expected_detail_substring)
                {
                    failures.push(format!(
                        "{}: detail '{}' does not contain '{}'",
                        key, e.detail, expected.expected_detail_substring
                    ));
                }
            }
        }
    }
    if !failures.is_empty() {
        panic!(
            "{} Group-4 PNG failures:\n  {}",
            failures.len(),
            failures.join("\n  ")
        );
    }
}

#[test]
fn invalid_gif_fixtures() {
    let errors = testkit::read_errors();
    let mut failures: Vec<String> = Vec::new();
    for (key, expected) in errors.iter() {
        if !key.starts_with("gif/") {
            continue;
        }
        let input = testkit::read_fixture(key);
        match decode(&input) {
            Ok(_) => failures.push(format!(
                "{}: decode succeeded, expected {}",
                key, expected.expected_kind
            )),
            Err(e) => {
                if e.kind.as_str() != expected.expected_kind {
                    failures.push(format!(
                        "{}: kind {} != {}",
                        key, e.kind, expected.expected_kind
                    ));
                    continue;
                }
                if !expected.expected_detail_substring.is_empty()
                    && !e.detail.contains(&expected.expected_detail_substring)
                {
                    failures.push(format!(
                        "{}: detail '{}' does not contain '{}'",
                        key, e.detail, expected.expected_detail_substring
                    ));
                }
            }
        }
    }
    if !failures.is_empty() {
        panic!(
            "{} Group-4 GIF failures:\n  {}",
            failures.len(),
            failures.join("\n  ")
        );
    }
}

#[test]
fn invalid_jpeg_fixtures() {
    let errors = testkit::read_errors();
    let mut failures: Vec<String> = Vec::new();
    for (key, expected) in errors.iter() {
        if !key.starts_with("jpeg/") {
            continue;
        }
        let input = testkit::read_fixture(key);
        match decode(&input) {
            Ok(_) => failures.push(format!(
                "{}: decode succeeded, expected {}",
                key, expected.expected_kind
            )),
            Err(e) => {
                if e.kind.as_str() != expected.expected_kind {
                    failures.push(format!(
                        "{}: kind {} != {}",
                        key, e.kind, expected.expected_kind
                    ));
                    continue;
                }
                if !expected.expected_detail_substring.is_empty()
                    && !e.detail.contains(&expected.expected_detail_substring)
                {
                    failures.push(format!(
                        "{}: detail '{}' does not contain '{}'",
                        key, e.detail, expected.expected_detail_substring
                    ));
                }
            }
        }
    }
    if !failures.is_empty() {
        panic!(
            "{} Group-4 JPEG failures:\n  {}",
            failures.len(),
            failures.join("\n  ")
        );
    }
}
