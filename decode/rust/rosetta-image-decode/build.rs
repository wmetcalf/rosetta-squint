// Build script for the in-tree JPEG decode shim (C, uses setjmp/longjmp
// for libjpeg error recovery — Rust panic/catch_unwind isn't safe under
// panic=abort builds like cargo-fuzz).
//
// Compiles c-src/jpeg_decode_shim.c against mozjpeg-sys's headers.

fn main() {
    println!("cargo:rerun-if-changed=c-src/jpeg_decode_shim.c");

    // mozjpeg-sys's build.rs publishes its include directory via the
    // "cargo:include=..." metadata line, which downstream crates pick up
    // as DEP_<links-key>_INCLUDE. Its links key is `jpeg`.
    let mozjpeg_include = std::env::var("DEP_JPEG_INCLUDE")
        .expect("DEP_JPEG_INCLUDE not set — mozjpeg-sys must be a direct dep");

    cc::Build::new()
        .file("c-src/jpeg_decode_shim.c")
        // mozjpeg-sys can export multiple include paths separated by the
        // platform separator (colon on Linux). Split and add each.
        .includes(mozjpeg_include.split(':'))
        .opt_level(2)
        .compile("rid_jpeg_shim");
}
