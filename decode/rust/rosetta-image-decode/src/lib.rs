//! rosetta-image-decode — byte-exact PIL-compatible image decoders.
//!
//! v0.1.0 supports BMP + PNG. See SPEC.md in the shared /spec/ directory.

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
