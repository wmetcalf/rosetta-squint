//! Test helpers shared across the integration test files in tests/.
//! Each `tests/*.rs` file declares `mod testkit;` to bring these into scope.

pub mod goldens;
pub mod lanczos_case;
pub mod predecoded;
pub mod spec_path;

pub use goldens::{algorithm_cases, AlgorithmCase};
pub use lanczos_case::{load_lanczos_case, LanczosCase};
pub use predecoded::load_predecoded;
pub use spec_path::SPEC_DIR;
