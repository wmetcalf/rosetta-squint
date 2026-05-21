# rosetta-image-decode (Java)

Byte-exact PIL-compatible image decoder library, Java port.

## Build

    mvn -B clean test

Tests resolve fixtures and goldens from `../../spec/` (relative to this module).
Run `mvn test` from the module root.

## v1 Formats

- BMP (Tier 1+2+3 minus BI_JPEG/BI_PNG)

Other formats land in later sub-projects of the rosetta-image-decode family.
