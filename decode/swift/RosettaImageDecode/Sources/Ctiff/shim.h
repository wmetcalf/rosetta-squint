#ifndef CTIFF_SHIM_H
#define CTIFF_SHIM_H
#include <tiffio.h>

// TIFFGetField is variadic in C and cannot be called from Swift directly.
// These inline shims wrap the common cases.
static inline int TIFFGetField_UInt32(TIFF* tif, uint32_t tag, uint32_t* val) {
    return TIFFGetField(tif, tag, val);
}
#endif
