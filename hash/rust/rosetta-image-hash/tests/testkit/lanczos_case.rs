//! Parses `spec/lanczos_cases/*.bin` reference vectors.

use std::fs;

use super::spec_path;

pub struct LanczosCase {
    pub src_w: usize,
    pub src_h: usize,
    pub dst_w: usize,
    pub dst_h: usize,
    pub src: Vec<Vec<u8>>,
    pub dst: Vec<Vec<u8>>,
}

pub fn load_lanczos_case(name: &str) -> LanczosCase {
    let path = format!("{}/lanczos_cases/{}.bin", spec_path::SPEC_DIR, name);
    let data = fs::read(&path).unwrap_or_else(|e| panic!("read {path}: {e}"));
    assert!(data.len() >= 16, "lanczos case {name} too short");
    let sw = u32::from_le_bytes(data[0..4].try_into().unwrap()) as usize;
    let sh = u32::from_le_bytes(data[4..8].try_into().unwrap()) as usize;
    let dw = u32::from_le_bytes(data[8..12].try_into().unwrap()) as usize;
    let dh = u32::from_le_bytes(data[12..16].try_into().unwrap()) as usize;
    assert_eq!(data.len(), 16 + sw * sh + dw * dh, "lanczos {name} length mismatch");

    let mut off = 16;
    let mut src = Vec::with_capacity(sh);
    for _ in 0..sh {
        let mut row = Vec::with_capacity(sw);
        for _ in 0..sw {
            row.push(data[off]);
            off += 1;
        }
        src.push(row);
    }
    let mut dst = Vec::with_capacity(dh);
    for _ in 0..dh {
        let mut row = Vec::with_capacity(dw);
        for _ in 0..dw {
            row.push(data[off]);
            off += 1;
        }
        dst.push(row);
    }
    LanczosCase {
        src_w: sw,
        src_h: sh,
        dst_w: dw,
        dst_h: dh,
        src,
        dst,
    }
}
