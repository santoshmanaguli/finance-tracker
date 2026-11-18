import { execFileSync } from "node:child_process";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { PostgreSqlContainer, type StartedPostgreSqlContainer } from "@testcontainers/postgresql";
import type { TestProject } from "vitest/node";

/**
 * Vitest global setup for integration tests: ONE real Postgres 16 container for the whole run,
 * migrated with the SAME dbmate migration files production uses.
 *
 * Why not an in-memory fake DB? Our correctness depends on Postgres-specific features (partial
 * unique indexes, CHECK regexes, TIMESTAMPTZ). Tests on anything else would prove nothing.
 * Same image tag as docker-compose.yml.
 */
declare module "vitest" {
  export interface ProvidedContext {
    databaseUrl: string;
  }
}

const here = path.dirname(fileURLToPath(import.meta.url));
const apiDir = path.resolve(here, "../..");
const migrationsDir = path.resolve(apiDir, "../../db/migrations");

let container: StartedPostgreSqlContainer | undefined;

export async function setup(project: TestProject): Promise<void> {
  container = await new PostgreSqlContainer("postgres:16-alpine").start();
  const url = container.getConnectionUri();

  execFileSync(
    path.join(apiDir, "node_modules/.bin/dbmate"),
    ["--url", `${url}?sslmode=disable`, "--migrations-dir", migrationsDir, "--no-dump-schema", "up"],
    { stdio: "pipe" },
  );

  project.provide("databaseUrl", url);
}

export async function teardown(): Promise<void> {
  await container?.stop();
}
