package testkit

import (
	"path/filepath"
)

// SpecDir resolves /spec/ relative to the calling test's working directory.
// Go tests run with CWD set to the package directory containing the test file,
// so internal/* tests need "../../../spec" and root-package tests need "../../spec".

// DirRoot returns the spec dir as resolved from the root-package CWD (go/imagehash/).
func DirRoot() string { return filepath.Join("..", "..", "spec") }

// DirInternal returns the spec dir as resolved from an internal/* package CWD.
func DirInternal() string { return filepath.Join("..", "..", "..", "..", "spec") }

// PathFromRoot joins SpecDir + relative.
func PathFromRoot(rel string) string { return filepath.Join(DirRoot(), rel) }

// PathFromInternal joins SpecDir + relative for internal/* tests.
func PathFromInternal(rel string) string { return filepath.Join(DirInternal(), rel) }
