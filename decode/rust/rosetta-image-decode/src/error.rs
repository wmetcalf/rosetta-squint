use thiserror::Error;
use crate::types::Format;

/// Categorizes decode failures.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum DecodeErrorKind {
    UnsupportedFormat,
    CorruptInput,
    Truncated,
    UnsupportedFeature,
}

impl DecodeErrorKind {
    pub fn as_str(&self) -> &'static str {
        match self {
            DecodeErrorKind::UnsupportedFormat => "unsupportedFormat",
            DecodeErrorKind::CorruptInput => "corruptInput",
            DecodeErrorKind::Truncated => "truncated",
            DecodeErrorKind::UnsupportedFeature => "unsupportedFeature",
        }
    }
}

impl std::fmt::Display for DecodeErrorKind {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.write_str(self.as_str())
    }
}

/// Error type returned by `decode`.
#[derive(Debug, Clone, Error, PartialEq, Eq)]
pub struct DecodeError {
    pub kind: DecodeErrorKind,
    pub format: Option<Format>,
    pub detail: String,
}

impl std::fmt::Display for DecodeError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match (&self.format, self.detail.is_empty()) {
            (Some(fmt), false) => write!(f, "{}[{}]: {}", self.kind, fmt, self.detail),
            (Some(fmt), true) => write!(f, "{}[{}]", self.kind, fmt),
            (None, false) => write!(f, "{}: {}", self.kind, self.detail),
            (None, true) => write!(f, "{}", self.kind),
        }
    }
}

impl DecodeError {
    pub(crate) fn new(kind: DecodeErrorKind, format: Option<Format>, detail: impl Into<String>) -> Self {
        DecodeError { kind, format, detail: detail.into() }
    }
}
