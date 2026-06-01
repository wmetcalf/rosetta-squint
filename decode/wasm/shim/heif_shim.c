/* Stubs for wasi-libc / no-EH gaps. mkstemp is on an encode/temp-file path
   the decode-only build never runs. The __cxa_* stubs replicate emscripten's
   no-exception model: a C++ throw becomes a wasm trap, which the host runtime
   catches and reports as a decode error. Valid HEIC never throws. */
#include <stddef.h>
int mkstemp(char *tmpl) { (void)tmpl; return -1; }
void *__cxa_allocate_exception(size_t s) { (void)s; __builtin_trap(); }
void __cxa_throw(void *t, void *ti, void (*d)(void *)) { (void)t; (void)ti; (void)d; __builtin_trap(); }
