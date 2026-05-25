//! squint-cli — compute a perceptual image hash and print the hex string to stdout.
//!
//! Used by tools/cross-squint-diff for live cross-port equivalence checking.
//!
//! Usage: squint-cli <algo> <size> <path>
//!   algo  — one of: phash, phash_simple, dhash, dhash_vertical, average_hash,
//!           whash_haar, whash_db4, whash_db4_robust, colorhash, crop_resistant_hash
//!   size  — hash_size (or binbits for colorhash); pass "-" for crop_resistant_hash
//!   path  — path to the image file
//!
//! Exit:  0 on success, 1 on hash/decode error, 2 on usage error.

use std::env;
use std::process::ExitCode;

use rosetta_squint::{
    average_hash, colorhash, crop_resistant_hash, dhash, dhash_vertical, phash, phash_simple,
    whash_db4, whash_db4_robust, whash_haar,
};

fn main() -> ExitCode {
    let args: Vec<String> = env::args().collect();
    if args.len() != 4 {
        eprintln!("usage: squint-cli <algo> <size> <path>");
        return ExitCode::from(2);
    }
    let algo = args[1].as_str();
    let size: usize = args[2].parse().unwrap_or(8);
    let path = &args[3];

    let result = match algo {
        "phash" => phash(path, size).map(|h| h.to_hex()),
        "phash_simple" => phash_simple(path, size).map(|h| h.to_hex()),
        "dhash" => dhash(path, size).map(|h| h.to_hex()),
        "dhash_vertical" => dhash_vertical(path, size).map(|h| h.to_hex()),
        "average_hash" => average_hash(path, size).map(|h| h.to_hex()),
        "whash_haar" => whash_haar(path, size).map(|h| h.to_hex()),
        "whash_db4" => whash_db4(path, size).map(|h| h.to_hex()),
        "whash_db4_robust" => whash_db4_robust(path, size).map(|h| h.to_hex()),
        "colorhash" => colorhash(path, size).map(|h| h.to_hex()),
        "crop_resistant_hash" => crop_resistant_hash(path, None).map(|mh| mh.to_hex()),
        _ => {
            eprintln!("unknown algo: {}", algo);
            return ExitCode::from(2);
        }
    };

    match result {
        Ok(hex) => {
            println!("{}", hex);
            ExitCode::from(0)
        }
        Err(e) => {
            eprintln!("error: {}", e);
            ExitCode::from(1)
        }
    }
}
