mod testkit;

use rosetta_image_hash::internal::pil_gaussian_blur;
use serde::Deserialize;
use std::fs;
use testkit::spec_path::SPEC_DIR;

#[derive(Debug, Deserialize)]
struct GaussianBlurCase {
    name: String,
    input: Vec<Vec<u8>>,
    output: Vec<Vec<u8>>,
}

#[derive(Debug, Deserialize)]
struct GaussianBlurCases {
    cases: Vec<GaussianBlurCase>,
}

fn load_cases() -> GaussianBlurCases {
    let path = format!("{}/gaussian_blur_cases.json", SPEC_DIR);
    let data = fs::read_to_string(&path).unwrap_or_else(|e| panic!("read {path}: {e}"));
    serde_json::from_str(&data).expect("parse gaussian_blur_cases.json")
}

#[test]
fn gaussian_blur_group1() {
    let cases = load_cases();
    let mut failures: Vec<String> = Vec::new();

    for c in &cases.cases {
        let got = pil_gaussian_blur::apply(&c.input, 2.0);

        if got != c.output {
            // Find first mismatch
            let mut first_diff = String::new();
            'outer: for y in 0..c.output.len() {
                for x in 0..c.output[y].len() {
                    if got[y][x] != c.output[y][x] {
                        first_diff = format!(
                            "first diff at ({},{}) got={} want={}",
                            y, x, got[y][x], c.output[y][x]
                        );
                        break 'outer;
                    }
                }
            }
            failures.push(format!("case={} {}", c.name, first_diff));
        }
    }

    if !failures.is_empty() {
        panic!("{} gaussian_blur failures:\n  {}", failures.len(), failures.join("\n  "));
    }
}
