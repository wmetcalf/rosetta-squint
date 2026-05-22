"""rosetta-image-decode spec regenerator.

Reads every fixture under fixtures/<format>/ and produces:
  - decoded/<format>/<fixture-relative-path>.bin   pre-decoded pixel buffer
  - goldens.json                                    SHA256 manifest

Output is idempotent: running twice produces zero git diff except for the
`regenerated_at` timestamp, which is excluded from --check comparisons.

Usage:
  python regenerate.py            # write outputs
  python regenerate.py --check    # exit 1 if outputs would differ from committed
  python regenerate.py --format <fmt>   # regenerate just one format

The supported formats are taken from formats.json; only formats with
status="supported" are processed (others skipped silently). In v1 of the
shared core, all formats are status="planned" so this script is a no-op
beyond updating the timestamp. Format sub-projects flip their entry to
"supported" when they land their first fixture.
"""
from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import json
import struct
import sys
import tempfile
from pathlib import Path

import PIL
from PIL import Image

# pillow-heif bundles libheif 1.21.x which produces ±1 px-different output
# from system libheif 1.17.x. HEIC goldens are anchored to system libheif
# via spec/regen_heic_via_system.py (ctypes wrapper). regenerate.py refuses
# to handle HEIC; this is intentional. See SPEC.md §16.

SPEC_DIR = Path(__file__).parent
FIXTURES_DIR = SPEC_DIR / "fixtures"
DECODED_DIR = SPEC_DIR / "decoded"
GOLDENS_PATH = SPEC_DIR / "goldens.json"
FORMATS_PATH = SPEC_DIR / "formats.json"
ERRORS_PATH = SPEC_DIR / "errors.json"

GOLDENS_SCHEMA_VERSION = 1


def load_formats() -> dict:
    return json.loads(FORMATS_PATH.read_text())


def supported_formats(formats_data: dict) -> set[str]:
    return {
        name
        for name, entry in formats_data["formats"].items()
        if entry["status"] == "supported"
    }


def decide_channels(img: Image.Image) -> tuple[Image.Image, int]:
    """Return (converted-image, channel-count). Preserve alpha if present."""
    mode = img.mode
    has_alpha = (
        mode in ("RGBA", "LA", "PA")
        or (mode == "P" and "transparency" in img.info)
        or "A" in mode
    )
    if has_alpha:
        return img.convert("RGBA"), 4
    return img.convert("RGB"), 3


def serialize_golden(width: int, height: int, channels: int, pixels: bytes) -> bytes:
    """Pack the 12-byte header + pixel bytes per SPEC.md §2."""
    if len(pixels) != width * height * channels:
        raise ValueError(
            f"pixel buffer length {len(pixels)} != {width}*{height}*{channels}"
        )
    header = struct.pack("<II", width, height) + bytes([channels, 0, 0, 0])
    return header + pixels


def fixture_to_relpath(fixture_path: Path) -> str:
    """Relative path from FIXTURES_DIR, using forward slashes."""
    return str(fixture_path.relative_to(FIXTURES_DIR)).replace("\\", "/")


def regenerate(only_format: str | None) -> dict:
    """Walk fixtures/<format>/* and produce decoded outputs + new goldens dict.

    Returns the new goldens.json structure. Does not write the JSON to disk
    (writing happens in main() so --check mode can compare without persisting).
    Pixel goldens (.bin files) ARE written as a side effect for both modes —
    in v1 (empty fixtures), no .bin files are produced anyway.
    """
    formats_data = load_formats()
    supported = supported_formats(formats_data)

    # Load error fixtures so we skip them (they can't be decoded by design)
    error_fixtures: set[str] = set()
    if ERRORS_PATH.exists():
        errors_data = json.loads(ERRORS_PATH.read_text())
        error_fixtures = set(errors_data.get("fixtures", {}))

    new_goldens = {
        "schema_version": GOLDENS_SCHEMA_VERSION,
        "pillow_version": PIL.__version__,
        "regenerated_at": dt.datetime.now(dt.timezone.utc).strftime(
            "%Y-%m-%dT%H:%M:%SZ"
        ),
        "fixtures": {},
    }

    for fmt_dir in sorted(FIXTURES_DIR.iterdir()):
        if not fmt_dir.is_dir():
            continue
        fmt_name = fmt_dir.name
        if fmt_name not in formats_data["formats"]:
            print(f"warning: unknown format dir '{fmt_name}' — skipping", file=sys.stderr)
            continue
        if fmt_name not in supported:
            continue  # planned/deprecated formats are skipped
        if only_format and fmt_name != only_format:
            continue
        if fmt_name == "heic":
            # HEIC is intentionally not handled here — see comment at top.
            # Use spec/regen_heic_via_system.py (ctypes wrapper around
            # system libheif 1.17.6) instead, which produces the
            # cross-port reference bytes.
            if only_format == "heic":
                raise RuntimeError(
                    "Refusing to regenerate HEIC goldens via PIL/pillow-heif "
                    "(libheif version mismatch with system port FFIs). "
                    "Use spec/regen_heic_via_system.py instead."
                )
            continue

        out_dir = DECODED_DIR / fmt_name
        out_dir.mkdir(parents=True, exist_ok=True)

        # JPEG files use .jpg extension; TIFF files use .tif extension;
        # all others match fmt_name directly.
        fmt_extensions = {fmt_name}
        if fmt_name == "jpeg":
            fmt_extensions.add("jpg")
        if fmt_name == "tiff":
            fmt_extensions.add("tif")
        if fmt_name == "heic":
            fmt_extensions.add("heif")

        for fixture in sorted(fmt_dir.rglob("*")):
            if not fixture.is_file() or fixture.name.startswith("."):
                continue
            if fixture.suffix.lower().lstrip(".") not in fmt_extensions:
                continue  # skip non-image files (e.g. LICENSE.md in fixture dirs)
            rel = fixture_to_relpath(fixture)
            if rel in error_fixtures:
                continue  # invalid fixtures are covered by errors.json
            with Image.open(fixture) as img:
                img.load()
                converted, channels = decide_channels(img)
                pixels = converted.tobytes()
                blob = serialize_golden(
                    converted.width, converted.height, channels, pixels
                )

            out_path = DECODED_DIR / rel
            out_path = out_path.with_suffix(out_path.suffix + ".bin")
            out_path.parent.mkdir(parents=True, exist_ok=True)
            out_path.write_bytes(blob)

            new_goldens["fixtures"][rel] = {
                "format": fmt_name,
                "width": converted.width,
                "height": converted.height,
                "channels": channels,
                "sha256": hashlib.sha256(pixels).hexdigest(),
            }

    return new_goldens


def write_goldens(goldens: dict) -> None:
    GOLDENS_PATH.write_text(json.dumps(goldens, indent=2, sort_keys=True) + "\n")


def diff_goldens(committed: dict, new: dict) -> list[str]:
    """Return non-empty list of diff descriptions; empty if equivalent (ignoring timestamp)."""
    diffs = []
    if committed.get("schema_version") != new["schema_version"]:
        diffs.append(
            f"schema_version: {committed.get('schema_version')} -> {new['schema_version']}"
        )
    if committed.get("pillow_version") != new["pillow_version"]:
        diffs.append(
            f"pillow_version: {committed.get('pillow_version')} -> {new['pillow_version']}"
        )
    committed_fix = committed.get("fixtures", {})
    new_fix = new["fixtures"]
    added = set(new_fix) - set(committed_fix)
    removed = set(committed_fix) - set(new_fix)
    for name in sorted(added):
        diffs.append(f"+ {name}")
    for name in sorted(removed):
        diffs.append(f"- {name}")
    for name in sorted(set(committed_fix) & set(new_fix)):
        if committed_fix[name] != new_fix[name]:
            diffs.append(f"~ {name}: {committed_fix[name]} -> {new_fix[name]}")
    return diffs


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="exit 1 if outputs would differ from committed (excluding timestamp)")
    parser.add_argument("--format", help="regenerate only this format")
    args = parser.parse_args()

    new_goldens = regenerate(args.format)

    # When --format is set, MERGE new entries into the existing goldens.json
    # rather than replacing the file. Without this, regenerating one format
    # silently wipes the other formats' entries — a footgun we hit in real
    # use. Detect mode short-circuits to compare only the new entries.
    if args.format and GOLDENS_PATH.exists():
        existing = json.loads(GOLDENS_PATH.read_text())
        existing_fix = existing.get("fixtures", {})
        # Preserve non-target-format entries.
        for k, v in existing_fix.items():
            if v.get("format") != args.format:
                new_goldens["fixtures"][k] = v
        # Preserve the global pillow_version if we didn't actually run PIL.
        # (HEIC short-circuits to no fixtures; keep prior version string.)
        if not any(v.get("format") == args.format for v in new_goldens["fixtures"].values()):
            new_goldens["pillow_version"] = existing.get("pillow_version", new_goldens["pillow_version"])

    if args.check:
        committed = json.loads(GOLDENS_PATH.read_text())
        diffs = diff_goldens(committed, new_goldens)
        if diffs:
            print("drift detected:", file=sys.stderr)
            for d in diffs:
                print(f"  {d}", file=sys.stderr)
            return 1
        print("spec: no drift.")
        return 0

    # Non-check mode: actually write goldens.json.
    write_goldens(new_goldens)
    print(f"wrote {GOLDENS_PATH.name} with {len(new_goldens['fixtures'])} fixtures.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
