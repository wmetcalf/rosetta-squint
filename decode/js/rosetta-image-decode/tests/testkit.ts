import { readFileSync, readdirSync, statSync } from "node:fs";
import { join } from "node:path";

export const SPEC_DIR = join("..", "..", "spec");

export function readFixture(rel: string): Uint8Array {
  const path = join(SPEC_DIR, "fixtures", rel);
  return new Uint8Array(readFileSync(path));
}

export interface DecodedGolden {
  width: number;
  height: number;
  channels: number;
  pixels: Uint8Array;
}

export function readGolden(fixtureRel: string): DecodedGolden {
  const path = join(SPEC_DIR, "decoded", `${fixtureRel}.bin`);
  const blob = new Uint8Array(readFileSync(path));
  if (blob.length < 12) throw new Error(`golden ${fixtureRel} too short`);
  const view = new DataView(blob.buffer, blob.byteOffset, 12);
  const width = view.getUint32(0, true);
  const height = view.getUint32(4, true);
  const channels = blob[8]!;
  const pixels = blob.subarray(12);
  return { width, height, channels, pixels };
}

export function listValidFixtures(format: string): string[] {
  const dir = join(SPEC_DIR, "fixtures", format, "valid");
  return readdirSync(dir)
    .filter((name) => statSync(join(dir, name)).isFile())
    .filter((name) => {
      if (format === "jpeg") {
        return name.endsWith(".jpg") || name.endsWith(".jpeg");
      }
      return name.endsWith(`.${format}`);
    })
    .map((name) => `${format}/valid/${name}`)
    .sort();
}

export interface ExpectedError {
  format: string | null;
  expected_kind: string;
  expected_detail_substring: string;
}

export function readErrors(): Record<string, ExpectedError> {
  const path = join(SPEC_DIR, "errors.json");
  const doc = JSON.parse(readFileSync(path, "utf8")) as {
    fixtures: Record<string, ExpectedError>;
  };
  return doc.fixtures;
}
