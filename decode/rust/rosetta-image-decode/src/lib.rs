//! rosetta-image-decode — byte-exact PIL-compatible image decoders.
//!
//! v0.1.0 supports BMP + PNG. See SPEC.md in the shared /spec/ directory.

mod bmp;
mod gif;
mod png;
mod decode;
mod error;
mod types;

pub use decode::{decode, detect_format, supported_formats};
pub use error::{DecodeError, DecodeErrorKind};
pub use types::{Channels, DecodedImage, Format};
