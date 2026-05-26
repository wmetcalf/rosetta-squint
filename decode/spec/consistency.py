"""rosetta-squint-decode spec-only consistency checks.

Does NOT re-run PIL. Validates:
  - formats.json is valid against formats.schema.json
  - goldens.json is valid against goldens.schema.json
  - every entry in goldens.json.fixtures has a corresponding fixture file
  - every fixture file has a corresponding goldens entry
  - every goldens entry has a matching decoded/<format>/<rel>.bin
  - every .bin decodes to a header consistent with the goldens metadata
    (width/height/channels match; pixel byte count matches sha256)
  - no orphaned .bin files (file exists but no goldens entry)
  - every supported format in formats.json has at least one fixture

Exit 0 = consistent. Exit 1 = inconsistent (CI fails).
"""
from __future__ import annotations

import hashlib
import json
import struct
import sys
from pathlib import Path

from jsonschema import Draft202012Validator

SPEC_DIR = Path(__file__).parent
FIXTURES_DIR = SPEC_DIR / "fixtures"
DECODED_DIR = SPEC_DIR / "decoded"
GOLDENS_PATH = SPEC_DIR / "goldens.json"
GOLDENS_SCHEMA_PATH = SPEC_DIR / "goldens.schema.json"
FORMATS_PATH = SPEC_DIR / "formats.json"
FORMATS_SCHEMA_PATH = SPEC_DIR / "formats.schema.json"
ERRORS_PATH = SPEC_DIR / "errors.json"


def main() -> int:
    errors: list[str] = []

    formats = json.loads(FORMATS_PATH.read_text())
    formats_schema = json.loads(FORMATS_SCHEMA_PATH.read_text())
    goldens = json.loads(GOLDENS_PATH.read_text())
    goldens_schema = json.loads(GOLDENS_SCHEMA_PATH.read_text())
    error_fixtures: set[str] = set()
    if ERRORS_PATH.exists():
        errors_data = json.loads(ERRORS_PATH.read_text())
        error_fixtures = set(errors_data.get("fixtures", {}))

    # 1. Schemas
    for name, data, schema in (
        ("formats.json", formats, formats_schema),
        ("goldens.json", goldens, goldens_schema),
    ):
        validator = Draft202012Validator(schema)
        for err in sorted(validator.iter_errors(data), key=lambda e: list(e.path)):
            errors.append(f"{name}: {list(err.path)}: {err.message}")

    # 2. fixtures listed in goldens must exist on disk
    for rel in sorted(goldens.get("fixtures", {})):
        fixture_path = FIXTURES_DIR / rel
        if not fixture_path.is_file():
            errors.append(f"goldens fixture '{rel}': file not found at {fixture_path}")

    # 3. fixtures on disk must appear in goldens (skip dotfiles + .gitkeep)
    for fmt_dir in sorted(FIXTURES_DIR.iterdir()):
        if not fmt_dir.is_dir():
            continue
        if fmt_dir.name not in formats["formats"]:
            errors.append(f"fixtures/{fmt_dir.name}: not a known format")
            continue
        fmt_name = fmt_dir.name
        # Build the set of accepted extensions for this format.
        # JPEG fixtures use .jpg; TIFF fixtures use .tif.
        fmt_extensions = {f".{fmt_name}"}
        if fmt_name == "jpeg":
            fmt_extensions.add(".jpg")
        if fmt_name == "tiff":
            fmt_extensions.add(".tif")
        for fixture in sorted(fmt_dir.rglob("*")):
            if not fixture.is_file() or fixture.name.startswith("."):
                continue
            if fixture.suffix.lower() not in fmt_extensions:
                continue  # skip non-image files (e.g. LICENSE.md in fixture dirs)
            rel = str(fixture.relative_to(FIXTURES_DIR)).replace("\\", "/")
            if rel in error_fixtures:
                continue  # invalid fixtures are covered by errors.json, not goldens.json
            if rel not in goldens.get("fixtures", {}):
                errors.append(f"fixture file '{rel}': no entry in goldens.json")

    # 4. decoded/.bin files must match goldens metadata
    for rel, entry in goldens.get("fixtures", {}).items():
        decoded_path = DECODED_DIR / (rel + ".bin")
        if not decoded_path.is_file():
            errors.append(f"goldens fixture '{rel}': decoded .bin not found at {decoded_path}")
            continue
        data = decoded_path.read_bytes()
        if len(data) < 12:
            errors.append(f"decoded '{decoded_path.name}': too short ({len(data)} bytes)")
            continue
        width, height = struct.unpack("<II", data[:8])
        channels = data[8]
        expected_pixel_bytes = width * height * channels
        if len(data) != 12 + expected_pixel_bytes:
            errors.append(
                f"decoded '{decoded_path.name}': length {len(data)} != 12 + {width}*{height}*{channels}"
            )
            continue
        if width != entry["width"] or height != entry["height"] or channels != entry["channels"]:
            errors.append(
                f"decoded '{decoded_path.name}': header ({width}x{height}c{channels}) "
                f"!= goldens ({entry['width']}x{entry['height']}c{entry['channels']})"
            )
            continue
        actual_sha = hashlib.sha256(data[12:]).hexdigest()
        if actual_sha != entry["sha256"]:
            errors.append(
                f"decoded '{decoded_path.name}': sha256 {actual_sha} != goldens {entry['sha256']}"
            )

    # 5. orphaned .bin files (exist on disk but not in goldens)
    if DECODED_DIR.exists():
        for bin_file in sorted(DECODED_DIR.rglob("*.bin")):
            rel = str(bin_file.relative_to(DECODED_DIR))[:-4].replace("\\", "/")
            if rel not in goldens.get("fixtures", {}):
                errors.append(f"orphaned decoded file: {bin_file} (no goldens entry for '{rel}')")

    # 6. every supported format has at least one fixture
    for name, entry in formats["formats"].items():
        if entry["status"] == "supported":
            count = sum(1 for k in goldens.get("fixtures", {}) if goldens["fixtures"][k]["format"] == name)
            if count == 0:
                errors.append(f"format '{name}': status=supported but zero fixtures in goldens")
            if count != entry["fixture_count"]:
                errors.append(
                    f"format '{name}': formats.json fixture_count={entry['fixture_count']} "
                    f"but goldens has {count}"
                )

    if errors:
        print(f"Spec consistency check FAILED ({len(errors)} errors):", file=sys.stderr)
        for err in errors:
            print(f"  {err}", file=sys.stderr)
        return 1

    fixture_count = len(goldens.get("fixtures", {}))
    supported_count = sum(1 for f in formats["formats"].values() if f["status"] == "supported")
    print(
        f"Spec consistency check passed: {fixture_count} fixtures, "
        f"{supported_count} supported formats, {len(formats['formats'])} planned/supported total."
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
