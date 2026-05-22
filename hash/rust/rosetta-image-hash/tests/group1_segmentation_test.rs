mod testkit;

use rosetta_image_hash::internal::find_segments;
use serde::Deserialize;
use std::fs;
use testkit::spec_path::SPEC_DIR;

#[derive(Debug, Deserialize)]
struct SegCase {
    name: String,
    input: Vec<Vec<f32>>,
    segment_threshold: f32,
    min_segment_size: usize,
    num_segments: usize,
    segments: Vec<Vec<[usize; 2]>>,
}

#[derive(Debug, Deserialize)]
struct SegCases {
    cases: Vec<SegCase>,
}

fn load_cases() -> SegCases {
    let path = format!("{}/segmentation_cases.json", SPEC_DIR);
    let data = fs::read_to_string(&path).unwrap_or_else(|e| panic!("read {path}: {e}"));
    serde_json::from_str(&data).expect("parse segmentation_cases.json")
}

#[test]
fn segmentation_group1() {
    let cases = load_cases();
    let mut failures: Vec<String> = Vec::new();

    for c in &cases.cases {
        let got = find_segments::find_all_segments(&c.input, c.segment_threshold, c.min_segment_size);

        // Check segment count
        if got.len() != c.num_segments {
            failures.push(format!(
                "case={} segment count: got={} want={}",
                c.name,
                got.len(),
                c.num_segments
            ));
            continue;
        }

        // Check each segment
        for (i, (got_seg, want_seg_raw)) in got.iter().zip(c.segments.iter()).enumerate() {
            let want_seg: Vec<(usize, usize)> =
                want_seg_raw.iter().map(|&[y, x]| (y, x)).collect();

            // Both should be sorted, but let's sort before comparing
            let mut got_sorted = got_seg.clone();
            let mut want_sorted = want_seg.clone();
            got_sorted.sort_unstable();
            want_sorted.sort_unstable();

            if got_sorted != want_sorted {
                failures.push(format!(
                    "case={} segment[{}]: len got={} want={}; first got={:?} want={:?}",
                    c.name,
                    i,
                    got_sorted.len(),
                    want_sorted.len(),
                    got_sorted.first(),
                    want_sorted.first()
                ));
            }
        }
    }

    if !failures.is_empty() {
        panic!("{} segmentation failures:\n  {}", failures.len(), failures.join("\n  "));
    }
}
