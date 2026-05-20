mod testkit;

use std::collections::HashSet;
use std::fs;
use std::io::{BufRead, BufReader};

use image::DynamicImage;
use rosetta_image_hash::{average_hash, colorhash, dhash, phash, whash_haar, Hash, ImageHashError};

fn load_exemptions() -> HashSet<String> {
	let mut exempt = HashSet::new();
	let Ok(f) = fs::File::open("DECODER_NOTES.md") else { return exempt };
	let reader = BufReader::new(f);
	for line in reader.lines().map_while(|r| r.ok()) {
		if let Some(idx) = line.find('—') {
			let name = line[..idx].trim();
			if name.ends_with(".png") {
				exempt.insert(name.to_string());
			}
		}
	}
	exempt
}

fn decode_png(fixture: &str) -> DynamicImage {
	let path = format!("{}/fixtures/{}", testkit::SPEC_DIR, fixture);
	image::open(&path).unwrap_or_else(|e| panic!("open {path}: {e}"))
}

fn run(
	name: &str,
	exempt: &HashSet<String>,
	compute: impl Fn(&DynamicImage, usize) -> Result<Hash, ImageHashError>,
	label: &str,
	failures: &mut Vec<String>,
) {
	for c in testkit::algorithm_cases(name) {
		if exempt.contains(&c.fixture) {
			continue;
		}
		let img = decode_png(&c.fixture);
		match compute(&img, c.size) {
			Err(e) => failures.push(format!("{label} fixture={} size={}: {e}", c.fixture, c.size)),
			Ok(h) => {
				if h.to_hex() != c.hex {
					failures.push(format!(
						"{label} fixture={} size={}: got {} want {}",
						c.fixture, c.size, h.to_hex(), c.hex
					));
				}
			}
		}
	}
}

#[test]
fn png_end_to_end() {
	let exempt = load_exemptions();
	let mut failures: Vec<String> = Vec::new();
	run("average_hash", &exempt, average_hash, "average_hash", &mut failures);
	run("dhash",        &exempt, dhash,        "dhash",        &mut failures);
	run("phash",        &exempt, phash,        "phash",        &mut failures);
	run("whash_haar",   &exempt, whash_haar,   "whash_haar",   &mut failures);
	run("colorhash",    &exempt, colorhash,    "colorhash",    &mut failures);
	if !failures.is_empty() {
		panic!("{} Group-3 failures:\n  {}", failures.len(), failures.join("\n  "));
	}
}
