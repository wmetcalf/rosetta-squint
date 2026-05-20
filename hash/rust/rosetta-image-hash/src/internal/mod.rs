//! Internal kernel modules. Re-exported via `#[doc(hidden)] pub mod internal`
//! in lib.rs so the crate's own integration tests can reach them. Not part
//! of the public API contract.

pub mod pil_gray;
