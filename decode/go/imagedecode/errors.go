package imagedecode

import "fmt"

// DecodeErrorKind categorizes decode failures.
type DecodeErrorKind int

const (
	UnsupportedFormat DecodeErrorKind = iota
	CorruptInput
	Truncated
	UnsupportedFeature
	ImageTooLarge
)

func (k DecodeErrorKind) String() string {
	switch k {
	case UnsupportedFormat:
		return "unsupportedFormat"
	case CorruptInput:
		return "corruptInput"
	case Truncated:
		return "truncated"
	case UnsupportedFeature:
		return "unsupportedFeature"
	case ImageTooLarge:
		return "imageTooLarge"
	}
	return "unknown"
}

// DecodeError is returned by Decode on failure.
// HasFormat is false when Kind == UnsupportedFormat (magic didn't match any format).
type DecodeError struct {
	Kind      DecodeErrorKind
	HasFormat bool
	Format    Format
	Detail    string
}

func (e *DecodeError) Error() string {
	if e.HasFormat {
		if e.Detail != "" {
			return fmt.Sprintf("%s[%s]: %s", e.Kind, e.Format, e.Detail)
		}
		return fmt.Sprintf("%s[%s]", e.Kind, e.Format)
	}
	if e.Detail != "" {
		return fmt.Sprintf("%s: %s", e.Kind, e.Detail)
	}
	return e.Kind.String()
}

func newError(kind DecodeErrorKind, format Format, hasFormat bool, detail string) *DecodeError {
	return &DecodeError{Kind: kind, HasFormat: hasFormat, Format: format, Detail: detail}
}
