#!/usr/bin/env node
import {
  phash, phashSimple, dhash, dhashVertical, averageHash,
  whashHaar, whashDb4, whashDb4Robust, colorhash, cropResistantHash,
} from "../dist/index.js";

const [, , algo, sizeStr, path] = process.argv;
if (!algo || !sizeStr || !path) {
  process.stderr.write("usage: squint-cli <algo> <size> <path>\n");
  process.exit(2);
}
const size = parseInt(sizeStr, 10);

const algos = {
  phash, phash_simple: phashSimple, dhash, dhash_vertical: dhashVertical,
  average_hash: averageHash, whash_haar: whashHaar, whash_db4: whashDb4,
  whash_db4_robust: whashDb4Robust, colorhash,
  crop_resistant_hash: cropResistantHash,
};

const fn = algos[algo];
if (!fn) {
  process.stderr.write(`unknown algo: ${algo}\n`);
  process.exit(2);
}

try {
  const result = algo === "crop_resistant_hash"
    ? await cropResistantHash(path)
    : await fn(path, size);
  process.stdout.write(`${result.toString()}\n`);
} catch (e) {
  process.stderr.write(`error: ${e.message ?? e}\n`);
  process.exit(1);
}
