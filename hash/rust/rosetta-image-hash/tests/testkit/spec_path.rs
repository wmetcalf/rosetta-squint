//! Relative path constants. Cargo runs integration tests with CWD = crate root,
//! so /spec/ is resolved via "../../spec".

pub const SPEC_DIR: &str = "../../spec";

pub fn spec_path(rel: &str) -> String {
    format!("{}/{}", SPEC_DIR, rel)
}
