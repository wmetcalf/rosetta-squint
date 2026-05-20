//! PIL 'HSV' conversion using the integer formula from libImaging/Convert.c rgb2hsv_row.
//!
//! IMPORTANT: PIL uses INTEGER arithmetic on uint8 RGB directly, not a float HSV
//! scaled by 255. Naive Math.round(float_hsv * 255) diverges on .5 boundaries.
//! Example: RGB(100,150,200) → PIL (148,127,200); round (149,128,200).
//!
//! The negative-h_pre case (r == maxc and bc < gc) is wrapped via `+= 6 * 255`
//! so the final division is on a non-negative integer.

/// Returns (h, s, v) each in 0..=255 for uint8 RGB input.
/// Matches Pillow `Image.convert('HSV')` byte-exact.
pub fn to_hsv(r: u8, g: u8, b: u8) -> (u8, u8, u8) {
    let ri = i32::from(r);
    let gi = i32::from(g);
    let bi = i32::from(b);
    let maxc = ri.max(gi).max(bi);
    let minc = ri.min(gi).min(bi);
    let v = maxc as u8;
    if maxc == 0 {
        return (0, 0, v);
    }
    let s = ((255 * (maxc - minc)) / maxc) as u8;
    if minc == maxc {
        return (0, s, v);
    }
    let delta = maxc - minc;
    let rc = ((maxc - ri) * 255) / delta;
    let gc = ((maxc - gi) * 255) / delta;
    let bc = ((maxc - bi) * 255) / delta;
    let mut h_pre = if ri == maxc {
        bc - gc
    } else if gi == maxc {
        2 * 255 + rc - bc
    } else {
        4 * 255 + gc - rc
    };
    if h_pre < 0 {
        h_pre += 6 * 255;
    }
    let h = (h_pre / 6) as u8;
    (h, s, v)
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
        hsv: [u8; 3],
    }

    #[test]
    fn all_cases_match_spec() {
        let path = "../../spec/hsv_cases.json";
        let data = fs::read_to_string(path).expect("read hsv_cases.json");
        let doc: Doc = serde_json::from_str(&data).expect("parse");
        assert_eq!(doc.cases.len(), 31, "expected 31 cases");
        for c in &doc.cases {
            let (h, s, v) = to_hsv(c.rgb[0], c.rgb[1], c.rgb[2]);
            assert_eq!(
                [h, s, v],
                c.hsv,
                "RGB({},{},{})",
                c.rgb[0],
                c.rgb[1],
                c.rgb[2]
            );
        }
    }

    #[test]
    fn negative_h_pre_wrap() {
        assert_eq!(to_hsv(200, 100, 150), (233, 127, 200));
    }

    #[test]
    fn half_boundary_floor_not_round() {
        assert_eq!(to_hsv(100, 150, 200), (148, 127, 200));
    }

    #[test]
    fn saturation_170_boundary() {
        assert_eq!(to_hsv(255, 85, 85).1, 170);
    }
}
