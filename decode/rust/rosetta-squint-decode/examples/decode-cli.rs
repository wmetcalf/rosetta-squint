//! decode-cli — decode an image and emit raw bytes (spec/SPEC.md §2 wire
//! format: 12-byte header + row-major pixels) to stdout.
//!
//! Used by tools/cross-port-diff/diff_all.py for live cross-port equivalence
//! checking.
//!
//! Usage: decode-cli <fixture.path>
//! Exit:  0 on success, 1 on decode error, 2 on harness error.

use std::env;
use std::fs;
use std::io::Write;
use std::process::ExitCode;

use rosetta_squint_decode::{decode, Channels};

fn main() -> ExitCode {
    let args: Vec<String> = env::args().collect();
    if args.len() != 2 {
        eprintln!("usage: decode-cli <fixture>");
        return ExitCode::from(2);
    }
    let bytes = match fs::read(&args[1]) {
        Ok(b) => b,
        Err(e) => {
            eprintln!("read {}: {}", args[1], e);
            return ExitCode::from(2);
        }
    };
    match decode(&bytes) {
        Ok(img) => {
            let channels: u8 = match img.channels {
                Channels::Rgb => 3,
                Channels::Rgba => 4,
            };
            let mut stdout = std::io::stdout().lock();
            let w = (img.width as u32).to_le_bytes();
            let h = (img.height as u32).to_le_bytes();
            if stdout.write_all(&w).is_err()
                || stdout.write_all(&h).is_err()
                || stdout.write_all(&[channels, 0, 0, 0]).is_err()
                || stdout.write_all(&img.data).is_err()
            {
                eprintln!("write error");
                return ExitCode::from(2);
            }
            ExitCode::from(0)
        }
        Err(e) => {
            eprintln!("decode error: {:?}: {}", e.kind, e.detail);
            ExitCode::from(1)
        }
    }
}
