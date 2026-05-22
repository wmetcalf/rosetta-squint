/// Number of color channels in a DecodedImage's pixel buffer.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
pub enum Channels {
    Rgb,
    Rgba,
}

impl Channels {
    /// Returns 3 for Rgb, 4 for Rgba.
    pub fn bytes_per_pixel(&self) -> usize {
        match self {
            Channels::Rgb => 3,
            Channels::Rgba => 4,
        }
    }
}

/// Image format tag.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
pub enum Format {
    Bmp,
    Png,
    Gif,
    Jpeg,
    Webp,
    Tiff,
    Heic,
    Emf,
    Wmf,
}

impl std::fmt::Display for Format {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        let s = match self {
            Format::Bmp => "bmp",
            Format::Png => "png",
            Format::Gif => "gif",
            Format::Jpeg => "jpeg",
            Format::Webp => "webp",
            Format::Tiff => "tiff",
            Format::Heic => "heic",
            Format::Emf => "emf",
            Format::Wmf => "wmf",
        };
        f.write_str(s)
    }
}

/// Result of a successful Decode call.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct DecodedImage {
    pub width: usize,
    pub height: usize,
    pub data: Vec<u8>,
    pub channels: Channels,
    pub format: Format,
}
