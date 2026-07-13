module github.com/wmetcalf/rosetta-squint/squint/go/squint

go 1.25.0

require (
	github.com/wmetcalf/rosetta-squint/decode/go/imagedecode v0.0.0
	github.com/wmetcalf/rosetta-squint/hash/go/imagehash v0.0.0
)

require (
	github.com/chai2010/webp v1.4.0 // indirect
	github.com/tetratelabs/wazero v1.12.0 // indirect
	golang.org/x/image v0.43.0 // indirect
	golang.org/x/sys v0.44.0 // indirect
)

replace (
	github.com/wmetcalf/rosetta-squint/decode/go/imagedecode => ../../../decode/go/imagedecode
	github.com/wmetcalf/rosetta-squint/hash/go/imagehash => ../../../hash/go/imagehash
)
