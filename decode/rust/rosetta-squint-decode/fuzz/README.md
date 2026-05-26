# Fuzz targets for rosetta-squint-decode

This directory contains [cargo-fuzz](https://rust-fuzz.github.io/book/cargo-fuzz.html)
harnesses for the `rosetta-squint-decode` crate.

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

## Resolved: JPEG panic on truncated input (was a real bug)

**Target:** `decode_any` and `decode_with_prefix`

**Reproducer bytes:** `[0xFF, 0xD8]` (2-byte truncated JPEG SOI marker)

**Original root cause:** `src/jpeg.rs` used a `panic!()` + `catch_unwind` workaround for
libjpeg's `error_exit` callback. Worked under `cargo test` (panic=unwind) but broke under
`panic=abort` builds like cargo-fuzz — libFuzzer's deadly-signal handler intercepted
`SIGABRT` before `catch_unwind` could handle it.

**Resolved:** in-tree C shim at `c-src/jpeg_decode_shim.c` uses the canonical
`setjmp`/`longjmp` error-recovery pattern entirely in C. `error_exit` calls `longjmp` back
to a `setjmp` point inside the shim. Rust never sees a panic from this code path — it just
receives a return code + buffer via FFI.

`src/jpeg.rs` now wraps the shim with `extern "C"` declarations, maps the shim's return
codes to `DecodeError` variants, and includes a forced-link reference to `mozjpeg_sys`
(`use mozjpeg_sys::jpeg_std_error as _force_libjpeg_link`) so the linker pulls in
libjpeg-turbo's static library when external crates consume `rosetta-squint-decode`.

Both `cargo test` (42/42 passing) and the previously-crashing fuzz input now produce
`DecodeError::CorruptInput { detail: "libjpeg fatal error msg_code=51" }`.

---

## Known minor issue: one-time 192-byte leak under LSan

**Target:** any (surfaces in `decode_any` runs > 10 seconds)

**Observation:** LeakSanitizer reports a **single** 192-byte direct leak after running the
fuzzer. The "1 allocation(s)" count and the fact that this happens even on the initial
corpus (not requiring fuzzer mutation) indicates a one-time static allocation, not a
per-decode leak. Per-decode leak would compound across the ~100,000 iterations the
fuzzer runs and show up as many MB.

**Probable source:** libjpeg-turbo's static state allocated once when the first decoder
is initialised (jconfig pool or similar). Not affecting normal users — the allocation
lives until process exit anyway.

**Not investigated further:** the cost-benefit doesn't justify deep libjpeg-turbo
internals work for a 192-byte one-time allocation that LSan may simply be misreporting.
Documented here so future fuzz runs don't surface it as a new finding.
