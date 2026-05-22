package imagehash

import (
	"fmt"

	"github.com/wmetcalf/rosetta-image-hash/go/imagehash/internal/bitpack"
)

func HexToHash(hex string) (Hash, error) {
	bits, err := bitpack.UnpackSquare(hex)
	if err != nil {
		return Hash{}, err
	}
	return newHashFromBits(bits), nil
}

func HexToFlathash(hex string, hashSize int) (Hash, error) {
	if hashSize < 1 {
		return Hash{}, fmt.Errorf("hashSize must be >= 1")
	}
	bits, err := bitpack.UnpackFlat(hex, hashSize)
	if err != nil {
		return Hash{}, err
	}
	return newHashFromBits(bits), nil
}
