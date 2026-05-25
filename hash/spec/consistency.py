"""Spec-only consistency checks. Does NOT re-run imagehash.

Validates:
  - goldens.json is valid against goldens.schema.json
  - every decoded/*.rgb.bin has a matching sha256 in goldens.json.decoded_sha256
  - every fixture in fixtures/ appears in every applicable algorithm's fixture dict

Exit 0 = consistent. Exit 1 = inconsistent (CI fails).
"""
from __future__ import annotations

import hashlib
import json
import sys
from pathlib import Path

from jsonschema import Draft202012Validator

SPEC_DIR = Path(__file__).parent
FIXTURES_DIR = SPEC_DIR / "fixtures"
DECODED_DIR = SPEC_DIR / "decoded"
GOLDENS_PATH = SPEC_DIR / "goldens.json"
SCHEMA_PATH = SPEC_DIR / "goldens.schema.json"


def main() -> int:
    errors: list[str] = []
    goldens = json.loads(GOLDENS_PATH.read_text())
    schema = json.loads(SCHEMA_PATH.read_text())

    # 1. Schema validation
    validator = Draft202012Validator(schema)
    schema_errors = sorted(validator.iter_errors(goldens), key=lambda e: e.path)
    for err in schema_errors:
        errors.append(f"schema: {list(err.path)}: {err.message}")

    # 2. Every decoded file has a matching SHA in goldens.decoded_sha256
    sha_table = goldens.get("decoded_sha256", {})
    for decoded in sorted(DECODED_DIR.glob("*.rgb.bin")):
        fixture_name = decoded.name.removesuffix(".rgb.bin")
        if fixture_name not in sha_table:
            errors.append(f"decoded: {decoded.name}: not in goldens.decoded_sha256")
            continue
        actual = hashlib.sha256(decoded.read_bytes()).hexdigest()
        if actual != sha_table[fixture_name]:
            errors.append(f"decoded: {decoded.name}: sha mismatch (file {actual}, manifest {sha_table[fixture_name]})")

    # Every entry in sha_table has a corresponding decoded file
    decoded_names = {p.name.removesuffix(".rgb.bin") for p in DECODED_DIR.glob("*.rgb.bin")}
    for name in sha_table:
        if name not in decoded_names:
            errors.append(f"decoded_sha256: {name}: no matching decoded file")

    # 3. Every fixture in fixtures/ appears in every applicable algorithm.
    # crop_resistant_hash deliberately excludes some fixtures (e.g.
    # crop-boundary-150.png is for the H-H1 banker's-rounding audit and would
    # FAIL cross-port tests until Rust/Java/Swift switch rounding modes).
    # Mirror the exclusion list in regenerate.py.
    PER_ALGO_FIXTURE_EXCLUDE = {
        "crop_resistant_hash": {"crop-boundary-150.png"},
    }
    fixture_names = {p.name for p in FIXTURES_DIR.glob("*.png")}
    algos = goldens.get("algorithms", {})
    for algo_name, algo in algos.items():
        algo_fixtures = set(algo.get("fixtures", {}).keys())
        expected = fixture_names - PER_ALGO_FIXTURE_EXCLUDE.get(algo_name, set())
        missing = expected - algo_fixtures
        extra = algo_fixtures - fixture_names
        if missing:
            errors.append(f"algorithm {algo_name}: fixtures missing from goldens: {sorted(missing)}")
        if extra:
            errors.append(f"algorithm {algo_name}: fixtures in goldens but not on disk: {sorted(extra)}")

    if errors:
        print("Spec consistency check FAILED:", file=sys.stderr)
        for e in errors:
            print(f"  - {e}", file=sys.stderr)
        return 1
    print(f"Spec consistency check passed: {len(fixture_names)} fixtures, {len(algos)} algorithms, {len(sha_table)} decoded SHAs.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
