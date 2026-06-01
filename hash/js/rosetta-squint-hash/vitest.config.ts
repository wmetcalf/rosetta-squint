import { defineConfig } from "vitest/config";

export default defineConfig({
  test: {
    include: ["tests/**/*.test.ts"],
    testTimeout: 60000, // heavy hash tests (crop_resistant, all-algorithms) exceed the 5s default
  },
});
