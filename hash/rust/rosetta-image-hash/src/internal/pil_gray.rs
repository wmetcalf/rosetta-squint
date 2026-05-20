//! PIL 'L' (grayscale) conversion via the fixed-point ITU-R 601 luma formula.

/// Returns the grayscale value 0..=255 for uint8 RGB input.
/// Matches Pillow `Image.convert('L')` exactly.
pub fn to_gray(r: u8, g: u8, b: u8) -> u8 {
    ((u32::from(r) * 19595 + u32::from(g) * 38470 + u32::from(b) * 7471 + 32768) >> 16) as u8
}

#[cfg(test)]
mod tests {
    use super::*;
    use serde::Deserialize;
    use std::fs;

    #[derive(Deserialize)]
    struct Doc {
        cases: Vec<Case>,
    }
    #[derive(Deserialize)]
    struct Case {
        rgb: [u8; 3],
        #[serde(rename = "L")]
        l: u8,
    }

    #[test]
    fn all_cases_match_spec() {
        let path = "../../spec/grayscale_cases.json";
        let data = fs::read_to_string(path).expect("read grayscale_cases.json");
        let doc: Doc = serde_json::from_str(&data).expect("parse");
        assert_eq!(doc.cases.len(), 30, "expected 30 cases");
        for c in &doc.cases {
            assert_eq!(
                to_gray(c.rgb[0], c.rgb[1], c.rgb[2]),
                c.l,
                "RGB({},{},{})",
                c.rgb[0],
                c.rgb[1],
                c.rgb[2]
            );
        }
    }
}
