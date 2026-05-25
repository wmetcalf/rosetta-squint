//go:build !windows

package squint

import (
	"errors"
	"fmt"
	"os"
	"syscall"
)

// openNoFollow opens path with O_NOFOLLOW so that the open fails (ELOOP)
// if the final path component is a symlink. Translates ELOOP into
// ErrSymlinkNotAllowed so callers can distinguish symlink rejection from
// other I/O errors.
func openNoFollow(path string) (*os.File, error) {
	f, err := os.OpenFile(path, os.O_RDONLY|syscall.O_NOFOLLOW, 0)
	if err != nil {
		if errors.Is(err, syscall.ELOOP) {
			return nil, fmt.Errorf("%w: %s", ErrSymlinkNotAllowed, path)
		}
		return nil, err
	}
	return f, nil
}
