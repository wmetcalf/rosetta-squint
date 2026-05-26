//! Test helpers shared across the integration test files in tests/.
//! Each `tests/*.rs` file declares `mod testkit;` to bring these into scope.
//!
//! Each test file uses a SUBSET of the helpers, so any helper a given file
//! doesn't reference shows up as `dead_code` / `unused_imports`. Suppress
//! at the testkit module level — the helpers ARE used as a whole.
#![allow(dead_code)]
#![allow(unused_imports)]

pub mod goldens;
pub mod lanczos_case;
pub mod predecoded;
pub mod spec_path;

pub use goldens::{algorithm_cases, fixture_cases, AlgorithmCase, FixtureCase};
pub use lanczos_case::{load_lanczos_case, LanczosCase};
pub use predecoded::load_predecoded;
pub use spec_path::SPEC_DIR;
