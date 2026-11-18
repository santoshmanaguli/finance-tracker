import { defineConfig } from "vitest/config";

/**
 * Two test projects:
 * - unit:        pure functions, no I/O, milliseconds. Run constantly.
 * - integration: real Postgres 16 in Docker (Testcontainers), migrations applied with the same
 *                dbmate files production uses. Run before every push and in CI.
 * CI can run them as separate steps, so a fast unit failure stops the pipeline early.
 */
export default defineConfig({
  test: {
    projects: [
      {
        test: {
          name: "unit",
          include: ["test/unit/**/*.test.ts"],
        },
      },
      {
        test: {
          name: "integration",
          include: ["test/integration/**/*.test.ts"],
          globalSetup: ["test/support/postgresContainer.ts"],
          // Integration files share one database and truncate tables: run them one at a time.
          fileParallelism: false,
          testTimeout: 30_000,
          hookTimeout: 120_000,
        },
      },
    ],
  },
});
