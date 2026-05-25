# rosetta-squint umbrella Makefile.
#
# Single-command access to test/build/lint across all 6 ports.
# Each target shells into the right subdirectory and runs the native
# test framework. Exits non-zero if any port fails.
#
# Most users only need:
#   make test-all     # run every port's test suite
#   make test-hash    # just the 6 hash ports
#   make test-decode  # just the 5 decode ports
#   make test-squint  # just the 6 squint ports
#   make clean        # nuke node_modules, target/, .build/, etc.
#
# Targeted runs:
#   make test-rust-hash
#   make test-go-decode
#   make test-java-squint
#   ...

SHELL := /bin/bash
.SHELLFLAGS := -eu -o pipefail -c
SWIFT_PATH ?= /opt/swift/swift-5.9.2-RELEASE-ubuntu22.04/usr/bin

# ─── Top-level aggregates ───────────────────────────────────────────────────

.PHONY: test-all
test-all: test-hash test-decode test-squint
	@echo ""
	@echo "═══════════════════════════════════════════════"
	@echo "ALL PORTS PASSED across all three projects."
	@echo "═══════════════════════════════════════════════"

.PHONY: test-hash
test-hash: test-python-hash test-rust-hash test-go-hash test-java-hash test-js-hash test-swift-hash
	@echo "✓ hash side: all 6 ports passed"

.PHONY: test-decode
test-decode: test-rust-decode test-go-decode test-java-decode test-js-decode test-swift-decode
	@echo "✓ decode side: all 5 ports passed"

.PHONY: test-squint
test-squint: test-python-squint test-rust-squint test-go-squint test-java-squint test-js-squint test-swift-squint
	@echo "✓ squint side: all 6 ports passed"

# ─── Hash side ──────────────────────────────────────────────────────────────

.PHONY: test-python-hash
test-python-hash:
	@echo "→ python (hash)"
	@cd hash/python && pytest -q

.PHONY: test-rust-hash
test-rust-hash:
	@echo "→ rust (hash)"
	@cd hash/rust/rosetta-image-hash && cargo test --no-fail-fast --quiet

.PHONY: test-go-hash
test-go-hash:
	@echo "→ go (hash)"
	@cd hash/go/imagehash && go test -count=1 ./...

.PHONY: test-java-hash
test-java-hash:
	@echo "→ java (hash)"
	@cd hash/java && mvn -B -ntp -Dmaven.compiler.source=17 -Dmaven.compiler.target=17 test -q

.PHONY: test-js-hash
test-js-hash:
	@echo "→ js (hash)"
	@cd hash/js/rosetta-image-hash && npm test --silent

.PHONY: test-swift-hash
test-swift-hash:
	@echo "→ swift (hash)"
	@cd hash/swift/RosettaImageHash && PATH=$(SWIFT_PATH):$$PATH swift test --quiet

# ─── Decode side ────────────────────────────────────────────────────────────

.PHONY: test-rust-decode
test-rust-decode:
	@echo "→ rust (decode)"
	@cd decode/rust/rosetta-image-decode && cargo test --no-fail-fast --quiet

.PHONY: test-go-decode
test-go-decode:
	@echo "→ go (decode)"
	@cd decode/go/imagedecode && go test -count=1 ./...

.PHONY: test-java-decode
test-java-decode:
	@echo "→ java (decode)"
	@cd decode/java/rosetta-image-decode && mvn -B -ntp -Dmaven.compiler.source=17 -Dmaven.compiler.target=17 test -q

.PHONY: test-js-decode
test-js-decode:
	@echo "→ js (decode)"
	@cd decode/js/rosetta-image-decode && npm test --silent

.PHONY: test-swift-decode
test-swift-decode:
	@echo "→ swift (decode)"
	@cd decode/swift/RosettaImageDecode && PATH=$(SWIFT_PATH):$$PATH swift test --quiet

# ─── Squint side ────────────────────────────────────────────────────────────

.PHONY: test-python-squint
test-python-squint:
	@echo "→ python (squint)"
	@cd squint/python && pytest -q

.PHONY: test-rust-squint
test-rust-squint:
	@echo "→ rust (squint)"
	@cd squint/rust/rosetta-squint && cargo test --no-fail-fast --quiet

.PHONY: test-go-squint
test-go-squint:
	@echo "→ go (squint)"
	@cd squint/go/squint && go test -count=1 ./...

.PHONY: test-java-squint
test-java-squint:
	@echo "→ java (squint)"
	@cd squint/java/rosetta-squint && mvn -B -ntp -Dmaven.compiler.source=17 -Dmaven.compiler.target=17 test -q

.PHONY: test-js-squint
test-js-squint:
	@echo "→ js (squint)"
	@cd squint/js/rosetta-squint && npm test --silent

.PHONY: test-swift-squint
test-swift-squint:
	@echo "→ swift (squint)"
	@cd squint/swift/RosettaSquint && PATH=$(SWIFT_PATH):$$PATH swift test --quiet

# ─── Cross-port live diff (decode side) ─────────────────────────────────────

.PHONY: cross-port-diff
cross-port-diff:
	@echo "→ cross-port-diff (decode, vs frozen goldens)"
	@cd decode && tools/cross-port-diff/diff_all.py --vs-goldens

# ─── Cross-squint live diff (full chain: decode + hash, across 6 ports) ─────

.PHONY: cross-squint-diff
cross-squint-diff:
	@echo "→ cross-squint-diff (full grid, 35 algo×size combos × 2 fixtures = 70 byte-exact checks)"
	@tools/cross-squint-diff/diff_all_squint.py --regression

.PHONY: cross-squint-diff-fast
cross-squint-diff-fast:
	@echo "→ cross-squint-diff (minimal grid, ~22 combos — fast local iter)"
	@tools/cross-squint-diff/diff_all_squint.py --grid=minimal --regression

.PHONY: cross-squint-diff-boundary
cross-squint-diff-boundary:
	@echo "→ cross-squint-diff (boundary sizes 2/32/64 — snap-to-threshold verify)"
	@tools/cross-squint-diff/diff_all_squint.py --grid=boundary --regression

# ─── Differential fuzz: run all 6 ports against random/mutated inputs ──────

.PHONY: differential-fuzz
differential-fuzz:
	@echo "→ differential-fuzz (60s, mixed strategy) — finds cross-port drift"
	@tools/cross-squint-diff/differential_fuzz.py --duration 60

.PHONY: differential-hash-fuzz
differential-hash-fuzz:
	@echo "→ differential-hash-fuzz (60s, mixed strategy) — finds cross-port HASH drift"
	@tools/cross-squint-diff/differential_hash_fuzz.py --duration 60

# ─── Single-port fuzz (panic-free invariant) ──────────────────────────────

.PHONY: fuzz-rust-decode
fuzz-rust-decode:
	@echo "→ fuzz: rust decode (cargo-fuzz, 60s)"
	@cd decode/rust/rosetta-image-decode && cargo +nightly fuzz run decode_any -- -max_total_time=60

.PHONY: fuzz-rust-hash
fuzz-rust-hash:
	@echo "→ fuzz: rust hash (cargo-fuzz, 60s per target)"
	@cd hash/rust/rosetta-image-hash && cargo +nightly fuzz run hex_to_hash -- -max_total_time=60

.PHONY: fuzz-go-decode
fuzz-go-decode:
	@echo "→ fuzz: go decode (native Go 1.18 fuzz, 60s)"
	@cd decode/go/imagedecode && go test -run='^$$' -fuzz=FuzzDecodeAny -fuzztime=60s

.PHONY: fuzz-java-decode
fuzz-java-decode:
	@echo "→ fuzz: java decode (Jazzer, 60s)"
	@cd decode/java/rosetta-image-decode && JAZZER_FUZZ=1 mvn -B -ntp -Pfuzz -Dtest='DecoderFuzzTest#decodeAny' test -Dmaven.compiler.source=17 -Dmaven.compiler.target=17 -DjazzerArgs="-max_total_time=60"

.PHONY: fuzz-java-hash
fuzz-java-hash:
	@echo "→ fuzz: java hash (Jazzer, 60s)"
	@cd hash/java && JAZZER_FUZZ=1 mvn -B -ntp -Pfuzz -Dtest='HexParserFuzzTest#hexToHash' test -Dmaven.compiler.source=17 -Dmaven.compiler.target=17 -DjazzerArgs="-max_total_time=60"

# ─── Cross-port benchmark ───────────────────────────────────────────────────

.PHONY: bench
bench:
	@echo "→ cross-port bench (phash @ 8 on peppers.png, 10 iters)"
	@tools/bench/bench.py --iter 10

# ─── Build all CLIs ─────────────────────────────────────────────────────────

.PHONY: build-all-clis
build-all-clis: build-all-decode-clis build-all-squint-clis
	@echo "✓ all CLIs built (decode + squint)"

.PHONY: build-all-decode-clis
build-all-decode-clis:
	@echo "→ building per-port decode CLIs"
	@cd decode/rust/rosetta-image-decode && cargo build --release --example decode-cli
	@cd decode/go/imagedecode && go build -o ../../tools/cross-port-diff/decode-go ./cmd/decode-cli
	@cd decode/java/rosetta-image-decode && mvn -B -ntp -DskipTests -Dmaven.compiler.source=17 -Dmaven.compiler.target=17 package
	@cd decode/js/rosetta-image-decode && npm install && npm run build
	@cd decode/swift/RosettaImageDecode && PATH=$(SWIFT_PATH):$$PATH swift build --product DecodeCLI -c release

.PHONY: build-all-squint-clis
build-all-squint-clis:
	@echo "→ building per-port squint CLIs (Rust/Go/Java/JS/Swift)"
	@cd squint/rust/rosetta-squint && cargo build --release --example squint-cli
	@cd squint/go/squint && go build -o ../../tools/cross-squint-diff/squint-go ./cmd/squint-cli
	@cd squint/java/rosetta-squint && mvn -B -ntp -DskipTests -Dmaven.compiler.source=17 -Dmaven.compiler.target=17 package
	@cd squint/js/rosetta-squint && npm install && npm run build
	@cd squint/swift/RosettaSquint && PATH=$(SWIFT_PATH):$$PATH swift build --product SquintCLI -c release

# ─── Build/clean utilities ──────────────────────────────────────────────────

.PHONY: clean
clean:
	@echo "→ clean (node_modules, target/, .build/, build/, dist/, __pycache__)"
	@find . \( -name node_modules -o -name target -o -name .build -o -name build -o -name dist -o -name __pycache__ -o -name '.pytest_cache' \) -type d -prune -exec rm -rf {} +
	@echo "✓ clean done"

.PHONY: help
help:
	@echo "Top-level targets:"
	@echo "  make test-all          # run every test in every port"
	@echo "  make test-hash         # 6 hash ports only"
	@echo "  make test-decode       # 5 decode ports only"
	@echo "  make test-squint       # 6 squint ports only"
	@echo ""
	@echo "Cross-port verification (decode):"
	@echo "  make build-all-clis        # build per-port decode + squint CLI binaries"
	@echo "  make cross-port-diff       # live diff via tools/cross-port-diff (decode)"
	@echo "  make cross-squint-diff     # all 6 ports byte-exact on the spec grid"
	@echo ""
	@echo "Fuzz (cross-port, finds drift the curated grid can't reach):"
	@echo "  make differential-fuzz       # 60s: random bytes → all 6 decoders, diff"
	@echo "  make differential-hash-fuzz  # 60s: random pixels → all 6 hash funcs, diff"
	@echo ""
	@echo "Fuzz (single-port, finds panics / hangs):"
	@echo "  make fuzz-rust-decode  fuzz-rust-hash   # cargo-fuzz / nightly"
	@echo "  make fuzz-go-decode                     # go 1.18+ native fuzz"
	@echo "  make fuzz-java-decode  fuzz-java-hash   # jazzer (junit5)"
	@echo "  (JS fuzz runs as part of npm test via fast-check.)"
	@echo ""
	@echo "Per-port (target = test-<lang>-<project>):"
	@echo "  make test-rust-hash     test-rust-decode     test-rust-squint"
	@echo "  make test-go-hash       test-go-decode       test-go-squint"
	@echo "  make test-java-hash     test-java-decode     test-java-squint"
	@echo "  make test-js-hash       test-js-decode       test-js-squint"
	@echo "  make test-swift-hash    test-swift-decode    test-swift-squint"
	@echo "  make test-python-hash                        test-python-squint"
	@echo ""
	@echo "Housekeeping:"
	@echo "  make clean             # delete all build artifacts"
