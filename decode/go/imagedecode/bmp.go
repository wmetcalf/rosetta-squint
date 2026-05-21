package imagedecode

// decodeBmp is the BMP-specific decoder. Stub for now; Tasks 14-16 implement it.
func decodeBmp(b []byte) (DecodedImage, error) {
	return DecodedImage{}, newError(UnsupportedFeature, Bmp, true, "BMP decoder not yet implemented")
}
