//go:build windows

package squint

import (
	"fmt"
	"os"
)

// openNoFollow approximates O_NOFOLLOW on Windows by Lstat-ing the path
// first and rejecting symlinks before opening. There is a narrow race
// between the lstat and the open here — narrower than the symlink-target
// race the check exists to defeat — but Windows lacks an O_NOFOLLOW flag
// and the alternative (CreateFileW with FILE_FLAG_OPEN_REPARSE_POINT)
// would require syscall-level wrapping.
func openNoFollow(path string) (*os.File, error) {
	info, err := os.Lstat(path)
	if err != nil {
		return nil, err
	}
	if info.Mode()&os.ModeSymlink != 0 {
		return nil, fmt.Errorf("%w: %s", ErrSymlinkNotAllowed, path)
	}
	return os.Open(path)
}
