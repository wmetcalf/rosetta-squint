// tests/testkit.rs - shared test helpers
// (this file is NOT a test itself; it's a module included by other test files)

use serde::Deserialize;
use std::fs;
use std::path::{Path, PathBuf};

pub const SPEC_DIR: &str = "../../spec";

pub fn spec_path(rel: &str) -> PathBuf {
    Path::new(SPEC_DIR).join(rel)
}

pub fn read_fixture(rel: &str) -> Vec<u8> {
    let path = spec_path("fixtures").join(rel);
    fs::read(&path).unwrap_or_else(|e| panic!("read_fixture {}: {}", rel, e))
}

pub struct DecodedGolden {
    pub width: usize,
    pub height: usize,
    pub channels: usize,
    pub pixels: Vec<u8>,
}

pub fn read_golden(fixture_rel: &str) -> DecodedGolden {
    let path = spec_path("decoded").join(format!("{}.bin", fixture_rel));
    let blob = fs::read(&path).unwrap_or_else(|e| panic!("read_golden {}: {}", fixture_rel, e));
    assert!(blob.len() >= 12, "golden {} too short", fixture_rel);
    let width = u32::from_le_bytes(blob[0..4].try_into().unwrap()) as usize;
    let height = u32::from_le_bytes(blob[4..8].try_into().unwrap()) as usize;
    let channels = blob[8] as usize;
    let pixels = blob[12..].to_vec();
    DecodedGolden {
        width,
        height,
        channels,
        pixels,
    }
}

pub fn list_valid_fixtures(format: &str) -> Vec<String> {
    let dir = spec_path("fixtures").join(format).join("valid");
    let mut out: Vec<String> = fs::read_dir(&dir)
        .unwrap_or_else(|e| panic!("list_valid_fixtures {}: {}", format, e))
        .filter_map(|e| e.ok())
        .filter(|e| e.path().is_file())
        .filter_map(|e| {
            let name = e.file_name().into_string().ok()?;
            if name.ends_with(&format!(".{}", format)) {
                Some(format!("{}/valid/{}", format, name))
            } else {
                None
            }
        })
        .collect();
    out.sort();
    out
}

#[derive(Debug, Deserialize, Clone)]
pub struct ExpectedError {
    pub format: Option<String>,
    pub expected_kind: String,
    pub expected_detail_substring: String,
}

#[derive(Debug, Deserialize)]
struct ErrorsJson {
    fixtures: std::collections::BTreeMap<String, ExpectedError>,
}

pub fn read_errors() -> std::collections::BTreeMap<String, ExpectedError> {
    let path = spec_path("errors.json");
    let data = fs::read_to_string(&path).expect("read errors.json");
    let doc: ErrorsJson = serde_json::from_str(&data).expect("parse errors.json");
    doc.fixtures
}
