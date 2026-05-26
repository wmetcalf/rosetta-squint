//! Loads `goldens.json` once and provides typed iteration over algorithm cases.

// `if let` nesting here mirrors the goldens.json shape closely and refactoring
// would obscure the structure; suppress the collapsible-match suggestion.
#![allow(clippy::collapsible_match)]

use std::collections::BTreeMap;
use std::fs;
use std::sync::OnceLock;

use serde::Deserialize;

use super::spec_path;

#[derive(Debug, Deserialize)]
struct Goldens {
    algorithms: BTreeMap<String, AlgorithmEntry>,
}

#[derive(Debug, Deserialize)]
struct AlgorithmEntry {
    fixtures: BTreeMap<String, BTreeMap<String, Option<String>>>,
}

#[derive(Debug, Clone)]
pub struct AlgorithmCase {
    pub fixture: String,
    pub size: usize,
    pub hex: String,
}

static GOLDENS: OnceLock<Goldens> = OnceLock::new();

fn load() -> &'static Goldens {
    GOLDENS.get_or_init(|| {
        let path = spec_path::spec_path("goldens.json");
        let data = fs::read_to_string(&path).expect("read goldens.json");
        serde_json::from_str(&data).expect("parse goldens.json")
    })
}

pub fn algorithm_cases(algorithm: &str) -> Vec<AlgorithmCase> {
    let g = load();
    let entry = g
        .algorithms
        .get(algorithm)
        .unwrap_or_else(|| panic!("algorithm {algorithm:?} not in goldens.json"));
    let mut out = Vec::new();
    for (fixture, sizes) in &entry.fixtures {
        for (size_str, hex_opt) in sizes {
            let Some(hex) = hex_opt else { continue };
            let size: usize = size_str.parse().expect("size parse");
            out.push(AlgorithmCase {
                fixture: fixture.clone(),
                size,
                hex: hex.clone(),
            });
        }
    }
    out
}

/// A golden case for algorithms that don't use a numeric size (e.g. crop_resistant_hash).
#[derive(Debug, Clone)]
pub struct FixtureCase {
    pub fixture: String,
    pub hex: String,
}

/// Returns cases for an algorithm whose fixtures have a single `"default"` key.
pub fn fixture_cases(algorithm: &str) -> Vec<FixtureCase> {
    let g = load();
    let entry = g
        .algorithms
        .get(algorithm)
        .unwrap_or_else(|| panic!("algorithm {algorithm:?} not in goldens.json"));
    let mut out = Vec::new();
    for (fixture, sizes) in &entry.fixtures {
        if let Some(hex_opt) = sizes.get("default") {
            if let Some(hex) = hex_opt {
                out.push(FixtureCase {
                    fixture: fixture.clone(),
                    hex: hex.clone(),
                });
            }
        }
    }
    out
}
