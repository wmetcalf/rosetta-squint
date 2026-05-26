//! rosetta-squint-decode — byte-exact PIL-compatible image decoders.
//!
//! v0.1.0 supports BMP + PNG. See SPEC.md in the shared /spec/ directory.

// Newer clippy versions (Rust 1.95+) tighten some style lints in ways
// that conflict with the FFI/decoder-style code in this crate. Suppress
// the noisy ones at the crate level; the substantive lints (correctness,
// suspicious, perf) remain enabled.
#![allow(clippy::collapsible_if)]
#![allow(clippy::implicit_saturating_sub)]
#![allow(clippy::same_item_push)]
#![allow(clippy::unnecessary_cast)]

mod bmp;
mod gif;
mod heic;
mod jpeg;
mod png;
mod tiff;
mod webp;
mod decode;
pub(crate) mod dimension_sniff;
mod error;
pub(crate) mod limits;
mod types;

pub use decode::{decode, detect_format, supported_formats};
pub use error::{DecodeError, DecodeErrorKind};
pub use limits::MAX_PIXELS;
pub use types::{Channels, DecodedImage, Format};
