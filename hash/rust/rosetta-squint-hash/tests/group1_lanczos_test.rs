// Test-only suppressions: the for-loop indices mirror the reference
// Python loops; clippy's iterator suggestion would obscure the port
// equivalence and is not load-bearing for test code.
#![allow(clippy::needless_range_loop)]

mod testkit;

use rosetta_squint_hash::internal::lanczos;

#[test]
fn downsample_64_to_32_gradient() {
    run_case("downsample_64_to_32_gradient");
}

#[test]
fn upsample_16_to_32_gradient() {
    run_case("upsample_16_to_32_gradient");
}

#[test]
fn identity_32_to_32_random() {
    run_case("identity_32_to_32_random");
}

#[test]
fn asymmetric_64x48_to_32x24() {
    run_case("asymmetric_64x48_to_32x24");
}

fn run_case(name: &str) {
    let c = testkit::load_lanczos_case(name);
    let got = lanczos::resize(&c.src, c.dst_w, c.dst_h);
    assert_eq!(got.len(), c.dst_h, "row count");
    assert_eq!(got[0].len(), c.dst_w, "col count");
    for y in 0..c.dst_h {
        for x in 0..c.dst_w {
            assert_eq!(
                got[y][x], c.dst[y][x],
                "{name} pixel ({y},{x}): got {} want {}", got[y][x], c.dst[y][x]
            );
        }
    }
}
