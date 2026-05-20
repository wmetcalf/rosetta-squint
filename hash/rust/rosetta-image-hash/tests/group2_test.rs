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
