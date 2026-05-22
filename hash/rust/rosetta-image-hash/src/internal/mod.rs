//! Internal kernel modules. Re-exported via `#[doc(hidden)] pub mod internal`
//! in lib.rs so the crate's own integration tests can reach them. Not part
//! of the public API contract.

pub mod pil_gray;
pub mod pil_hsv;
pub mod lanczos;
pub mod dct;
pub mod haar;
pub mod db4;
pub mod bitpack;
pub mod img_rgb;
