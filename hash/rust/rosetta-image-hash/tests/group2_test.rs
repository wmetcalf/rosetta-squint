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

#[test]
fn colorhash_goldens() {
    let cases = algorithm_cases("colorhash");
    let mut failures: Vec<String> = Vec::new();
    for c in &cases {
        let img = load_predecoded(&c.fixture);
        let h = rosetta_image_hash::colorhash(&img, c.size).expect("compute");
        if h.to_hex() != c.hex {
            failures.push(format!("fixture={} binbits={} got={} want={}", c.fixture, c.size, h.to_hex(), c.hex));
        }
    }
    if !failures.is_empty() {
        panic!("{} failures:\n  {}", failures.len(), failures.join("\n  "));
    }
}

#[test]
fn bin_encoding_b4() {
    use rosetta_image_hash::colorhash_bin_encode;
    let cases: [(usize, [bool; 4]); 7] = [
        (0,  [false, false, false, false]),
        (1,  [false, false, false, true]),
        (2,  [false, false, true,  false]),
        (4,  [false, true,  true,  false]),  // NOT 0100
        (7,  [false, true,  true,  true]),
        (8,  [true,  true,  false, false]),  // NOT 1000
        (15, [true,  true,  true,  true]),
    ];
    for (v, expected) in cases {
        let got = colorhash_bin_encode(v, 4);
        assert_eq!(got.len(), 4);
        for i in 0..4 {
            assert_eq!(got[i], expected[i], "v={v} bit {i}");
        }
    }
}

#[test]
fn bin_encoding_b3() {
    use rosetta_image_hash::colorhash_bin_encode;
    assert_eq!(colorhash_bin_encode(0, 3), vec![false, false, false]);
    assert_eq!(colorhash_bin_encode(7, 3), vec![true, true, true]);
}
