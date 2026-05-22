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
	@echo "→ cross-squint-diff (live chain comparison across 6 ports)"
	@tools/cross-squint-diff/diff_all_squint.py --regression

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
	@echo "  make build-all-clis    # build per-port decode-cli binaries"
	@echo "  make cross-port-diff   # live diff via tools/cross-port-diff"
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
