# Fuzz targets for rosetta-image-decode

This directory contains [cargo-fuzz](https://rust-fuzz.github.io/book/cargo-fuzz.html)
harnesses for the `rosetta-image-decode` crate.

All targets use libFuzzer and require the **nightly** toolchain:

```bash
cargo +nightly fuzz <subcommand> ...
```

---

## Targets

### `decode_any`

Feeds arbitrary bytes through the top-level `decode()` dispatcher.

**What it tests:**
- Every format branch (BMP, PNG, GIF, JPEG, WebP, TIFF, HEIC) is reachable via magic-byte matching.
- The contract that `decode()` never panics — only returns `Ok(DecodedImage)` or `Err(DecodeError)`.
- The `MAX_PIXELS` allocation cap is honoured.

**File:** `fuzz_targets/decode_any.rs`

---

### `decode_with_prefix`

Picks one of seven format magic-byte prefixes deterministically from `data[0]`, prepends it to the remainder of the fuzzer-supplied bytes, then calls `decode()`.

**What it tests:**
- Higher signal-per-cycle than pure random bytes: forces the fuzzer to exercise each decoder's internal parsing logic instead of bouncing off `detect_format`.
- Same no-panic contract as `decode_any`, but with structured bias toward valid headers.

**File:** `fuzz_targets/decode_with_prefix.rs`

---

### `detect_format`

Feeds arbitrary bytes to `detect_format()` alone.

**What it tests:**
- Magic-byte matching is exhaustive and panic-free on any input length including empty.
- No out-of-bounds reads in the byte-pattern comparisons.
- Pure function with no I/O, so throughput is very high (~1M exec/s on typical hardware).

**File:** `fuzz_targets/detect_format.rs`

---

## Seed corpus

Seed files are committed under `corpus/<target>/` (170 files per target, copied from
`decode/spec/fixtures/`). They include both valid and intentionally-corrupt fixtures for every
supported format, giving the fuzzer a head start on interesting edge cases.

```
fuzz/corpus/
  decode_any/         # 170 seed files (valid + invalid BMP/PNG/GIF/JPEG/WebP/TIFF/HEIC)
  decode_with_prefix/ # same seeds — fuzzer selects prefix from data[0]
  detect_format/      # same seeds
```

---

## Running a short verification

These commands confirm each target compiles and the seed corpus does not immediately crash
the decoder:

```bash
cargo +nightly fuzz run decode_any          -- -max_total_time=10
cargo +nightly fuzz run decode_with_prefix  -- -max_total_time=10
cargo +nightly fuzz run detect_format       -- -max_total_time=10
```

---

## Running a longer campaign

```bash
# Run for one hour, using 4 parallel jobs
cargo +nightly fuzz run decode_any -- -max_total_time=3600 -jobs=4

# Or for a specific format focus
cargo +nightly fuzz run decode_with_prefix -- -max_total_time=3600
```

Evolved corpora should be saved to a separate directory to keep the committed seeds clean:

```bash
cargo +nightly fuzz run decode_any fuzz/corpus/decode_any fuzz/corpus-evolved/decode_any \
    -- -max_total_time=3600
```

---

## Triaging a crash

When a crash is found, libFuzzer writes the reproducer to `fuzz/artifacts/<target>/`.

**Reproduce:**

```bash
cargo +nightly fuzz run decode_any fuzz/artifacts/decode_any/crash-<hash>
```

**Print the crashing input as hex:**

```bash
cargo +nightly fuzz fmt decode_any fuzz/artifacts/decode_any/crash-<hash>
```

**Minimise the reproducer:**

```bash
cargo +nightly fuzz tmin decode_any fuzz/artifacts/decode_any/crash-<hash>
```

---

## Known crash (real bug — requires a fix)

**Target:** `decode_any` and `decode_with_prefix`

**Reproducer bytes:** `[0xFF, 0xD8]` (2-byte truncated JPEG SOI marker)

**Reproducer file:** `fuzz/artifacts/decode_any/crash-26eb6285d32d133930aab9a669b1155aa82099ae`

**Root cause:**
`src/jpeg.rs` installs a custom `error_exit` callback (`jpeg_error_exit_panic`) that calls
`panic!()` when libjpeg hits a fatal error. The intent is that `decode_jpeg` wraps the call
in `std::panic::catch_unwind`, converting the panic into `Err(DecodeError::CorruptInput)`.

However, when the panic unwinds through the C libjpeg stack frames
(the `extern "C-unwind"` ABI), libFuzzer's own signal handler intercepts the resulting
`SIGABRT`/signal before `catch_unwind` can handle it, causing libFuzzer to report exit
status 77 (deadly signal).

**Observed panic message:**
```
thread '<unnamed>' panicked at src/jpeg.rs:26:5:
libjpeg fatal error (msg_code=51)
```
`msg_code=51` is `JERR_PREMATURE_END` — "Premature end of JPEG file".

**Impact:** Any 2-byte (or longer) buffer starting with `0xFF 0xD8` that is not a valid
JPEG will panic the process instead of returning `DecodeError::CorruptInput`.

**Recommended fix (follow-up PR):** Replace the `panic!`/`catch_unwind` strategy with
libjpeg's built-in `setjmp`/`longjmp` error handling:
store a `jmp_buf` in a custom error manager struct, return from `error_exit` via `longjmp`,
and check the `setjmp` return value in `decode_jpeg_inner`. This avoids unwinding through C.
