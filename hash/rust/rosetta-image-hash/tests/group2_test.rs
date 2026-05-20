mod testkit;

use rosetta_image_hash::average_hash;
use testkit::{algorithm_cases, load_predecoded};

#[test]
fn average_hash_goldens() {
	let cases = algorithm_cases("average_hash");
	let mut failures: Vec<String> = Vec::new();
	for c in &cases {
		let img = load_predecoded(&c.fixture);
		let h = average_hash(&img, c.size).expect("compute");
		let got = h.to_hex();
		if got != c.hex {
			failures.push(format!(
				"fixture={} size={} got={} want={}",
				c.fixture, c.size, got, c.hex
			));
		}
	}
	if !failures.is_empty() {
		panic!("{} failures:\n  {}", failures.len(), failures.join("\n  "));
	}
}

#[test]
fn dhash_goldens() {
	let cases = algorithm_cases("dhash");
	let mut failures: Vec<String> = Vec::new();
	for c in &cases {
		let img = load_predecoded(&c.fixture);
		let h = rosetta_image_hash::dhash(&img, c.size).expect("compute");
		if h.to_hex() != c.hex {
			failures.push(format!("fixture={} size={} got={} want={}", c.fixture, c.size, h.to_hex(), c.hex));
		}
	}
	if !failures.is_empty() {
		panic!("{} failures:\n  {}", failures.len(), failures.join("\n  "));
	}
}

#[test]
fn phash_goldens() {
    let cases = algorithm_cases("phash");
    let mut failures: Vec<String> = Vec::new();
    for c in &cases {
        let img = load_predecoded(&c.fixture);
        let h = rosetta_image_hash::phash(&img, c.size).expect("compute");
        if h.to_hex() != c.hex {
            failures.push(format!("fixture={} size={} got={} want={}", c.fixture, c.size, h.to_hex(), c.hex));
        }
    }
    if !failures.is_empty() {
        panic!("{} failures:\n  {}", failures.len(), failures.join("\n  "));
    }
}

#[test]
fn whash_haar_goldens() {
    let cases = algorithm_cases("whash_haar");
    let mut failures: Vec<String> = Vec::new();
    for c in &cases {
        let img = load_predecoded(&c.fixture);
        let h = rosetta_image_hash::whash_haar(&img, c.size).expect("compute");
        if h.to_hex() != c.hex {
            failures.push(format!("fixture={} size={} got={} want={}", c.fixture, c.size, h.to_hex(), c.hex));
        }
    }
    if !failures.is_empty() {
        panic!("{} failures:\n  {}", failures.len(), failures.join("\n  "));
    }
}
