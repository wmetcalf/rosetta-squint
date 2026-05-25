// Test-only suppressions: the for-loop indices mirror the reference
// Python loops; clippy's iterator suggestion would obscure the port
// equivalence and is not load-bearing for test code.
#![allow(clippy::needless_range_loop)]

mod testkit;

use rosetta_image_hash::internal::pil_median_filter;
use serde::Deserialize;
use std::fs;
use testkit::spec_path::SPEC_DIR;

#[derive(Debug, Deserialize)]
struct MedianFilterCase {
    name: String,
    input: Vec<Vec<u8>>,
    output: Vec<Vec<u8>>,
}

#[derive(Debug, Deserialize)]
struct MedianFilterCases {
    cases: Vec<MedianFilterCase>,
}

fn load_cases() -> MedianFilterCases {
    let path = format!("{}/median_filter_cases.json", SPEC_DIR);
    let data = fs::read_to_string(&path).unwrap_or_else(|e| panic!("read {path}: {e}"));
    serde_json::from_str(&data).expect("parse median_filter_cases.json")
}

#[test]
fn median_filter_group1() {
    let cases = load_cases();
    let mut failures: Vec<String> = Vec::new();

    for c in &cases.cases {
        let got = pil_median_filter::apply(&c.input);

        if got != c.output {
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
        panic!("{} median_filter failures:\n  {}", failures.len(), failures.join("\n  "));
    }
}
